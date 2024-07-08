package dev.nicho.rolesync.config;

import dev.nicho.rolesync.RoleSync;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.List;

public class ConfigValidator {

    private final RoleSync plugin;

    public ConfigValidator(RoleSync plugin) {
        this.plugin = plugin;
    }

    /**
     * Validates a YAML file from a String against the schema.
     *
     * @param yaml The YAML file as a String
     * @throws InvalidConfigurationException if the configuration is invalid
     */
    public void validateYaml(String yaml) throws InvalidConfigurationException {
        List<String> errors = new ArrayList<>();

        YamlConfiguration c = new YamlConfiguration();
        c.loadFromString(yaml);

        // Validate most important fields

        if (!c.isInt("configVersion")) {
            errors.add("configVersion is set incorrectly. Please do not modify this field.");
        }

        final String token = c.getString("bot.token");
        if (token == null || token.isEmpty()) {
            errors.add("Bot token is not set. Please follow setup instructions to create a bot and add it to your server.");
        }

        final String server = c.getString("bot.server");
        if (!isDiscordId(server)) {
            errors.add("Invalid server ID is set in 'bot.server'. Please set the server ID in the config.yml.");
        }

        String databaseValidation = validateDatabaseSection(c);
        if (databaseValidation != null) {
            errors.add(databaseValidation);
        }

        if (!errors.isEmpty()) {
            throw new InvalidConfigurationException("Config file failed to validate: " + errors);
        }
    }

    private String validateDatabaseSection(YamlConfiguration c) {
        ConfigurationSection db = c.getConfigurationSection("database");
        if (db == null) {
            return "Required section 'database' is missing";
        }

        String engine = db.getString("type");
        if (engine.equalsIgnoreCase("sqlite")) {
            return null;
        }

        if (engine.equalsIgnoreCase("mysql")) {
            if (db.getBoolean("mysql.disableSSL")) {
                plugin.getLogger().warning("WARNING! Using MySQL with SSL disabled. This is very unsafe. Traffic to the database is not encrypted.");
            }

            String host = db.getString("mysql.dbhost");
            if (host == null || host.isEmpty()) {
                return "You must set mysql options when using mysql instead of sqlite.";
            }

            return null;
        }

        return "Database type must be either sqlite or mysql";
    }

    private boolean isDiscordId(String val) {
        return val != null
                && !val.isEmpty()
                && !val.equals("000000000000000000")
                && val.matches("^\\d{16,}$");
    }
}
