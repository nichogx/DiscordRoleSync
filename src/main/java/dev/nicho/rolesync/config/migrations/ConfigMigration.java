package dev.nicho.rolesync.config.migrations;

import dev.nicho.rolesync.config.ConfigReader;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.*;

/**
 * A ConfigMigration defines a migration between two versions of a
 * config file. Will open the `to` version config, and rewrite it with
 * the values from the passed in config.
 * Does NOT support:
 * - Same key with different types
 * - Reusing key names (renaming "a" to "b" and adding a new "a")
 */
public class ConfigMigration {

    private final int fromVersion;
    private final String resourcePath;

    // A map from new name to the previous name.
    private final Map<String, String> renamed = new HashMap<>();
    private final Map<String, Object> hardcoded = new HashMap<>();

    /**
     * Creates a new ConfigMigration
     *
     * @param fromVersion  the "from" version supported. Setting to zero is
     *                     equivalent to calling ConfigMigration(resourcePath).
     * @param resourcePath the resource path of the new version
     */
    public ConfigMigration(int fromVersion, String resourcePath) {
        if (resourcePath.charAt(0) == '/') {
            throw new IllegalArgumentException("resourcePath may not start with a leading slash");
        }

        if (fromVersion < 0) {
            throw new IllegalArgumentException("Version cannot be negative");
        }

        this.fromVersion = fromVersion;
        this.resourcePath = resourcePath;
    }

    /**
     * Creates a new ConfigMigration, ignoring config versions.
     * This ConfigMigration will not check or set the configVersion key.
     *
     * @param resourcePath the resource path of the new version
     */
    public ConfigMigration(String resourcePath) {
        this(0, resourcePath);
    }

    public int getFromVersion() {
        return fromVersion;
    }

    /**
     * Will mark a key as renamed. This key will be copied from
     * the previous config into the new one.
     *
     * @param previously The previous name of the key.
     * @param name       The name to rename it to.
     */
    public void renamedKey(String previously, String name) {
        this.renamed.put(name, previously);
    }

    /**
     * Will hardcode a value for a key. This is useful if we want
     * migrated configs to have specific values, different from
     * the default one in the new version.
     *
     * @param name  The name of the key. This is the NEW KEY.
     * @param value The value it should have in the final config.
     */
    public void hardcode(String name, Object value) {
        this.hardcoded.put(name, value);
    }

    public FileConfiguration run(FileConfiguration config) {
        return run(config.saveToString());
    }

    /**
     * Will run the configured migration on the passed in yaml, returning
     * a new yaml file as a String.
     *
     * @param config The old yaml file
     * @return The new yaml file
     */
    public FileConfiguration run(String config) {
        FileConfiguration oldConfig = parseFileConfiguration(config);
        FileConfiguration newConfig = createNewConfig();

        int version = newConfig.getInt("configVersion");

        // Initializes the new config with the same-named values from the old one.
        oldConfig.getValues(true).forEach((k, v) -> {
            // Only leaf keys that also exist in the new file should be included
            if (newConfig.contains(k, true) && !oldConfig.isConfigurationSection(k)) {
                newConfig.set(k, v);
            }
        });

        // Set all hardcoded values
        this.hardcoded.forEach(newConfig::set);

        // Update all renamed keys
        this.renamed.forEach((name, previously) -> newConfig.set(name, oldConfig.get(previously)));

        // Lastly, set the new configVersion. This might have
        // been overridden by the previous steps.
        newConfig.set("configVersion", version);

        return newConfig;
    }

    private @NotNull FileConfiguration parseFileConfiguration(String config) {
        FileConfiguration oldConfig = new YamlConfiguration();
        try {
            oldConfig.loadFromString(config);
        } catch (InvalidConfigurationException e) {
            throw new IllegalArgumentException("Invalid YAML supplied");
        }

        if (fromVersion != 0) {
            int version = oldConfig.getInt("configVersion", 1);
            if (version != this.fromVersion) {
                throw new IllegalArgumentException("This migration is not configured to accept config version " + version);
            }
        }

        return oldConfig;
    }

    private FileConfiguration createNewConfig() {
        // Get the new config file from the config directory
        try {
            return ConfigReader.getConfigFromResource(this.resourcePath);
        } catch (IOException e) {
            throw new IllegalStateException("Error getting resource " + this.resourcePath + " from the .jar: " + e.getMessage());
        }
    }
}
