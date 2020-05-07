package dev.nicho.rolesync;

import dev.nicho.rolesync.db.SQLiteHandler;
import dev.nicho.rolesync.permissionapis.PermPluginNotFoundException;
import dev.nicho.rolesync.util.JDAUtils;
import net.dv8tion.jda.api.AccountType;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import javax.security.auth.login.LoginException;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;

public class RoleSync extends JavaPlugin {

    private YamlConfiguration language = null;

    @Override
    public void onLoad() {
        getLogger().info("Reading config.yml");
        saveDefaultConfig();

        // TODO validate config

        getLogger().info("Reading language file");
        File langFile = loadLangFile(getConfig().getString("language"));
        getLogger().info("Loaded " + langFile.getName());

        language = new YamlConfiguration();
        try {
            language.load(langFile);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onEnable() {
        getLogger().info("Initializing bot");
        JDABuilder builder = JDABuilder
                .create(getConfig().getString("botInfo.token"),
                        GatewayIntent.GUILD_MEMBERS,
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.DIRECT_MESSAGES)
                .disableCache(
                        CacheFlag.EMOTE,
                        CacheFlag.VOICE_STATE,
                        CacheFlag.ACTIVITY,
                        CacheFlag.CLIENT_STATUS
                );
        try {
            builder.addEventListeners(new SyncBot(this, language));
            builder.build();
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
}
