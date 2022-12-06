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

        this.mojang = new MojangAPI(plugin);

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
                                    if (userInfo.verified || !plugin.getConfig().getBoolean("requireVerification")) {
                                        giveRoleAndNickname(member, null);
                                    } else {
                                        removeRoleAndNickname(member);
                                    }
                                    checkMemberRoles(member, userInfo);
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
        if (event.getChannel().getType() == ChannelType.PRIVATE) {
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

        if (argv[0].equalsIgnoreCase(this.plugin.getConfig().getString("commandNames.info", "info"))) {
            ch.info(argv, event);
        } else if (argv[0].equalsIgnoreCase(this.plugin.getConfig().getString("commandNames.link", "link"))) {
            ch.link(argv, event);
        } else if (argv[0].equalsIgnoreCase(this.plugin.getConfig().getString("commandNames.admLink", "admLink"))) {
            ch.admlink(argv, event);
        } else if (argv[0].equalsIgnoreCase(this.plugin.getConfig().getString("commandNames.unlink", "unlink"))) {
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
                    if (Bukkit.getOnlineMode() || plugin.getConfig().getBoolean("alwaysOnlineMode"))
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
            DatabaseHandler.LinkedUserInfo userInfo = db.getLinkedUserInfo(Objects.requireNonNull(event.getMember()).getId());
            if (userInfo != null) {
                db.removeFromWhitelist(userInfo.uuid);

                setPermissions(userInfo.uuid, null);
            }
        } catch (SQLException | NullPointerException e) {
            plugin.getLogger().severe("An error occurred while removing kicked/banned/left member from whitelist. " +
                    "Please check the stack trace below and contact the developer.");
            e.printStackTrace();
        }
    }

    void checkMemberRoles(Member member) {
        try {
            checkMemberRoles(member, db.getLinkedUserInfo(member.getId()));
        } catch (SQLException e) {
            plugin.getLogger().severe("An error occurred while looking for the UUID of a user. " +
                    "Please check the stack trace below and contact the developer.");
            e.printStackTrace();
        }
    }

    void checkMemberRoles(Member member, DatabaseHandler.LinkedUserInfo userInfo) {
        try {
            ConfigurationSection perms = plugin.getConfig().getConfigurationSection("groups");
            if (userInfo == null) { // user not linked
                return; // ignore
            }

            if (plugin.getConfig().getBoolean("requireVerification") && !userInfo.verified) {
                setPermissions(userInfo.uuid, null);
            } else {
                List<String> permsToHave = new ArrayList<>();
                for (String perm : perms.getKeys(true)) {
                    if (perms.getStringList(perm).isEmpty()) continue;
                    final boolean hasRole = JDAUtils.hasRoleFromList(member, perms.getStringList(perm));
                    if (hasRole) {
                        permsToHave.add(perm);
                    }
                }
                setPermissions(userInfo.uuid, permsToHave);
            }

            if (plugin.getConfig().getBoolean("manageWhitelist")) {
                if (JDAUtils.hasRoleFromList(member, plugin.getConfig().getStringList("whitelistRoles"))) {
                    db.addToWhitelist(userInfo.uuid);
                } else {
                    db.removeFromWhitelist(userInfo.uuid);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("An error occurred while trying to check roles for the user. " +
                    "Please check the stack trace below and contact the developer.");
            e.printStackTrace();
        }
    }

    void setPermissions(String uuid, List<String> permsToHave) {
        VaultSetGroupsTask vgt = new VaultSetGroupsTask(uuid, permsToHave);
        if (plugin.getServer().getPluginManager().getPlugin("PermissionsEx") != null) { // using PEX, do it synchronously
            vgt.runTask(plugin);
        } else {
            vgt.runTaskAsynchronously(plugin);
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
                JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onPermissionError"), event.getMessage(), plugin.getConfig(), lang.getString("noPermissionError"));
                return;
            }

            if (argv.length < 2) {
                JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onUserError"), event.getMessage(), plugin.getConfig(), lang.getString("incorrectCommandFormat"));
                return;
            }

            try {
                if (argv[1].length() > 16 && StringUtils.isNumeric(argv[1])) { // looks like Discord ID
                    DatabaseHandler.LinkedUserInfo userInfo = db.getLinkedUserInfo(argv[1]);

                    if (userInfo == null) {
                        JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onUserError"), event.getMessage(), plugin.getConfig(), lang.getString("userNotLinked"));
                        return;
                    }

                    String name;
                    String msgToSend;
                    if (Bukkit.getOnlineMode() || plugin.getConfig().getBoolean("alwaysOnlineMode")) {
                        name = mojang.onlineUuidToName(userInfo.uuid).name;
                        msgToSend = lang.getString("linkedTo") + " " + name + " (" + userInfo.uuid + ")";
                    } else {
                        msgToSend = lang.getString("linkedTo") + " " + userInfo.username + " (" + userInfo.uuid + ")";
                    }

                    JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onSuccess"), event.getMessage(), plugin.getConfig(), null);
                    JDAUtils.sendMessageWithDelete(event.getTextChannel(), msgToSend, plugin.getConfig());
                } else { // try minecraft nick
                    String uuid = mojang.nameToUUID(argv[1]).uuid;
                    DatabaseHandler.LinkedUserInfo userInfo = db.getLinkedUserInfo(uuid);

                    if (userInfo == null) {
                        JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onUserError"), event.getMessage(), plugin.getConfig(), lang.getString("userNotLinked"));
                        return;
                    }

                    bot.retrieveUserById(userInfo.discordId, true).queue(usr -> {
                        String name = "_" + lang.getString("unknownUser") + "_";
                        if (usr != null) {
                            name = usr.getAsTag();
                        }

                        JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onSuccess"), event.getMessage(), plugin.getConfig(), null);
                        JDAUtils.sendMessageWithDelete(event.getTextChannel(), lang.getString("linkedTo") + " " + name + " (" + userInfo.discordId + ")" , plugin.getConfig());
                    }, error -> { });
                }
            } catch (SQLException | IOException e) {
                JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onBotError"), event.getMessage(), plugin.getConfig(), lang.getString("commandError"));
                plugin.getLogger().severe("An error occurred while getting info for the user. " +
                        "Please check the stack trace below and contact the developer.");
                e.printStackTrace();
            }
        }

        void link(String[] argv, MessageReceivedEvent event) {
            if (argv.length < 2) {
                JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onUserError"), event.getMessage(), plugin.getConfig(), lang.getString("incorrectCommandFormat"));
                return;
            }

            try {
                this.linkUser(event.getAuthor().getId(), argv[1], event);
            } catch (SQLException | IOException e) {
                JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onBotError"), event.getMessage(), plugin.getConfig(), lang.getString("commandError"));
                plugin.getLogger().severe("An error occurred while trying to check link the user. " +
                        "Please check the stack trace below and contact the developer.");
                e.printStackTrace();
            }
        }

        void unlink(String[] argv, MessageReceivedEvent event) {
            if (!JDAUtils.hasRoleFromList(event.getMember(), plugin.getConfig().getStringList("adminCommandRoles"))) {
                JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onPermissionError"), event.getMessage(), plugin.getConfig(), lang.getString("noPermissionError"));
                return;
            }

            if (argv.length < 2) {
                JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onUserError"), event.getMessage(), plugin.getConfig(), lang.getString("incorrectCommandFormat"));
                return;
            }

            try {
                DatabaseHandler.LinkedUserInfo userInfo = null;

                if (argv[1].length() > 16 && StringUtils.isNumeric(argv[1])) { // looks like Discord ID
                    userInfo = db.getLinkedUserInfo(argv[1]);
                } else { // try minecraft nick
                    String uuid = mojang.nameToUUID(argv[1]).uuid;
                    if (uuid != null) userInfo = db.getLinkedUserInfo(uuid);
                }

                if (userInfo == null) {
                    JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onUserError"), event.getMessage(), plugin.getConfig(), lang.getString("userNotLinked"));
                    return;
                }

                setPermissions(userInfo.uuid, null); // remove all managed permissions before unlinking
                db.unlink(userInfo.uuid);

                Guild guild = bot.getGuildById(plugin.getConfig().getString("botInfo.server"));
                if (guild == null) {
                    plugin.getLogger().warning("Guild not found while trying to remove a linked role.");
                    return;
                }

                guild.retrieveMemberById(userInfo.discordId).queue(SyncBot.this::removeRoleAndNickname, err -> { });
                JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onSuccess"), event.getMessage(), plugin.getConfig(), lang.getString("successUnlink"));
            } catch (SQLException | IOException e) {
                JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onBotError"), event.getMessage(), plugin.getConfig(), lang.getString("commandError"));
                plugin.getLogger().severe("An error occurred while getting info for the user. " +
                        "Please check the stack trace below and contact the developer.");
                e.printStackTrace();
            }
        }

        void admlink(String[] argv, MessageReceivedEvent event) {
            if (!JDAUtils.hasRoleFromList(event.getMember(), plugin.getConfig().getStringList("adminCommandRoles"))) {
                JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onPermissionError"), event.getMessage(), plugin.getConfig(), lang.getString("noPermissionError"));
                return;
            }

            if (argv.length < 3) {
                JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onUserError"), event.getMessage(), plugin.getConfig(), lang.getString("incorrectCommandFormat"));
                return;
            }

            try {
                this.linkUser(argv[1], argv[2], event);
            } catch (SQLException | IOException e) {
                JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onBotError"), event.getMessage(), plugin.getConfig(), lang.getString("commandError"));
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
                    JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onUserError"), event.getMessage(), plugin.getConfig(), lang.getString("userNotLinked"));
                    JDAUtils.sendMessageWithDelete(event.getTextChannel(), lang.getString("pleaseLink"), plugin.getConfig());
                } else if (db.verify(event.getAuthor().getId(), code)) {
                    Guild guild = bot.getGuildById(plugin.getConfig().getString("botInfo.server"));
                    if (guild == null) {
                        plugin.getLogger().warning("Guild not found while trying to remove a linked role.");
                        return;
                    }

                    guild.retrieveMemberById(event.getAuthor().getId()).queue(member -> {
                        String mcUser = userInfo.username;
                        if (Bukkit.getOnlineMode() || plugin.getConfig().getBoolean("alwaysOnlineMode")) {
                            try {
                                mcUser = mojang.onlineUuidToName(userInfo.uuid).name;
                            } catch (IOException ignored) { }
                        }

                        giveRoleAndNickname(member, mcUser);
                        checkMemberRoles(member);

                        JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onSuccess"), event.getMessage(), plugin.getConfig(), lang.getString("successVerify"));
                    }, err -> { });
                } else {
                    JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onUserError"), event.getMessage(), plugin.getConfig(), lang.getString("incorrectVerificationCode"));
                }
            } catch (SQLException e) {
                JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onBotError"), event.getMessage(), plugin.getConfig(), lang.getString("commandError"));
                plugin.getLogger().severe("An error occurred while getting info for the user. " +
                        "Please check the stack trace below and contact the developer.");
                e.printStackTrace();
            }
        }

        private void linkUser(String discordId, String mcUsername, MessageReceivedEvent event) throws IOException, SQLException {
            DatabaseHandler.LinkedUserInfo userInfo = db.getLinkedUserInfo(discordId);
            if (userInfo != null) {
                JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onUserError"), event.getMessage(), plugin.getConfig(), lang.getString("discordAlreadyLinked"));
                event.getAuthor().openPrivateChannel().queue(
                        channel -> channel.sendMessage(lang.getString("discordAlreadyLinked"))
                                .queue(null, err -> { }));

                return;
            }

            MojangAPI.MojangSearchResult result = mojang.nameToUUID(mcUsername);
            String uuid = result.uuid;
            if (uuid == null) {
                JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onUserError"), event.getMessage(), plugin.getConfig(), lang.getString("unknownUser"));

                return;
            }

            userInfo = db.getLinkedUserInfo(uuid);
            if (userInfo != null) {
                JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onUserError"), event.getMessage(), plugin.getConfig(), lang.getString("minecraftAlreadyLinked"));
                event.getAuthor().openPrivateChannel().queue(
                        channel -> channel.sendMessage(lang.getString("minecraftAlreadyLinked"))
                                .queue(null, err -> { }));

                return;
            }

            db.linkUser(discordId, uuid);
            Objects.requireNonNull(bot.getGuildById(plugin.getConfig().getString("botInfo.server")))
                    .retrieveMemberById(discordId).queue(member -> {
                        if (member != null) {
                            if (!plugin.getConfig().getBoolean("requireVerification")) {
                                giveRoleAndNickname(member, result.name);
                            }
                            checkMemberRoles(member);
                        }
                    }, error -> { });
            JDAUtils.reactAndDelete(plugin.getConfig().getString("react.onSuccess"), event.getMessage(), plugin.getConfig(), lang.getString("successLink"));
        }
    }

    class VaultSetGroupsTask extends BukkitRunnable {

        private final List<String> groupsToHave;
        private final String uuid;

        public VaultSetGroupsTask(String uuid, List<String> permsToHave) {
            this.uuid = uuid;
            this.groupsToHave = permsToHave;
        }

        @Override
        public void run() {
            vault.setGroups(uuid, groupsToHave);
        }
    }
}
