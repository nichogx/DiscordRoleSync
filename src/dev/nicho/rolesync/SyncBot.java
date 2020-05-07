package dev.nicho.rolesync;

import dev.nicho.rolesync.db.DatabaseHandler;
import dev.nicho.rolesync.db.MySQLHandler;
import dev.nicho.rolesync.db.SQLiteHandler;
import dev.nicho.rolesync.permissionapis.LuckPermsAPI;
import dev.nicho.rolesync.permissionapis.PermPluginNotFoundException;
import dev.nicho.rolesync.permissionapis.PermissionsAPI;
import dev.nicho.rolesync.util.JDAUtils;
import dev.nicho.rolesync.util.MojangAPI;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
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
    private PermissionsAPI permPlugin = null;
    private JDA bot = null;

    public SyncBot(@Nonnull JavaPlugin plugin, YamlConfiguration language) throws IOException, SQLException, PermPluginNotFoundException {
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

        ConfigurationSection perms = plugin.getConfig().getConfigurationSection("permissions");
        List<String> managedPerms = new ArrayList<String>();
        for (String perm : perms.getKeys(true)) {
            if (perms.getStringList(perm).isEmpty()) continue;
            managedPerms.add(perm);
        }

        if (plugin.getConfig().getString("permissionPlugin").equalsIgnoreCase("luckperms")) {
            this.permPlugin = new LuckPermsAPI(managedPerms);
        }
    }

    @Override
    public void onReady(@Nonnull ReadyEvent event) {
        this.bot = event.getJDA();
        this.ch = new CommandHandler();

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
        } else if (argv[0].equalsIgnoreCase("link")) {
            ch.link(argv, event);
            checkRoles(event.getAuthor());
        } else if (argv[0].equalsIgnoreCase("unlink")) {
            ch.unlink(argv, event);
        }
    }


    @Override
    public void onGuildMemberRoleAdd(@Nonnull GuildMemberRoleAddEvent event) {
        checkRoles(event.getUser());
    }

    @Override
    public void onGuildMemberRoleRemove(@Nonnull GuildMemberRoleRemoveEvent event) {
        checkRoles(event.getUser());
    }

    void checkRoles(User user) {
        try {
            ConfigurationSection perms = plugin.getConfig().getConfigurationSection("permissions");
            String uuid = db.findUUIDByDiscordID(user.getId());
            if (uuid == null) { // user not linked
                return; // ignore
            }

            List<String> permsToHave = new ArrayList<String>();
            for (String perm : perms.getKeys(true)) {
                if (perms.getStringList(perm).isEmpty()) continue;
                final boolean hasRole = JDAUtils.hasRoleFromList(user, perms.getStringList(perm), bot);
                if (hasRole) {
                    permsToHave.add(perm);
                }
            }
            permPlugin.setPermissions(uuid, permsToHave);


            if (plugin.getConfig().getBoolean("manageWhitelist")) {
                if (JDAUtils.hasRoleFromList(user, plugin.getConfig().getStringList("whitelistRoles"), bot)) {
                    db.addToWhitelist(uuid);
                    Bukkit.getOfflinePlayer(UUID.fromString(uuid)).setWhitelisted(true);
                } else {
                    db.removeFromWhitelist(uuid);
                    Bukkit.getOfflinePlayer(UUID.fromString(uuid)).setWhitelisted(false);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("An error occured while trying to check roles for the user. " +
                    "Please check the stack trace below and contact the developer.");
            e.printStackTrace();
        }
    }

    class CommandHandler {

        void info(String[] argv, MessageReceivedEvent event) {
            if (!JDAUtils.hasRoleFromList(event.getAuthor(), plugin.getConfig().getStringList("adminCommandRoles"), bot)) {
                JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onPermissionError"), event.getMessage(), plugin.getConfig());
                return;
            }

            if (argv.length < 2) {
                JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onUserError"), event.getMessage(), plugin.getConfig());
            }

            try {
                if (argv[1].length() > 16 && StringUtils.isNumeric(argv[1])) { // looks like Discord ID

                    String uuid = db.findUUIDByDiscordID(argv[1]);

                    if (uuid == null) {
                        JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onUserError"), event.getMessage(), plugin.getConfig());
                        return;
                    }

                    String name = MojangAPI.uuidToName(uuid);

                    JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onSuccess"), event.getMessage(), plugin.getConfig());
                    event.getChannel().sendMessage(lang.getString("linkedTo") + " " + name + " (" + uuid + ")" )
                            .queue(msg -> {
                                if (plugin.getConfig().getBoolean("deleteCommands"))
                                    msg.delete().queueAfter(plugin.getConfig().getInt("deleteAfter"), TimeUnit.SECONDS);
                            });

                } else { // try minecraft nick
                    String uuid = MojangAPI.nameToUUID(argv[1]);
                    String id = db.findDiscordIDbyUUID(uuid);

                    if (id == null) {
                        JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onUserError"), event.getMessage(), plugin.getConfig());
                        return;
                    }

                    User usr = bot.getUserById(id);
                    String name = "_" + lang.getString("unknownUser") + "_";
                    if (usr != null) {
                        name = usr.getAsTag();
                    }

                    JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onSuccess"), event.getMessage(), plugin.getConfig());
                    event.getChannel().sendMessage(lang.getString("linkedTo") + " " + name + " (" + id + ")" )
                            .queue(msg -> {
                                if (plugin.getConfig().getBoolean("deleteCommands"))
                                    msg.delete().queueAfter(plugin.getConfig().getInt("deleteAfter"), TimeUnit.SECONDS);
                            });
                }
            } catch (SQLException | IOException e) {
                JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onBotError"), event.getMessage(), plugin.getConfig());
                plugin.getLogger().severe("An error occured while getting info for the user. " +
                        "Please check the stack trace below and contact the developer.");
                e.printStackTrace();
            }
        }

        void link(String[] argv, MessageReceivedEvent event) {
            if (!plugin.getConfig().getStringList("botInfo.channelsToListen").contains(event.getChannel().getId())
                    && !JDAUtils.hasRoleFromList(event.getAuthor(), plugin.getConfig().getStringList("adminCommandRoles"), bot)) {
                JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onPermissionError"), event.getMessage(), plugin.getConfig());
                return;
            }

            if (argv.length < 2) {
                JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onUserError"), event.getMessage(), plugin.getConfig());
            }

            try {
                String linkedUUID = db.findUUIDByDiscordID(event.getAuthor().getId());
                if (linkedUUID != null) {
                    JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onUserError"), event.getMessage(), plugin.getConfig());
                    event.getAuthor().openPrivateChannel().queue(channel -> {
                        channel.sendMessage(lang.getString("discordAlreadyLinked")).queue();
                    });

                    return;
                }

                String uuid = MojangAPI.nameToUUID(argv[1]);
                String linkedID = db.findDiscordIDbyUUID(uuid);

                if (linkedID != null) {
                    JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onUserError"), event.getMessage(), plugin.getConfig());
                    event.getAuthor().openPrivateChannel().queue(channel -> {
                        channel.sendMessage(lang.getString("minecraftAlreadyLinked")).queue();
                    });

                    return;
                }

                db.linkUser(event.getAuthor().getId(), uuid);
                JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onSuccess"), event.getMessage(), plugin.getConfig());
            } catch (SQLException | IOException e) {
                JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onBotError"), event.getMessage(), plugin.getConfig());
                plugin.getLogger().severe("An error occured while trying to check link the user. " +
                        "Please check the stack trace below and contact the developer.");
                e.printStackTrace();
            }
        }

        void unlink(String[] argv, MessageReceivedEvent event) {
            if (!JDAUtils.hasRoleFromList(event.getAuthor(), plugin.getConfig().getStringList("adminCommandRoles"), bot)) {
                JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onPermissionError"), event.getMessage(), plugin.getConfig());
                return;
            }

            if (argv.length < 2) {
                JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onUserError"), event.getMessage(), plugin.getConfig());
            }

            try {
                String uuid = null;

                if (argv[1].length() > 16 && StringUtils.isNumeric(argv[1])) { // looks like Discord ID
                    uuid = db.findUUIDByDiscordID(argv[1]);
                } else { // try minecraft nick
                    uuid = MojangAPI.nameToUUID(argv[1]);
                }

                if (uuid == null) {
                    JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onUserError"), event.getMessage(), plugin.getConfig());
                    return;
                }

                permPlugin.setPermissions(uuid, null); // remove all managed permissions before unlinking
                db.unlink(argv[1]); // accepts uuid or discord id

                JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onSuccess"), event.getMessage(), plugin.getConfig());
            } catch (SQLException | IOException e) {
                JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onBotError"), event.getMessage(), plugin.getConfig());
                plugin.getLogger().severe("An error occured while getting info for the user. " +
                        "Please check the stack trace below and contact the developer.");
                e.printStackTrace();
            }
        }
    }
}
