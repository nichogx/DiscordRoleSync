package dev.nicho.rolesync;

import dev.nicho.dependencymanager.DependencyManager;
import dev.nicho.rolesync.db.DatabaseHandler;
import dev.nicho.rolesync.db.MySQLHandler;
import dev.nicho.rolesync.db.SQLiteHandler;
import dev.nicho.rolesync.permissionapis.PermPluginNotFoundException;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import javax.security.auth.login.LoginException;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.SQLException;
import java.util.UUID;

public class RoleSync extends JavaPlugin {

    private YamlConfiguration language = null;
    private DatabaseHandler db = null;
    private SyncBot listener = null;
    private DependencyManager dm = null;
    private JDA jda = null;

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
            dm.downloadAll();
            dm.addAllToClasspath();
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

            listener = new SyncBot(this, language, this.db);
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
        } catch (PermPluginNotFoundException e) {
            getLogger().severe("Permission plugin was not found: " + e.getMessage());
            this.setEnabled(false);

            return;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // no arguments
        if (args.length < 1) { // print usage and return
            sender.sendMessage(language.getString("usage") + "\n" +
                    "/drs whitelist: " + language.getString("drsWhitelistDescription") + "\n" +
                    "/drs reload: " + language.getString("drsReloadDescription")
            );

            return true;
        }

        if (args[0].equalsIgnoreCase("whitelist")) {
            if (!sender.hasPermission("discordrolesync.managewhitelist")) {
                sender.sendMessage(ChatColor.RED + language.getString("noPermissionError"));

                return false;
            }

            if (args.length < 2) { // print usage and return
                sender.sendMessage(language.getString("usage") + "\n" +
                        "/drs whitelist reset: " + language.getString("drsWhitelistResetDescription")
                );

                return true;
            }

            // check for subcommands
            if (args[1].equalsIgnoreCase("reset")) {
                System.out.println("reset");
                if (!getConfig().getBoolean("manageWhitelist")) {
                    sender.sendMessage(ChatColor.RED + language.getString("whitelistNotEnabled"));

                    return false;
                }

                System.out.println("reset b4 try");

                try {
                    // delete all from whitelist
                    System.out.println("ii");
                    Bukkit.getWhitelistedPlayers().forEach(offlinePlayer -> {
                        offlinePlayer.setWhitelisted(false);
                    });

                    System.out.println("aa");
                    db.forAllWhitelisted((discordID, uuid) -> {
                        Bukkit.getOfflinePlayer(UUID.fromString(uuid)).setWhitelisted(true);
                    });
                    System.out.println("bb");

                    sender.sendMessage(ChatColor.GREEN + language.getString("whitelistResetComplete"));
                } catch (Exception e) {
                    sender.sendMessage(ChatColor.RED + language.getString("commandError"));
                    getLogger().severe("An error occured while performing the whitelist reset command. Please check stack trace " +
                            "below and contact the developer.");
                    e.printStackTrace();
                    return false;
                }

                return true;
            }

            return false;
        } else if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("discordrolesync.reload")) {
                sender.sendMessage(ChatColor.RED + language.getString("noPermissionError"));

                return false;
            }

            try {
                reloadConfig();
                loadLang();

                return true;
            } catch (InvalidConfigurationException e) {
                sender.sendMessage(ChatColor.RED + language.getString("commandError"));
                getLogger().severe("One of the yml files is invalid. The stack trace below might have more information.");
                e.printStackTrace();

                return false;
            } catch (IOException e) {
                sender.sendMessage(ChatColor.RED + language.getString("commandError"));
                getLogger().severe("An error occurred while loading the yml files. Please check the stack trace below and contact the developer.");
                e.printStackTrace();

                return false;
            }
        } else if (args[0].equalsIgnoreCase("botrestart")) {
            if (!sender.hasPermission("discordrolesync.botrestart")) {
                sender.sendMessage(ChatColor.RED + language.getString("noPermissionError"));

                return false;
            }

            jda.shutdown();

            try {
                startBot();
            } catch (LoginException e) {
                e.printStackTrace();
            }

            return true;
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
