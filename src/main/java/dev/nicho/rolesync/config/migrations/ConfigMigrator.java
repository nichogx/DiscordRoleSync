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
        // Version 2 is the current config.yml
        // NOTE: when updating this to 3, legacy v2 should be saved somewhere else (e.g. config_versions/2.yml)
        ConfigMigration v1to2 = new ConfigMigration(1, "config.yml");
        v1to2.renamedKey("botInfo.token", "bot.token");
        v1to2.renamedKey("botInfo.server", "bot.server");
        migrations.add(v1to2);

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
