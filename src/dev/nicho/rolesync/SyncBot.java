package dev.nicho.rolesync;

import dev.nicho.rolesync.db.DatabaseHandler;
import dev.nicho.rolesync.db.MySQLHandler;
import dev.nicho.rolesync.db.SQLiteHandler;
import dev.nicho.rolesync.util.MojangAPI;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.guild.member.update.GenericGuildMemberUpdateEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.restaction.MessageAction;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class SyncBot extends ListenerAdapter {

    private JavaPlugin plugin = null;
    private DatabaseHandler db = null;
    private CommandHandler ch = null;
    private YamlConfiguration lang = null;

    public SyncBot(@Nonnull JavaPlugin plugin, YamlConfiguration language) throws IOException, SQLException {
        super();
        this.plugin = plugin;
        this.lang = language;
        plugin.getLogger().info("Finished initializing bot.");

        if (plugin.getConfig().getString("database.type").equalsIgnoreCase("mysql")) {
            this.db = new MySQLHandler(plugin,
                    plugin.getConfig().getString("database.mysql.dbhost"),
                    plugin.getConfig().getInt("database.mysql.dbport"),
                    plugin.getConfig().getString("database.mysql.dbname"),
                    plugin.getConfig().getString("database.mysql.dbuser"),
                    plugin.getConfig().getString("database.mysql.dbpass"));
        } else {
            this.db = new SQLiteHandler(plugin, new File(plugin.getDataFolder(), "database.db"));
        }
    }

    @Override
    public void onReady(@Nonnull ReadyEvent event) {
        this.ch = new CommandHandler(plugin, event.getJDA(), db, lang);
        plugin.getLogger().info("Logged in: " + event.getJDA().getSelfUser().getName());
    }

    @Override
    public void onMessageReceived(@Nonnull MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return; // ignore bots

        String message = event.getMessage().getContentRaw();
        String prefix = this.plugin.getConfig().getString("botInfo.prefix");

        if (!message.substring(0, prefix.length()).equals(prefix)) return; // ignore if no prefix

        String[] argv = message.split(" ");
        argv[0] = argv[0].substring(prefix.length()); // remove prefix

        if (argv[0].equalsIgnoreCase("info")) {
            ch.info(argv, event);
        }
    }

    @Override
    public void onGenericGuildMemberUpdate(@Nonnull GenericGuildMemberUpdateEvent event) {

    }
}

class CommandHandler {

    private JavaPlugin plugin = null;
    private JDA bot = null;
    private DatabaseHandler db = null;
    private YamlConfiguration lang = null;

    CommandHandler(JavaPlugin plugin, JDA bot, DatabaseHandler db, YamlConfiguration lang) {
        this.plugin = plugin;
        this.bot = bot;
        this.db = db;
        this.lang = lang;
    }

    void info(String[] argv, MessageReceivedEvent event) {
        if (!hasRoleFromList(event.getAuthor(), plugin.getConfig().getStringList("adminCommandRoles"))) {
            this.reactAndDelete(plugin.getConfig().getString("react.onPermissionError"), event.getMessage());
        }

        if (argv.length < 2) {
            this.reactAndDelete(plugin.getConfig().getString("react.onUserError"), event.getMessage());
        }

        try {
            if (argv[1].length() > 16 && StringUtils.isNumeric(argv[1])) { // looks like Discord ID

                    String uuid = db.findUUIDByDiscordID(argv[1]);

                    if (uuid == null) {
                        this.reactAndDelete(plugin.getConfig().getString("react.onUserError"), event.getMessage());
                        return;
                    }

                    String name = MojangAPI.uuidToName(uuid);

                    this.reactAndDelete(plugin.getConfig().getString("react.onSuccessError"), event.getMessage());
                    event.getChannel().sendMessage(this.lang.getString("linkedTo") + " " + name + " (" + uuid + ")" ).queue();

            } else { // try minecraft nick
                String uuid = MojangAPI.nameToUUID(argv[1]);
                String id = db.findDiscordIDbyUUID(UUID.fromString(uuid).toString());

                if (id == null) {
                    this.reactAndDelete(plugin.getConfig().getString("react.onUserError"), event.getMessage());
                    return;
                }

                User usr = bot.getUserById(id);
                String name = "_" + this.lang.getString("unknownUser") + "_";
                if (usr != null) {
                    name = usr.getAsTag();
                }

                this.reactAndDelete(plugin.getConfig().getString("react.onSuccessError"), event.getMessage());
                event.getChannel().sendMessage(this.lang.getString("linkedTo") + " " + name + " (" + id + ")" )
                        .queue(msg -> msg.delete()
                                .queueAfter(plugin.getConfig().getInt("deleteAfter"), TimeUnit.SECONDS));
            }
        } catch (SQLException | IOException e) {
            reactAndDelete(plugin.getConfig().getString("react.onBotError"), event.getMessage());
            e.printStackTrace();
        }
    }

    void link(String[] argv, MessageReceivedEvent event) {
        if (!hasRoleFromList(event.getAuthor(), plugin.getConfig().getStringList("adminCommandRoles"))) {
            this.reactAndDelete(plugin.getConfig().getString("react.onPermissionError"), event.getMessage());
        }

        if (argv.length < 2) {
            this.reactAndDelete(plugin.getConfig().getString("react.onUserError"), event.getMessage());
        }
    }

    private void reactAndDelete(String reaction, Message message) {
        message.addReaction(reaction).queue();

        if (plugin.getConfig().getBoolean("deleteCommands")) {
            message.delete().queueAfter(plugin.getConfig().getInt("deleteAfter"), TimeUnit.SECONDS);
        }
    }

    private boolean hasRoleFromList(User user, List<String> roleList) {
        for (Guild guild : bot.getGuilds()) {
            final Member member = guild.getMember(user);
            if (member != null) {
                for (String roleID : roleList) {
                    Role roleFound = member.getRoles().stream().filter(role -> role.getId().equals(roleID)).findFirst().orElse(null);

                    if (roleFound != null) {
                        return true;
                    }
                }
            }
        }

        return false;
    }
}
