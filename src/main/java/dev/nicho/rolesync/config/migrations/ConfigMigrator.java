package dev.nicho.rolesync.config.migrations;

import dev.nicho.rolesync.config.ConfigReader;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * ConfigMigrator will attempt to update the existing config.yml file
 * to a newer format. Will sequentially update from v1 to the latest version.
 */
public class ConfigMigrator {
    private final List<ConfigMigration> migrations = new ArrayList<>();
    private final int latestVersion;

    private final JavaPlugin plugin;

    public ConfigMigrator(JavaPlugin plugin) {
        this.plugin = plugin;

        // Version 1 to 2
        ConfigMigration v1to2 = new ConfigMigration(1, "config_versions/2.yml");
        v1to2.renamedKey("botInfo.token", "bot.token");
        v1to2.renamedKey("botInfo.server", "bot.server");
        migrations.add(v1to2);

        // Version 2 to 3
        // When updating to 4, change the resource path here.
        ConfigMigration v2to3 = new ConfigMigration(2, "config_versions/3.yml");
        v2to3.renamedKey("showPlayers", "botActivity.enable");
        migrations.add(v2to3);

        // Version 3 to 4
        // When updating to 5, change the resource path here.
        ConfigMigration v3to4 = new ConfigMigration(3, "config.yml");
        v3to4.function("discordRename.template", (oldCfg) -> {
            String old = oldCfg.getString("changeNicknames");
            if (old.equalsIgnoreCase("after")) {
                return "$discord_name$ ($minecraft_name$)";
            }

            if (old.equalsIgnoreCase("replace")) {
                return "$minecraft_name$";
            }

            return "";
        });
        migrations.add(v3to4);

        try {
            this.latestVersion = ConfigReader
                    .getConfigFromResource("config.yml").getInt("configVersion", 1);
        } catch (IOException e) {
            throw new IllegalStateException("Error getting resource config.yml from the .jar: " + e.getMessage());
        }
    }

    /**
     * Runs all configured migrations for this config file.
     *
     * @param config the config file to migrate
     * @return a new config file if it was migrated, null otherwise
     */
    public FileConfiguration run(FileConfiguration config) {
        int version = config.getInt("configVersion", 1);

        if (version == this.latestVersion) {
            this.plugin.getLogger().info("Found latest config version " + version);
            return null;
        }

        ConfigMigration migration = findMigration(version);
        if (migration == null) {
            this.plugin.getLogger().warning("Unable to find config migration from version " + version + " to " + this.latestVersion);
            return null;
        }

        this.plugin.getLogger().info("Running migration from version " + version);
        FileConfiguration migrated = migration.run(config);

        if (migrated.getInt("configVersion") == version) {
            this.plugin.getLogger().severe("Config migration failed. configVersion was not updated.");
            return null;
        }

        // Try migrating again
        FileConfiguration nextMigration = this.run(migrated);
        if (nextMigration != null) {
            return nextMigration;
        }

        return migrated;
    }

    private ConfigMigration findMigration(int fromVersion) {
        for (ConfigMigration m : this.migrations) {
            if (m.getFromVersion() == fromVersion) {
                return m;
            }
        }

        return null;
    }
}
