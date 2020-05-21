package dev.nicho.rolesync;

import dev.nicho.rolesync.listeners.PlayerJoinListener;
import dev.nicho.rolesync.listeners.WhitelistLoginListener;
import dev.nicho.rolesync.util.APIException;
import dev.nicho.rolesync.util.Util;
import dev.nicho.rolesync.util.VaultAPI;
import org.bstats.bukkit.Metrics;
import dev.nicho.dependencymanager.DependencyManager;
import dev.nicho.rolesync.db.DatabaseHandler;
import dev.nicho.rolesync.db.MySQLHandler;
import dev.nicho.rolesync.db.SQLiteHandler;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import javax.security.auth.login.LoginException;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class RoleSync extends JavaPlugin {

    private YamlConfiguration language = null;
    private DatabaseHandler db = null;
    private SyncBot listener = null;
    private DependencyManager dm = null;
    private JDA jda = null;
    private VaultAPI vault = null;

    @Override
    public void onLoad() {

        File libFolder = new File(getDataFolder(), "lib");
        libFolder.mkdirs();
        dm = new DependencyManager(libFolder);

        try {
            // on maven central
            dm.addDependency(new URL("https://repo1.maven.org/maven2/org/apache/commons/commons-lang3/3.10/commons-lang3-3.10.jar"));
            dm.addDependency(new URL("https://repo1.maven.org/maven2/org/json/json/20190722/json-20190722.jar"));
            dm.addDependency(new URL("https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.31.1/sqlite-jdbc-3.31.1.jar"));
            dm.addDependency(new URL("https://repo1.maven.org/maven2/commons-dbcp/commons-dbcp/1.4/commons-dbcp-1.4.jar"));
            dm.addDependency(new URL("https://repo1.maven.org/maven2/commons-pool/commons-pool/1.6/commons-pool-1.6.jar"));

            // jda
            dm.addDependency(new URL("https://ci.dv8tion.net/job/JDA/146/artifact/build/libs/JDA-4.1.1_146-withDependencies-min.jar"));
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }

        try {
            getLogger().info("Reading config.yml");
            saveDefaultConfig();

            // TODO validate config

            loadLang();
        } catch (InvalidConfigurationException e) {
            getLogger().severe("One of the yml files is invalid. The stack trace below might have more information.");
            e.printStackTrace();
            this.setEnabled(false);
        } catch (IOException e) {
            getLogger().severe("An error occurred while loading the yml files. Please check the stack trace below and contact the developer.");
            e.printStackTrace();
            this.setEnabled(false);
        }
    }

    @Override
    public void onEnable() {

        long start = System.currentTimeMillis();
        getLogger().info("Fetching dependencies... This might take a while if this is the first start.");
        try {
            int newDownloaded = dm.downloadAll();
            if (newDownloaded == 0) {
                getLogger().info("All dependencies were already downloaded.");
            } else {
                getLogger().warning("" + newDownloaded + " new dependencies downloaded. Please restart the server.");
                setEnabled(false);
                return;
            }
        } catch (Exception e) {
            getLogger().severe("An error occurred while downloading the dependencies. Please check the stack trace below and contact the developer.");
            e.printStackTrace();
            setEnabled(false);
            return;
        }
        getLogger().info("Fetched all dependencies! (took " + (System.currentTimeMillis() - start) + "ms)");

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

            // migrations
            if (this.db.migrate()) {
                getLogger().info("Database migrated.");
            }

            // get all managed groups
            ConfigurationSection perms = getConfig().getConfigurationSection("groups");
            List<String> managedGroups = new ArrayList<>();
            for (String perm : perms.getKeys(true)) {
                if (perms.getStringList(perm).isEmpty()) continue;
                managedGroups.add(perm);
            }

            this.vault = new VaultAPI(managedGroups);
            listener = new SyncBot(this, language, this.db, this.vault);
            startBot();

        } catch (IOException | SQLException e) {
            getLogger().severe("Error setting up database");
            e.printStackTrace();
            this.setEnabled(false);

            return;
        } catch (LoginException e) {
            getLogger().severe("Error logging in. Did you set your token in config.yml?");
            this.setEnabled(false);

            return;
        } catch (APIException e) {
            getLogger().severe("Vault is not installed. Please install vault.");
            this.setEnabled(false);

            return;
        }

        // event listeners
        if (getConfig().getBoolean("manageWhitelist")) {
            getServer().getPluginManager().registerEvents(new WhitelistLoginListener(db, language, this), this);
        }
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(language, this), this);

        Metrics metrics = new Metrics(this, 7533);
        metrics.addCustomChart(new Metrics.SimplePie("used_language",
                () -> getConfig().getString("language")));

        metrics.addCustomChart(new Metrics.SimplePie("whitelist_enabled",
                () -> String.valueOf(getConfig().getBoolean("manageWhitelist"))));

        metrics.addCustomChart(new Metrics.SimplePie("delete_commands",
                () -> String.valueOf(getConfig().getBoolean("deleteCommands"))));

        metrics.addCustomChart(new Metrics.SimplePie("linked_role",
                () -> String.valueOf(getConfig().getBoolean("giveLinkedRole"))));

        metrics.addCustomChart(new Metrics.SimplePie("require_verification",
                () -> String.valueOf(getConfig().getBoolean("requireVerification"))));

        metrics.addCustomChart(new Metrics.SimplePie("change_nicknames", () -> {
            if (getConfig().getString("changeNicknames").equalsIgnoreCase("after")) {
                return "After";
            } else if (getConfig().getString("changeNicknames").equalsIgnoreCase("replace")) {
                return "Replace";
            }

            return "No"; // default is no
        }));

        metrics.addCustomChart(new Metrics.SimplePie("database_type", () -> {
            if (getConfig().getString("database.type").equalsIgnoreCase("mysql")) {
                return "MySQL";
            }

            return "SQLite"; // default is sqlite
        }));

        metrics.addCustomChart(new Metrics.SimplePie("changed_alternative_server", () -> {
            if (getConfig().getString("alternativeServer").isEmpty()) {
                return "Not changed";
            }

            return "Changed";
        }));

        metrics.addCustomChart(new Metrics.SimplePie("permissions_plugin", () -> {
            String permPlugin = vault.getPermProvider().getName();

            if (permPlugin != null && !permPlugin.isEmpty()) return permPlugin;
            return "unknown/other";
        }));

        metrics.addCustomChart(new Metrics.SingleLineChart("linked_users",
                () -> db.getLinkedUserCount()));

        // version check
        String version = getDescription().getVersion();
        String latestVersion;
        try {
            latestVersion = Util.getLatestVersion();
        } catch (IOException e) {
            e.printStackTrace();

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
            sender.sendMessage(ChatColor.BLUE + "[DRS] " + ChatColor.RESET + language.getString("usage") + "\n" +
                    ChatColor.BLUE + "[DRS] " + ChatColor.RESET + "/drs reload: " + language.getString("drsReloadDescription") + "\n" +
                    ChatColor.BLUE + "[DRS] " + ChatColor.RESET + "/drs botrestart: " + language.getString("drsBotRestartDescription") + "\n" +
                    ChatColor.BLUE + "[DRS] " + ChatColor.RESET + "/drs verify: " + language.getString("drsVerifyDescription")
            );

            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("discordrolesync.reload")) {
                sender.sendMessage(ChatColor.BLUE + "[DRS] " + ChatColor.RED + language.getString("noPermissionError"));

                return false;
            }

            try {
                reloadConfig();
                loadLang();

                sender.sendMessage(ChatColor.BLUE + "[DRS] " + ChatColor.GREEN + language.getString("reloadComplete"));

                return true;
            } catch (InvalidConfigurationException e) {
                sender.sendMessage(ChatColor.BLUE + "[DRS] " + ChatColor.RED + language.getString("commandError"));
                getLogger().severe("One of the yml files is invalid. The stack trace below might have more information.");
                e.printStackTrace();

                return false;
            } catch (IOException e) {
                sender.sendMessage(ChatColor.BLUE + "[DRS] " + ChatColor.RED + language.getString("commandError"));
                getLogger().severe("An error occurred while loading the yml files. Please check the stack trace below and contact the developer.");
                e.printStackTrace();

                return false;
            }
        } else if (args[0].equalsIgnoreCase("botrestart")) {
            if (!sender.hasPermission("discordrolesync.botrestart")) {
                sender.sendMessage(ChatColor.BLUE + "[DRS] " + ChatColor.RED + language.getString("noPermissionError"));

                return false;
            }

            jda.shutdown();

            try {
                startBot();
                sender.sendMessage(ChatColor.BLUE + "[DRS] " + ChatColor.GREEN + language.getString("botRestarted"));
            } catch (LoginException e) {
                e.printStackTrace();

                return false;
            }

            return true;
        } else if (args[0].equalsIgnoreCase("verify")) {
            if (sender instanceof Player) {
                try {
                    DatabaseHandler.LinkedUserInfo userInfo = db.getLinkedUserInfo(((Player) sender).getUniqueId().toString());
                    if (userInfo == null) {
                        sender.sendMessage(ChatColor.BLUE + "[DRS] " + ChatColor.RESET + language.getString("pleaseLink")
                                + " " + getConfig().getString("discordUrl"));
                    } else if (!userInfo.verified) {
                        sender.sendMessage(ChatColor.BLUE + "[DRS] " + ChatColor.RESET + language.getString("verificationInstructions")
                                + " " + ChatColor.AQUA + userInfo.code);
                    } else {
                        sender.sendMessage(ChatColor.BLUE + "[DRS] " + ChatColor.RESET + language.getString("alreadyVerified"));
                    }

                    return true;
                } catch (SQLException e) {
                    sender.sendMessage(ChatColor.RED + language.getString("commandError"));
                    getLogger().severe("An error occurred while getting linked user info. Please check the stack trace below and contact the developer.");
                    e.printStackTrace();

                    return false;
                }
            } else {
                sender.sendMessage(ChatColor.BLUE + "[DRS] " + ChatColor.RED + "This command can only be used in game.");

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

    private void startBot() throws LoginException {
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
    }
}
