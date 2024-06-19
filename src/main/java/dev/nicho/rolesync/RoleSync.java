package dev.nicho.rolesync;

import dev.nicho.rolesync.db.DatabaseHandler;
import dev.nicho.rolesync.db.MySQLHandler;
import dev.nicho.rolesync.db.SQLiteHandler;
import dev.nicho.rolesync.listeners.PlayerJoinListener;
import dev.nicho.rolesync.listeners.WhitelistLoginListener;
import dev.nicho.rolesync.util.SpigotPlugin;
import dev.nicho.rolesync.util.vault.VaultAPI;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import javax.security.auth.login.LoginException;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.milkbowl.vault.permission.Permission;
import org.apache.commons.io.FileUtils;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class RoleSync extends JavaPlugin {

    private YamlConfiguration language = null;
    private DatabaseHandler db = null;
    private SyncBot listener = null;
    private JDA jda = null;
    private VaultAPI vault = null;

    private String chatPrefix = null;

    @Override
    public void onLoad() {
        try {
            // Delete old libraries that were downloaded with DependencyManager
            File libFolder = new File(getDataFolder(), "lib");
            if (libFolder.exists() && libFolder.isDirectory()) {
                getLogger().info("Deleting old dependencies from lib folder");
                FileUtils.deleteDirectory(libFolder);
            }
        } catch (Exception e) {
            getLogger().severe("An error occurred while removing old dependencies.\n" +
                    e.getMessage());
            this.setEnabled(false);
        }

        try {
            getLogger().info("Reading config.yml");
            saveDefaultConfig();

            // TODO validate config

            loadLang();
        } catch (InvalidConfigurationException e) {
            getLogger().severe("One of the yml files is invalid.\n" +
                    e.getMessage());
            this.setEnabled(false);
        } catch (IOException e) {
            getLogger().severe("An error occurred while loading the yml files.\n" +
                    e.getMessage());
            this.setEnabled(false);
        }

        this.chatPrefix = getConfig().getString("chatPrefix.text", "[DRS]") + " ";
    }

    @Override
    public void onEnable() {
        try {
            if (getConfig().getString("database.type").equalsIgnoreCase("mysql")) {
                this.db = new MySQLHandler(this,
                        getConfig().getString("database.mysql.dbhost"),
                        getConfig().getInt("database.mysql.dbport"),
                        getConfig().getString("database.mysql.dbname"),
                        getConfig().getString("database.mysql.dbuser"),
                        getConfig().getString("database.mysql.dbpass"));
            } else {
                this.db = new SQLiteHandler(this, new File(getDataFolder(), "database.db"));
            }

            // get all managed groups
            ConfigurationSection perms = getConfig().getConfigurationSection("groups");
            List<String> managedGroups = new ArrayList<>();
            for (String perm : perms.getKeys(true)) {
                if (perms.getStringList(perm).isEmpty()) continue;
                managedGroups.add(perm);
            }

            // get permissions provider (vault)
            RegisteredServiceProvider<Permission> rsp = Bukkit.getServicesManager().getRegistration(Permission.class);
            if (rsp == null) {
                throw new IllegalStateException("Vault is not loaded.");
            }

            this.vault = new VaultAPI(rsp.getProvider(), managedGroups);
            listener = new SyncBot(this, language, this.db, this.vault);
            startBot();

        } catch (IOException | SQLException e) {
            getLogger().severe("Error setting up database.\n" + e.getMessage());
            this.setEnabled(false);

            return;
        } catch (IllegalStateException e) {
            getLogger().severe("Vault is not installed/loaded. Please install vault.");
            this.setEnabled(false);

            return;
        }

        // event listeners
        if (getConfig().getBoolean("manageWhitelist")) {
            getServer().getPluginManager().registerEvents(new WhitelistLoginListener(db, language, this), this);
        }
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(language, this), this);

        Metrics metrics = new Metrics(this, 7533);
        metrics.addCustomChart(new SimplePie("used_language",
                () -> getConfig().getString("language")));

        metrics.addCustomChart(new SimplePie("whitelist_enabled",
                () -> String.valueOf(getConfig().getBoolean("manageWhitelist"))));

        metrics.addCustomChart(new SimplePie("delete_commands",
                () -> String.valueOf(getConfig().getBoolean("deleteCommands"))));

        metrics.addCustomChart(new SimplePie("linked_role",
                () -> String.valueOf(getConfig().getBoolean("giveLinkedRole"))));

        metrics.addCustomChart(new SimplePie("show_players_online",
                () -> String.valueOf(getConfig().getBoolean("showPlayers"))));

        metrics.addCustomChart(new SimplePie("require_verification",
                () -> String.valueOf(getConfig().getBoolean("requireVerification"))));

        metrics.addCustomChart(new SimplePie("message_feedback",
                () -> String.valueOf(getConfig().getBoolean("messageFeedback"))));

        metrics.addCustomChart(new SimplePie("change_nicknames", () -> {
            if (getConfig().getString("changeNicknames").equalsIgnoreCase("after")) {
                return "After";
            } else if (getConfig().getString("changeNicknames").equalsIgnoreCase("replace")) {
                return "Replace";
            }

            return "No"; // default is no
        }));

        metrics.addCustomChart(new SimplePie("database_type", () -> {
            if (getConfig().getString("database.type").equalsIgnoreCase("mysql")) {
                return "MySQL";
            }

            return "SQLite"; // default is sqlite
        }));

        metrics.addCustomChart(new SimplePie("changed_alternative_server", () -> {
            if (getConfig().getString("alternativeServer").isEmpty()) {
                return "Not changed";
            }

            return "Changed";
        }));

        metrics.addCustomChart(new SimplePie("permissions_plugin", () -> {
            String permPlugin = vault.getPermPluginName();

            if (permPlugin != null && !permPlugin.isEmpty()) return permPlugin;
            return "unknown/other";
        }));

        metrics.addCustomChart(new SingleLineChart("linked_users",
                () -> db.getLinkedUserCount()));

        // version check
        String version = getDescription().getVersion();
        String latestVersion;
        try {
            latestVersion = SpigotPlugin.getLatestVersion();
        } catch (IOException e) {
            getLogger().warning("Error while checking for latest version." + e.getMessage());

            return;
        }

        if (!latestVersion.equalsIgnoreCase(version)) {
            String message = ChatColor.AQUA + "You are not running the latest version of DiscordRoleSync. " +
                    "Current: " + ChatColor.RED + version + ChatColor.AQUA + " " +
                    "Latest: " + ChatColor.GREEN + latestVersion;

            getLogger().info(message);
        } else {
            getLogger().info("You are running the latest version of DiscordRoleSync.");
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // no arguments
        if (args.length < 1) { // print usage and return
            sender.sendMessage(ChatColor.BLUE + chatPrefix + ChatColor.RESET + language.getString("usage") + "\n" +
                    ChatColor.BLUE + chatPrefix + ChatColor.RESET + "/drs reload: " + language.getString("drsReloadDescription") + "\n" +
                    ChatColor.BLUE + chatPrefix + ChatColor.RESET + "/drs botrestart: " + language.getString("drsBotRestartDescription") + "\n" +
                    ChatColor.BLUE + chatPrefix + ChatColor.RESET + "/drs verify: " + language.getString("drsVerifyDescription")
            );

            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("discordrolesync.reload")) {
                sender.sendMessage(ChatColor.BLUE + chatPrefix + ChatColor.RED + language.getString("noPermissionError"));

                return false;
            }

            try {
                reloadConfig();
                loadLang();

                sender.sendMessage(ChatColor.BLUE + chatPrefix + ChatColor.GREEN + language.getString("reloadComplete"));

                return true;
            } catch (InvalidConfigurationException e) {
                sender.sendMessage(ChatColor.BLUE + chatPrefix + ChatColor.RED + language.getString("commandError"));
                getLogger().severe("One of the yml files is invalid.\n" + e.getMessage());

                return false;
            } catch (IOException e) {
                sender.sendMessage(ChatColor.BLUE + chatPrefix + ChatColor.RED + language.getString("commandError"));
                getLogger().severe("An error occurred while loading the yml files.\n" + e.getMessage());

                return false;
            }
        } else if (args[0].equalsIgnoreCase("botrestart")) {
            if (!sender.hasPermission("discordrolesync.botrestart")) {
                sender.sendMessage(ChatColor.BLUE + chatPrefix + ChatColor.RED + language.getString("noPermissionError"));

                return false;
            }

            jda.shutdown();

            startBot();
            sender.sendMessage(ChatColor.BLUE + chatPrefix + ChatColor.GREEN + language.getString("botRestarted"));

            return true;
        } else if (args[0].equalsIgnoreCase("verify")) {
            if (sender instanceof Player) {
                this.getServer().getScheduler().runTaskAsynchronously(this, () -> {
                    try {
                        DatabaseHandler.LinkedUserInfo userInfo = db.getLinkedUserInfo(((Player) sender).getUniqueId().toString());
                        if (userInfo == null) {
                            sender.sendMessage(ChatColor.BLUE + chatPrefix + ChatColor.RESET + language.getString("pleaseLink")
                                    + " " + getConfig().getString("discordUrl"));
                        } else if (!userInfo.verified) {
                            sender.sendMessage(ChatColor.BLUE + chatPrefix + ChatColor.RESET + language.getString("verificationInstructions")
                                    + " " + ChatColor.AQUA + userInfo.code);
                        } else {
                            sender.sendMessage(ChatColor.BLUE + chatPrefix + ChatColor.RESET + language.getString("alreadyVerified"));
                        }
                    } catch (SQLException e) {
                        sender.sendMessage(ChatColor.RED + language.getString("commandError"));
                        getLogger().severe("An error occurred while getting linked user info.\n" + e.getMessage());
                    }
                });

                return true;
            } else {
                sender.sendMessage(ChatColor.BLUE + chatPrefix + ChatColor.RED + "This command can only be used in game.");

                return false;
            }
        }

        return true;
    }

    private void loadLang() throws IOException, InvalidConfigurationException {
        getLogger().info("Reading language file");
        File langFile = loadLangFile(getConfig().getString("language"));
        getLogger().info("Loaded " + langFile.getName());

        if (language == null) language = new YamlConfiguration();

        language.load(langFile);
    }

    private File loadLangFile(String language) {
        File langFile = new File(getDataFolder(), String.format("language/%s.yml", language));

        if (!langFile.exists()) {
            getLogger().info(String.format("Language file %s.yml does not exist, extracting from jar", language));
            try {
                saveResource(String.format("language/%s.yml", language), false);
            } catch (IllegalArgumentException e) {
                getLogger().warning(
                        String.format("Language file %s.yml does not exist in jar. Is it supported?" +
                                " Defaulting to en_US.", language));
                return loadLangFile("en_US");
            }

            langFile = loadLangFile(getConfig().getString("language"));
        }

        return langFile;
    }

    private void startBot() {
        this.getServer().getScheduler().runTaskAsynchronously(this, () -> {
            try {
                getLogger().info("Initializing bot");
                JDABuilder builder = JDABuilder
                        .create(getConfig().getString("botInfo.token"),
                                GatewayIntent.GUILD_MEMBERS,
                                GatewayIntent.GUILD_MESSAGES,
                                GatewayIntent.DIRECT_MESSAGES,
                                GatewayIntent.GUILD_BANS)
                        .disableCache(
                                CacheFlag.EMOTE,
                                CacheFlag.VOICE_STATE,
                                CacheFlag.ACTIVITY,
                                CacheFlag.CLIENT_STATUS
                        );

                builder.addEventListeners(listener);
                this.jda = builder.build();

                if (getConfig().getBoolean("showPlayers")) {
                    RoleSync that = this;
                    this.getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
                        String template = language.getString("playersOnline");
                        if (template == null) {
                            template = "%d/%d players";
                        }

                        String msg = String.format(template, that.getServer().getOnlinePlayers().size(), that.getServer().getMaxPlayers());
                        that.jda.getPresence().setActivity(Activity.playing(msg));
                    }, 0L, 36000L); // run every 30 minutes
                }
            } catch (LoginException e) {
                getLogger().log(Level.SEVERE, "Error logging in. Did you set your token in config.yml?", e);

                // Switch back to main thread to disable ourselves
                this.getServer().getScheduler().runTask(this, () -> this.setEnabled(false));
            }
        });
    }
}
