package dev.nicho.rolesync;

import net.dv8tion.jda.api.AccountType;
import net.dv8tion.jda.api.JDABuilder;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import javax.security.auth.login.LoginException;
import java.io.File;
import java.io.IOException;

public class RoleSync extends JavaPlugin {

    @Override
    public void onLoad() {
        getLogger().info("Reading config.yml");
        saveDefaultConfig();

        // TODO validate config

        getLogger().info("Reading language file");
        File langFile = loadLangFile(getConfig().getString("language"));
        getLogger().info("Loaded " + langFile.getName());

        YamlConfiguration language = new YamlConfiguration();
        try {
            language.load(langFile);
        } catch (IOException | InvalidConfigurationException e) {
            e.printStackTrace();
        }

        getLogger().info(language.getString("test"));
    }

    @Override
    public void onEnable() {
        getLogger().info("Initializing bot");
        JDABuilder builder = new JDABuilder(AccountType.BOT);
        builder.setToken(getConfig().getString("botInfo.token"));
        builder.addEventListeners(new SyncBot(this));
        try {
            builder.build();
        } catch (LoginException e) {
            getLogger().severe("Error logging in. Did you set your token in config.yml?");
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
