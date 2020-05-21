package dev.nicho.rolesync;

import dev.nicho.rolesync.db.DatabaseHandler;
import dev.nicho.rolesync.util.VaultAPI;
import dev.nicho.rolesync.util.JDAUtils;
import dev.nicho.rolesync.util.MojangAPI;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.PermissionException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class SyncBot extends ListenerAdapter {

    private final JavaPlugin plugin;
    private final DatabaseHandler db;
    private CommandHandler ch = null;
    private final YamlConfiguration lang;
    private final VaultAPI vault;
    private JDA bot = null;
    private final MojangAPI mojang;

    public SyncBot(@Nonnull JavaPlugin plugin, YamlConfiguration language, DatabaseHandler db, VaultAPI vault) {
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

        this.vault = vault;
    }

    @Override
    public void onReady(@Nonnull ReadyEvent event) {
        this.bot = event.getJDA();
        this.ch = new CommandHandler();

        plugin.getLogger().info("Logged in: " + event.getJDA().getSelfUser().getName());

        try {
            db.forAllLinkedUsers((userInfo) ->
                    Objects.requireNonNull(bot.getGuildById(this.plugin.getConfig().getString("botInfo.server")))
                            .retrieveMemberById(userInfo.discordId).queue(member -> {
                                if (member != null) {
                                    if (userInfo.verified) {
                                        giveRoleAndNickname(member, null);
                                    } else {
                                        removeRoleAndNickname(member);
                                    }
                                    checkMemberRoles(member, userInfo.uuid);
                                }
                            }, error -> { }));
        } catch (SQLException e) {
            plugin.getLogger().severe("An error occurred while checking all users. " +
                    "Please check the stack trace below and contact the developer.");
            e.printStackTrace();
        }
    }

    @Override
    public void onMessageReceived(@Nonnull MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return; // ignore bots

        String message = event.getMessage().getContentRaw();
        if (plugin.getConfig().getBoolean("requireVerification") && event.getChannel().getType() == ChannelType.PRIVATE) {
            ch.verify(message.split(" "), event);
        }

        String prefix = this.plugin.getConfig().getString("botInfo.prefix");
        if (message.length() < prefix.length() || !message.startsWith(prefix)) return; // ignore if no prefix

        if (!plugin.getConfig().getStringList("botInfo.channelsToListen").contains(event.getChannel().getId())
                && !JDAUtils.hasRoleFromList(event.getMember(), plugin.getConfig().getStringList("adminCommandRoles"))) {
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
    public void onGuildMemberJoin(@Nonnull GuildMemberJoinEvent event) {
        try {
            DatabaseHandler.LinkedUserInfo userInfo = db.getLinkedUserInfo(event.getMember().getId());
            if (userInfo != null) {
                if (!plugin.getConfig().getBoolean("requireVerification") || userInfo.verified) {
                    if (Bukkit.getOnlineMode())
                        giveRoleAndNickname(event.getMember(), mojang.onlineUuidToName(userInfo.uuid).name);
                    else
                        giveRoleAndNickname(event.getMember(), userInfo.username);
                }
            }
        } catch (SQLException | IOException e) {
            plugin.getLogger().severe("An error occurred while checking if a new member is linked. " +
                    "Please check the stack trace below and contact the developer.");
            e.printStackTrace();
        }
    }

    @Override
    public void onGuildMemberRemove(@Nonnull GuildMemberRemoveEvent event) {
        try {
            String uuid = db.findUUIDByDiscordID(Objects.requireNonNull(event.getMember()).getId());
            if (uuid != null) {
                db.removeFromWhitelist(uuid);

                setPermissions(uuid, null);
            }
        } catch (SQLException | NullPointerException e) {
            plugin.getLogger().severe("An error occurred while removing kicked/banned/left member from whitelist. " +
                    "Please check the stack trace below and contact the developer.");
            e.printStackTrace();
        }
    }

    void checkMemberRoles(Member member) {
        try {
            checkMemberRoles(member, db.findUUIDByDiscordID(member.getId()));
        } catch (SQLException e) {
            plugin.getLogger().severe("An error occurred while looking for the UUID of a user. " +
                    "Please check the stack trace below and contact the developer.");
            e.printStackTrace();
        }
    }

    void checkMemberRoles(Member member, String uuid) {
        try {
            ConfigurationSection perms = plugin.getConfig().getConfigurationSection("groups");
            if (uuid == null) { // user not linked
                return; // ignore
            }

            List<String> permsToHave = new ArrayList<>();
            for (String perm : perms.getKeys(true)) {
                if (perms.getStringList(perm).isEmpty()) continue;
                final boolean hasRole = JDAUtils.hasRoleFromList(member, perms.getStringList(perm));
                if (hasRole) {
                    permsToHave.add(perm);
                }
            }
            setPermissions(uuid, permsToHave);


            if (plugin.getConfig().getBoolean("manageWhitelist")) {
                if (JDAUtils.hasRoleFromList(member, plugin.getConfig().getStringList("whitelistRoles"))) {
                    db.addToWhitelist(uuid);
                } else {
                    db.removeFromWhitelist(uuid);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("An error occurred while trying to check roles for the user. " +
                    "Please check the stack trace below and contact the developer.");
            e.printStackTrace();
        }
    }

    void setPermissions(String uuid, List<String> permsToHave) {
        VaultSetPermissionsTask vpt = new VaultSetPermissionsTask(uuid, permsToHave);
        if (plugin.getServer().getPluginManager().getPlugin("PermissionsEx") != null) { // using PEX, do it synchronously
            vpt.runTask(plugin);
        } else {
            vpt.runTaskAsynchronously(plugin);
        }
    }

    void removeRoleAndNickname(Member member) {
        try {
            if (plugin.getConfig().getBoolean("giveLinkedRole")) {
                // remove role
                Role role = member.getGuild().getRoleById(plugin.getConfig().getString("linkedRole"));
                if (role == null) {
                    plugin.getLogger().warning("Linked role does not exist.");
                    return;
                }

                member.getGuild().removeRoleFromMember(member, role).queue(null,
                        error -> plugin.getLogger().warning("Error while adding role: " + error.getMessage()));
            }
        } catch (PermissionException e) {
            plugin.getLogger().warning("Bot has no permissions to remove roles for a user.");
        }

        try {
            // reset nickname
            if (plugin.getConfig().getString("changeNicknames").equalsIgnoreCase("after")) {
                member.modifyNickname(null).queue(null, error -> { });
            }
        } catch (PermissionException e) {
            plugin.getLogger().warning("Bot has no permissions to reset nickname of a user.");
        }
    }

    void giveRoleAndNickname(@Nonnull Member member, String mcUser) {
        try {
            if (mcUser != null) {
                if (plugin.getConfig().getString("changeNicknames").equalsIgnoreCase("after")) {
                    member.modifyNickname(member.getUser().getName() + " (" + mcUser + ")").queue(null, error -> { });
                } else if (plugin.getConfig().getString("changeNicknames").equalsIgnoreCase("replace")) {
                    member.modifyNickname(mcUser).queue(null, error -> { });
                }
            }
        } catch (PermissionException e) {
            plugin.getLogger().warning("Bot has no permissions to change nicknames for a user.");
        }

        try {
            if (plugin.getConfig().getBoolean("giveLinkedRole")) {
                Role role = member.getGuild().getRoleById(plugin.getConfig().getString("linkedRole"));
                if (role == null) {
                    plugin.getLogger().warning("Linked role does not exist.");
                    return;
                }

                member.getGuild().addRoleToMember(member, role).queue(null,
                        error -> plugin.getLogger().warning("Error while adding role: " + error.getMessage()));
            }
        } catch (PermissionException e) {
            plugin.getLogger().warning("Bot has no permissions to add roles for a user.");
        }
    }

    class CommandHandler {

        void info(String[] argv, MessageReceivedEvent event) {
            if (!JDAUtils.hasRoleFromList(event.getMember(), plugin.getConfig().getStringList("adminCommandRoles"))) {
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

                    String name;
                    String msgToSend = lang.getString("linkedTo") + " " + uuid;
                    if (Bukkit.getOnlineMode()) {
                        name = mojang.onlineUuidToName(uuid).name;
                        msgToSend = lang.getString("linkedTo") + " " + name + " (" + uuid + ")";
                    }

                    JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onSuccess"), event.getMessage(), plugin.getConfig());
                    event.getChannel().sendMessage(msgToSend)
                            .queue(msg -> {
                                if (plugin.getConfig().getBoolean("deleteCommands"))
                                    msg.delete().queueAfter(plugin.getConfig().getInt("deleteAfter"), TimeUnit.SECONDS);
                            });

                } else { // try minecraft nick
                    String uuid = mojang.nameToUUID(argv[1]).uuid;
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
                plugin.getLogger().severe("An error occurred while getting info for the user. " +
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
                    event.getAuthor().openPrivateChannel().queue(
                            channel -> channel.sendMessage(lang.getString("discordAlreadyLinked"))
                                    .queue(null, err -> { }));

                    return;
                }

                MojangAPI.MojangSearchResult result = mojang.nameToUUID(argv[1]);
                String uuid = result.uuid;
                String linkedID = db.findDiscordIDbyUUID(uuid);

                if (uuid == null) {
                    JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onUserError"), event.getMessage(), plugin.getConfig());

                    return;
                }

                if (linkedID != null) {
                    JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onUserError"), event.getMessage(), plugin.getConfig());
                    event.getAuthor().openPrivateChannel().queue(
                            channel -> channel.sendMessage(lang.getString("minecraftAlreadyLinked"))
                                    .queue(null, err -> { }));

                    return;
                }

                db.linkUser(event.getAuthor().getId(), uuid);
                if (!plugin.getConfig().getBoolean("requireVerification")) giveRoleAndNickname(event.getMember(), result.name);
                JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onSuccess"), event.getMessage(), plugin.getConfig());
            } catch (SQLException | IOException e) {
                JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onBotError"), event.getMessage(), plugin.getConfig());
                plugin.getLogger().severe("An error occurred while trying to check link the user. " +
                        "Please check the stack trace below and contact the developer.");
                e.printStackTrace();
            }
        }

        void unlink(String[] argv, MessageReceivedEvent event) {
            if (!JDAUtils.hasRoleFromList(event.getMember(), plugin.getConfig().getStringList("adminCommandRoles"))) {
                JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onPermissionError"), event.getMessage(), plugin.getConfig());
                return;
            }

            if (argv.length < 2) {
                JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onUserError"), event.getMessage(), plugin.getConfig());
            }

            try {
                String uuid;
                String discordID;

                if (argv[1].length() > 16 && StringUtils.isNumeric(argv[1])) { // looks like Discord ID
                    uuid = db.findUUIDByDiscordID(argv[1]);
                    discordID = argv[1];
                } else { // try minecraft nick
                    uuid = mojang.nameToUUID(argv[1]).uuid;
                    discordID = db.findDiscordIDbyUUID(uuid);
                }

                if (uuid == null) {
                    JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onUserError"), event.getMessage(), plugin.getConfig());
                    return;
                }

                setPermissions(uuid, null); // remove all managed permissions before unlinking
                db.unlink(uuid);

                if (discordID != null) {
                    Guild guild = bot.getGuildById(plugin.getConfig().getString("botInfo.server"));
                    if (guild == null) {
                        plugin.getLogger().warning("Guild not found while trying to remove a linked role.");
                        return;
                    }

                    guild.retrieveMemberById(discordID).queue(SyncBot.this::removeRoleAndNickname, err -> { });
                }
                JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onSuccess"), event.getMessage(), plugin.getConfig());
            } catch (SQLException | IOException e) {
                JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onBotError"), event.getMessage(), plugin.getConfig());
                plugin.getLogger().severe("An error occurred while getting info for the user. " +
                        "Please check the stack trace below and contact the developer.");
                e.printStackTrace();
            }
        }

        void verify(String[] argv, MessageReceivedEvent event) {
            String received = argv[0].trim();

            int code;
            try {
                if (!StringUtils.isNumeric(received) || received.length() != 6)
                    throw new NumberFormatException("Must be 6 digits long");

                code = Integer.parseInt(received);
            } catch (NumberFormatException e) {
                // TODO not a verification code

                return;
            }

            try {
                DatabaseHandler.LinkedUserInfo userInfo = db.getLinkedUserInfo(event.getAuthor().getId());
                if (userInfo == null) {
                    JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onUserError"), event.getMessage(), plugin.getConfig());
                    event.getChannel().sendMessage(lang.getString("pleaseLink")).queue(null, err -> { });
                } else if (db.verify(event.getAuthor().getId(), code)) {
                    Guild guild = bot.getGuildById(plugin.getConfig().getString("botInfo.server"));
                    if (guild == null) {
                        plugin.getLogger().warning("Guild not found while trying to remove a linked role.");
                        return;
                    }

                    guild.retrieveMemberById(event.getAuthor().getId()).queue(member -> {
                        String mcUser = userInfo.username;
                        if (Bukkit.getOnlineMode()) {
                            try {
                                mcUser = mojang.onlineUuidToName(userInfo.uuid).name;
                            } catch (IOException ignored) { }
                        }

                        giveRoleAndNickname(member, mcUser);

                        JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onSuccess"), event.getMessage(), plugin.getConfig());
                    }, err -> { });
                } else {
                    JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onUserError"), event.getMessage(), plugin.getConfig());
                }
            } catch (SQLException e) {
                JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onBotError"), event.getMessage(), plugin.getConfig());
                plugin.getLogger().severe("An error occurred while getting info for the user. " +
                        "Please check the stack trace below and contact the developer.");
                e.printStackTrace();
            }
        }
    }

    class VaultSetPermissionsTask extends BukkitRunnable {

        private final List<String> permsToHave;
        private final String uuid;

        public VaultSetPermissionsTask(String uuid, List<String> permsToHave) {
            this.uuid = uuid;
            this.permsToHave = permsToHave;
        }

        @Override
        public void run() {
            vault.setPermissions(uuid, permsToHave);
        }
    }
}
