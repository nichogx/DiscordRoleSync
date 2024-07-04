package dev.nicho.rolesync;

import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import dev.nicho.rolesync.bot.SyncBot;
import dev.nicho.rolesync.config.ConfigValidator;
import dev.nicho.rolesync.config.migrations.ConfigMigration;
import dev.nicho.rolesync.config.migrations.ConfigMigrator;
import dev.nicho.rolesync.db.DatabaseHandler;
import dev.nicho.rolesync.db.MySQLHandler;
import dev.nicho.rolesync.db.SQLiteHandler;
import dev.nicho.rolesync.listeners.PlayerJoinListener;
import dev.nicho.rolesync.listeners.WhitelistLoginListener;
import dev.nicho.rolesync.metrics.MetricCacher;
import dev.nicho.rolesync.util.plugin_meta.PluginVersion;
import dev.nicho.rolesync.util.vault.VaultAPI;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.milkbowl.vault.permission.Permission;
import org.apache.commons.io.FileUtils;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;

public class RoleSync extends JavaPlugin {

    private static final String defaultLanguage = "en_US";

    private YamlConfiguration language = null;
    private boolean configLoaded = false;

    private DatabaseHandler db = null;
    private SyncBot bot = null;
    private VaultAPI vault = null;

    private String chatPrefix = null;

    @Override
    public void onLoad() {
        try {
            // Delete old libraries that were downloaded with DependencyManager
            File libFolder = new File(getDataFolder(), "lib");
            if (libFolder.exists() && libFolder.isDirectory()) {
                getLogger().info("Deleting old lib folder");
                FileUtils.deleteDirectory(libFolder);
            }

            // Delete old language folder that was replaced with translations
            File langFolder = new File(getDataFolder(), "language");
            if (langFolder.exists() && langFolder.isDirectory()) {
                getLogger().info("Deleting old language folder");
                FileUtils.deleteDirectory(langFolder);
            }

            // Create the new translations folder
            Files.createDirectories(Paths.get(getDataFolder().getPath(), "translations"));
        } catch (Exception e) {
            getLogger().severe("An error occurred while updating old plugin configuration.\n" +
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

            getLogger().info("Attempting to migrate config.yml");
            ConfigMigrator migrator = new ConfigMigrator(this);
            FileConfiguration updatedConfig = migrator.run(getConfig());
            if (updatedConfig != null) {
                getLogger().info("Config file has been migrated. Validating...");
                Set<ValidationMessage> configErrors = validator.validateYaml(updatedConfig.saveToString());
                if (configErrors != null) {
                    throw new InvalidConfigurationException("Migrated config.yml failed to validate: " + configErrors);
                }

                getLogger().info("Migrated config.yml has been validated.");

                // Save old config to a backup file
                getConfig().save(Paths.get(getDataFolder().getPath(), String.format(
                        "config-bkp-%d-%d.yml",
                        getConfig().getInt("configVersion", 1),
                        System.currentTimeMillis()
                )).toString());

                // Save new config
                updatedConfig.save(Paths.get(getDataFolder().getPath(), "config.yml").toString());

                reloadConfig();
                getLogger().info("Done migrating configs!");
            } else {
                getLogger().info("No configs to migrate.");
            }

            String config = getConfig().saveToString();
            Set<ValidationMessage> configErrors = validator.validateYaml(config);
            if (configErrors != null) {
                throw new InvalidConfigurationException("config.yml failed to validate: " + configErrors);
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

            // get permissions provider (vault)
            RegisteredServiceProvider<Permission> rsp = Bukkit.getServicesManager().getRegistration(Permission.class);
            if (rsp == null) {
                throw new IllegalStateException("Vault is not loaded.");
            }

            this.vault = new VaultAPI(this, rsp.getProvider());
            this.bot = new SyncBot(this);
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
            getServer().getPluginManager().registerEvents(new WhitelistLoginListener(this), this);
        }
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);

        // bstats metrics
        registerMetrics();

        try {
            checkLatestVersion();
        } catch (IOException e) {
            getLogger().warning("Unable to run checks on the installed plugin version.\n" + e.getMessage());
        }
    }

    @Override
    public void onDisable() {
        synchronized (this) {
            // Cleanup bot
            if (this.bot != null)
                this.bot.shutdown();

            for (MetricCacher<?> cacher : metricCaches.values()) {
                cacher.stop();
            }
        }
    }

    private final Map<String, MetricCacher<?>> metricCaches = new HashMap<>();

    public void registerMetrics() {
        Metrics metrics = new Metrics(this, 7533);

        // Simple metrics, no caching required.

        metrics.addCustomChart(new SimplePie("used_language",
                () -> getConfig().getString("language")));

        metrics.addCustomChart(new SimplePie("whitelist_enabled",
                () -> String.valueOf(getConfig().getBoolean("manageWhitelist"))));

        metrics.addCustomChart(new SimplePie("delete_commands",
                () -> String.valueOf(getConfig().getBoolean("bot.legacyOptions.commandDeletion.enable"))));

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

        metrics.addCustomChart(new SimplePie("use_embeds",
                () -> String.valueOf(getConfig().getBoolean("embed.useEmbed"))));

        metrics.addCustomChart(new SimplePie("enabled_geyser_support",
                () -> String.valueOf(getConfig().getBoolean("experimental.geyser.enableGeyserSupport", false))));

        // Expensive metrics, or metrics that shouldn't run on the main thread.

        String linkedUsersChartId = "linked_users";
        MetricCacher<Integer> linkedUsersCache = new MetricCacher<>(this, () -> db.getLinkedUserCount(), 36000L);
        this.metricCaches.put(linkedUsersChartId, linkedUsersCache);

        this.getServer().getScheduler().runTaskLater(this, () -> {
            // All metrics are added a few seconds after initializing them, so that the caches have
            // time to populate.

            metrics.addCustomChart(new SingleLineChart(linkedUsersChartId, linkedUsersCache::getValue));
        }, 200L); // 10 seconds
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

            reloadConfig();
            loadLang();

            sender.sendMessage(ChatColor.BLUE + chatPrefix + ChatColor.GREEN + language.getString("reloadComplete"));

            return true;
        } else if (args[0].equalsIgnoreCase("botrestart")) {
            if (!sender.hasPermission("discordrolesync.botrestart")) {
                sender.sendMessage(ChatColor.BLUE + chatPrefix + ChatColor.RED + language.getString("noPermissionError"));

                return false;
            }

            bot.shutdown();
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
                            sender.sendMessage(ChatColor.BLUE + chatPrefix + ChatColor.RESET + language.getString("verification.instructions")
                                    .replace("%verify_command_name%", getConfig().getString("commandNames.verify", "verify"))
                                    .replace("%verification_code%", ChatColor.AQUA + String.valueOf(userInfo.code) + ChatColor.RESET));
                        } else {
                            sender.sendMessage(ChatColor.BLUE + chatPrefix + ChatColor.RESET + language.getString("verification.alreadyVerified"));
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

    private void loadLang() {
        getLogger().info("Updating custom translation files");

        int updated = updateLangFiles();
        getLogger().info(String.format("Updated %d language file%s", updated, updated == 1 ? "" : "s"));

        String lang = getConfig().getString("language");
        getLogger().info("Reading language file for " + lang);
        language = loadLangFile(lang);
        getLogger().info("Language file loaded! " + language.getString("hello"));
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
        } catch (Exception e) {
            throw new RuntimeException("Default language file not found in the .jar: " + e.getMessage());
        }

        Set<String> keys = english.getKeys(true);

        int updated = 0;

        File languageFolder = new File(getDataFolder(), "translations");
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

            String languageResource = String.format("language/%s", language);
            try (InputStream resourceStream = getResource(languageResource)) {
                if (resourceStream == null)
                    languageResource = "language/en_US.yml";
            } catch (Exception e) {
                languageResource = "language/en_US.yml";
            }

            ConfigMigration migration = new ConfigMigration(languageResource);
            FileConfiguration newConfig = migration.run(loaded);

            try {
                newConfig.save(Paths.get(getDataFolder().getPath(), "translations", language).toString());
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
     * Will load from plugin/DiscordRoleSync/translations/ if it exists,
     * otherwise will just read from the .jar.
     * If the language file does not exist, will default to en_US.
     *
     * @param language the language filename to use (without the extension).
     * @return the found or newly created file (extracted from the .jar)
     */
    private YamlConfiguration loadLangFile(String language) {
        File langFile = new File(getDataFolder(), String.format("translations/%s.yml", language));

        if (langFile.exists()) {
            getLogger().info(String.format("Found custom translation %s.yml", language));
            return YamlConfiguration.loadConfiguration(langFile);
        }

        try (
                InputStream stream = getResource(String.format("language/%s.yml", language));
                Reader reader = new InputStreamReader(stream)
        ) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (Exception e) {
            if (language.equals(defaultLanguage)) {
                throw new IllegalStateException(
                        String.format("%s.yml is the default language and was not found. This is a bug. " +
                                "Please contact the developer.", defaultLanguage)
                );
            }

            getLogger().warning(
                    String.format("Language file %s.yml does not exist in jar or in custom translation folder" +
                            ". Is it supported? Defaulting to %s.", language, defaultLanguage));
            return loadLangFile(defaultLanguage);
        }
    }

    private void startBot() {
        this.getServer().getScheduler().runTaskAsynchronously(this, () -> {
            try {
                this.bot.start();
            } catch (InvalidTokenException | IllegalArgumentException e) {
                getLogger().log(Level.SEVERE, "Error logging in. Did you set your token in config.yml?", e);

                // Switch back to main thread to disable ourselves
                this.getServer().getScheduler().runTask(this, () -> this.setEnabled(false));
            }
        });
    }

    public FileConfiguration getLanguage() {
        return language;
    }

    public DatabaseHandler getDb() {
        return db;
    }

    public VaultAPI getVault() {
        return vault;
    }
}
