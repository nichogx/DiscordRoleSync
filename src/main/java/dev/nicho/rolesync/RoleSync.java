package dev.nicho.rolesync;

import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import dev.nicho.rolesync.db.DatabaseHandler;
import dev.nicho.rolesync.db.MySQLHandler;
import dev.nicho.rolesync.db.SQLiteHandler;
import dev.nicho.rolesync.listeners.PlayerJoinListener;
import dev.nicho.rolesync.listeners.WhitelistLoginListener;
import dev.nicho.rolesync.util.config.ConfigValidator;
import dev.nicho.rolesync.util.plugin_meta.PluginVersion;
import dev.nicho.rolesync.util.vault.VaultAPI;

import java.io.*;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.*;
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
import org.bukkit.scheduler.BukkitTask;

public class RoleSync extends JavaPlugin {

    private static final String defaultLanguage = "en_US";

    private YamlConfiguration language = null;
    private boolean configLoaded = false;

    private DatabaseHandler db = null;
    private SyncBot syncBot = null;
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

            ConfigValidator validator;
            try (InputStream schemaStream = getResource("config_schema.json")) {
                JsonSchemaFactory schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
                JsonSchema schema = schemaFactory.getSchema(schemaStream);
                validator = new ConfigValidator(schema);
            }

            String config = getConfig().saveToString();
            Set<ValidationMessage> configErrors = validator.validateYaml(config);
            if (configErrors != null) {
                throw new InvalidConfigurationException("config.yaml failed to validate: " + configErrors);
            }

            loadLang();
        } catch (InvalidConfigurationException e) {
            getLogger().severe("One of the yml files is invalid.\n" +
                    e.getMessage());
            this.setEnabled(false);
            return;
        } catch (IOException e) {
            getLogger().severe("An error occurred while loading the yml files.\n" +
                    e.getMessage());
            this.setEnabled(false);
            return;
        }

        this.chatPrefix = getConfig().getString("chatPrefix.text", "[DRS]") + " ";

        this.configLoaded = true;
    }

    @Override
    public void onEnable() {
        if (!this.configLoaded) {
            getLogger().severe("Not enabling DiscordRoleSync since the config files failed to load. " +
                    "Make sure no errors are shown when loading the config and language files.");
            this.setEnabled(false);

            return;
        }

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
            this.syncBot = new SyncBot(this, language, this.db, this.vault);
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
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(db, language, this), this);

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

        try {
            checkLatestVersion();
        } catch (IOException e) {
            getLogger().warning("Unable to run checks on the installed plugin version.\n" + e.getMessage());
        }
    }

    @Override
    public void onDisable() {
        // Cleanup bot
        synchronized (this) {
            this.jda.shutdown();
        }
    }

    /**
     * Checks the installed version against the latest available, and logs
     * appropriate messages if the user is running either old or unsupported versions.
     *
     * @throws IOException if we need to and can't contact Spigot.
     */
    private void checkLatestVersion() throws IOException {
        String installedVersion = getDescription().getVersion();
        PluginVersion.VersionType versionType = PluginVersion.getVersionType(installedVersion);
        if (versionType != PluginVersion.VersionType.RELEASE) {
            // Server is running an unsupported version. Alert the user.
            getLogger().warning(language.getString("nonReleaseVersion.running." + versionType.toString()));
            return;
        }

        // Server is running a release version. Check for updates and send if available.
        PluginVersion v = new PluginVersion();
        String latestVersion = v.getLatestVersion();
        if (v.isOldRelease(installedVersion)) {
            getLogger().warning(String.format(
                    "%s %s %s, %s %s",
                    language.getString("notLatestVersion"),
                    language.getString("current"), installedVersion,
                    language.getString("latest"), latestVersion
            ));
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

            syncBot.stopTimers();
            synchronized (this) {
                jda.shutdown();
            }

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
        getLogger().info("Updating language files");

        int updated = updateLangFiles();
        getLogger().info(String.format("Updated %d language file%s", updated, updated == 1 ? "" : "s"));

        getLogger().info("Reading language file");
        File langFile = loadLangFile(getConfig().getString("language"));
        getLogger().info("Loaded " + langFile.getName());

        if (language == null) language = new YamlConfiguration();

        language.load(langFile);
    }

    /**
     * Updates all language files in the data folder to add potentially missing
     * keys, using the .jar's en_US as source of truth.
     * If any keys are missing, they will be added from the same language.yml in
     * the .jar, and from en_US as a fallback.
     *
     * @return how many files were updated
     */
    private int updateLangFiles() {
        // Get all keys that are supposed to exist, from en_US in the .jar
        YamlConfiguration english;
        try (
                InputStream stream = getResource(String.format("language/%s.yml", defaultLanguage));
                Reader reader = new InputStreamReader(stream)
        ) {
            english = YamlConfiguration.loadConfiguration(reader);
        } catch (IOException e) {
            throw new RuntimeException("Default language file not found in the .jar.");
        }

        Set<String> keys = english.getKeys(true);

        int updated = 0;

        File languageFolder = new File(getDataFolder(), "language");
        if (!languageFolder.isDirectory()) {
            // If the language folder does not exist, then there are no
            // language files to update.
            return 0;
        }

        // Go through all existing language files, checking if each needs to be updated
        Iterator<File> dir = FileUtils.iterateFiles(languageFolder, new String[]{"yml"}, false);
        while (dir.hasNext()) {
            File file = dir.next();
            String language = file.getName();

            YamlConfiguration loaded = YamlConfiguration.loadConfiguration(file);
            ArrayList<String> missingKeys = new ArrayList<>();
            for (String key : keys) {
                if (!loaded.contains(key)) {
                    missingKeys.add(key);
                }
            }

            // No keys are missing from this file, move on!
            if (missingKeys.isEmpty()) continue;
            getLogger().info(String.format("Language file %s is missing keys, updating...", language));

            YamlConfiguration readFrom;
            try (
                    InputStream resourceStream = getResource(String.format("language/%s", language));
                    Reader reader = new InputStreamReader(resourceStream)
            ) {
                readFrom = YamlConfiguration.loadConfiguration(reader);
            } catch (Exception e) {
                readFrom = english;
            }

            for (String key : missingKeys) {
                loaded.set(key, readFrom.get(key));
            }

            try {
                loaded.save(Paths.get(getDataFolder().getPath(), "language", language).toString());
            } catch (IOException e) {
                getLogger().severe("Failed to update language file." + e.getMessage());
                continue;
            }

            updated++;
        }

        return updated;
    }

    /**
     * Loads the requested language file.
     * If the language file does not exist, will default to en_US.
     *
     * @param language the language filename to use (without the extension).
     * @return the found or newly created file (extracted from the .jar)
     */
    private File loadLangFile(String language) {
        File langFile = new File(getDataFolder(), String.format("language/%s.yml", language));

        if (langFile.exists()) {
            return langFile;
        }

        getLogger().info(String.format("Language file %s.yml does not exist, extracting from jar", language));

        try {
            saveResource(String.format("language/%s.yml", language), false);
        } catch (IllegalArgumentException e) {
            getLogger().warning(
                    String.format("Language file %s.yml does not exist in jar. Is it supported?" +
                            " Defaulting to %s.", language, defaultLanguage));
            return loadLangFile(defaultLanguage);
        }

        return loadLangFile(getConfig().getString("language"));
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

                builder.addEventListeners(this.syncBot);

                synchronized (this) {
                    this.jda = builder.build();
                }
            } catch (LoginException e) {
                getLogger().log(Level.SEVERE, "Error logging in. Did you set your token in config.yml?", e);

                // Switch back to main thread to disable ourselves
                this.getServer().getScheduler().runTask(this, () -> this.setEnabled(false));
            }
        });
    }
}
