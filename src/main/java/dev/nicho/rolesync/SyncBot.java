package dev.nicho.rolesync;

import dev.nicho.rolesync.db.DatabaseHandler;
import dev.nicho.rolesync.permissionapis.LuckPermsAPI;
import dev.nicho.rolesync.permissionapis.PermPluginNotFoundException;
import dev.nicho.rolesync.permissionapis.PermissionsAPI;
import dev.nicho.rolesync.util.JDAUtils;
import dev.nicho.rolesync.util.MojangAPI;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class SyncBot extends ListenerAdapter {

    private JavaPlugin plugin = null;
    private DatabaseHandler db = null;
    private CommandHandler ch = null;
    private YamlConfiguration lang = null;
    private PermissionsAPI permPlugin = null;
    private JDA bot = null;
    private MojangAPI mojang = null;

    public SyncBot(@Nonnull JavaPlugin plugin, YamlConfiguration language, DatabaseHandler db) throws PermPluginNotFoundException {
        super();
        this.plugin = plugin;
        this.lang = language;
        this.db = db;
        plugin.getLogger().info("Finished initializing bot.");

        String alternateServer = plugin.getConfig().getString("alternativeServer");
        if (alternateServer.isEmpty()) {
            this.mojang = new MojangAPI();
        } else {
            this.mojang = new MojangAPI(alternateServer);
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

        try {
            db.forAllLinkedUsers((discordID, uuid) -> {
                bot.getGuildById(this.plugin.getConfig().getString("botInfo.server")).retrieveMemberById(discordID).queue(member -> {
                    if (member != null) {
                        checkMemberRoles(member, uuid);
                    }
                }, error -> {  });
            });
        } catch (SQLException e) {
            plugin.getLogger().severe("An error occured while checking all users. " +
                    "Please check the stack trace below and contact the developer.");
            e.printStackTrace();
        }
    }

    @Override
    public void onMessageReceived(@Nonnull MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return; // ignore bots

        String message = event.getMessage().getContentRaw();
        String prefix = this.plugin.getConfig().getString("botInfo.prefix");

        if (!message.substring(0, prefix.length()).equals(prefix)) return; // ignore if no prefix

        if (!plugin.getConfig().getStringList("botInfo.channelsToListen").contains(event.getChannel().getId())
                && !JDAUtils.hasRoleFromList(event.getMember(), plugin.getConfig().getStringList("adminCommandRoles"), bot)) {
            return; // ignore
        }

        String[] argv = message.split(" ");
        argv[0] = argv[0].substring(prefix.length()); // remove prefix

        if (argv[0].equalsIgnoreCase("info")) {
            ch.info(argv, event);
        } else if (argv[0].equalsIgnoreCase("link")) {
            ch.link(argv, event);
            checkMemberRoles(event.getMember());
        } else if (argv[0].equalsIgnoreCase("unlink")) {
            ch.unlink(argv, event);
        }

    }


    @Override
    public void onGuildMemberRoleAdd(@Nonnull GuildMemberRoleAddEvent event) {
        checkMemberRoles(event.getMember());
    }

    @Override
    public void onGuildMemberRoleRemove(@Nonnull GuildMemberRoleRemoveEvent event) {
        checkMemberRoles(event.getMember());
    }

    @Override
    public void onGuildMemberRemove(@Nonnull GuildMemberRemoveEvent event) {
        try {
            String uuid = db.findUUIDByDiscordID(event.getMember().getId());
            if (uuid != null) {
                db.removeFromWhitelist(uuid);
                Bukkit.getOfflinePlayer(UUID.fromString(uuid)).setWhitelisted(false);

                permPlugin.setPermissions(uuid, null);
            }
        } catch (SQLException | NullPointerException e) {
            plugin.getLogger().severe("An error occured while removing kicked/banned/left member from whitelist. " +
                    "Please check the stack trace below and contact the developer.");
            e.printStackTrace();
        }
    }

    void checkMemberRoles(Member member) {
        try {
            checkMemberRoles(member, db.findUUIDByDiscordID(member.getId()));
        } catch (SQLException e) {
            plugin.getLogger().severe("An error occured while looking for the UUID of a user. " +
                    "Please check the stack trace below and contact the developer.");
            e.printStackTrace();
        }
    }

    void checkMemberRoles(Member member, String uuid) {
        try {
            ConfigurationSection perms = plugin.getConfig().getConfigurationSection("permissions");
            if (uuid == null) { // user not linked
                return; // ignore
            }

            List<String> permsToHave = new ArrayList<String>();
            for (String perm : perms.getKeys(true)) {
                if (perms.getStringList(perm).isEmpty()) continue;
                final boolean hasRole = JDAUtils.hasRoleFromList(member, perms.getStringList(perm), bot);
                if (hasRole) {
                    permsToHave.add(perm);
                }
            }
            permPlugin.setPermissions(uuid, permsToHave);


            if (plugin.getConfig().getBoolean("manageWhitelist")) {
                if (JDAUtils.hasRoleFromList(member, plugin.getConfig().getStringList("whitelistRoles"), bot)) {
                    System.out.println("User has whitelist: " + member.getUser().getAsTag());
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
            if (!JDAUtils.hasRoleFromList(event.getMember(), plugin.getConfig().getStringList("adminCommandRoles"), bot)) {
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

                    String name = mojang.uuidToName(uuid);

                    JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onSuccess"), event.getMessage(), plugin.getConfig());
                    event.getChannel().sendMessage(lang.getString("linkedTo") + " " + name + " (" + uuid + ")" )
                            .queue(msg -> {
                                if (plugin.getConfig().getBoolean("deleteCommands"))
                                    msg.delete().queueAfter(plugin.getConfig().getInt("deleteAfter"), TimeUnit.SECONDS);
                            });

                } else { // try minecraft nick
                    String uuid = mojang.nameToUUID(argv[1]);
                    String id = db.findDiscordIDbyUUID(uuid);

                    if (id == null) {
                        JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onUserError"), event.getMessage(), plugin.getConfig());
                        return;
                    }

                    bot.retrieveUserById(id, true).queue(usr -> {
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
                    }, error -> { });
                }
            } catch (SQLException | IOException e) {
                JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onBotError"), event.getMessage(), plugin.getConfig());
                plugin.getLogger().severe("An error occured while getting info for the user. " +
                        "Please check the stack trace below and contact the developer.");
                e.printStackTrace();
            }
        }

        void link(String[] argv, MessageReceivedEvent event) {
            if (argv.length < 2) {
                JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onUserError"), event.getMessage(), plugin.getConfig());
            }

            try {
                String linkedUUID = db.findUUIDByDiscordID(event.getAuthor().getId());
                if (linkedUUID != null) {
                    JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onUserError"), event.getMessage(), plugin.getConfig());
                    event.getAuthor().openPrivateChannel().queue(channel -> {
                        channel.sendMessage(lang.getString("discordAlreadyLinked")).queue(null, err -> { });
                    });

                    return;
                }

                String uuid = mojang.nameToUUID(argv[1]);
                String linkedID = db.findDiscordIDbyUUID(uuid);

                if (uuid == null) {
                    JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onUserError"), event.getMessage(), plugin.getConfig());
                }

                if (linkedID != null) {
                    JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onUserError"), event.getMessage(), plugin.getConfig());
                    event.getAuthor().openPrivateChannel().queue(channel -> {
                        channel.sendMessage(lang.getString("minecraftAlreadyLinked")).queue(null, err -> { });
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
            if (!JDAUtils.hasRoleFromList(event.getMember(), plugin.getConfig().getStringList("adminCommandRoles"), bot)) {
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
                    uuid = mojang.nameToUUID(argv[1]);
                }

                if (uuid == null) {
                    JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onUserError"), event.getMessage(), plugin.getConfig());
                    return;
                }

                permPlugin.setPermissions(uuid, null); // remove all managed permissions before unlinking
                if (plugin.getConfig().getBoolean("manageWhitelist"))
                    Bukkit.getOfflinePlayer(UUID.fromString(uuid)).setWhitelisted(false); // remove whitelist before unlinking
                db.unlink(uuid); // accepts uuid or discord id

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
