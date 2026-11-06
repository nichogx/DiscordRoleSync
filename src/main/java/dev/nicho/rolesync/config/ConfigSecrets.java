package dev.nicho.rolesync.config;

import org.bukkit.configuration.Configuration;

import java.util.function.UnaryOperator;

/**
 * Reads secret config values, which can be overridden by environment variables
 * so admins don't need to store them in config.yml.
 */
public final class ConfigSecrets {

    public static final String BOT_TOKEN_ENV = "DRS_BOT_TOKEN";
    public static final String DB_PASSWORD_ENV = "DRS_DB_PASSWORD";

    private final Configuration config;
    private final UnaryOperator<String> env;

    /**
     * Creates a reader that uses the process environment variables.
     *
     * @param config the configuration to read the values from
     */
    public ConfigSecrets(Configuration config) {
        this(config, System::getenv);
    }

    /**
     * Creates a reader with a custom environment variable lookup.
     *
     * @param config the configuration to read the values from
     * @param env    returns the value of an environment variable, or null if it isn't set
     */
    public ConfigSecrets(Configuration config, UnaryOperator<String> env) {
        this.config = config;
        this.env = env;
    }

    /**
     * @return the bot token from DRS_BOT_TOKEN if set, otherwise from bot.token
     */
    public String getBotToken() {
        return get("bot.token", BOT_TOKEN_ENV);
    }

    /**
     * @return the MySQL password from DRS_DB_PASSWORD if set, otherwise from database.mysql.dbpass
     */
    public String getDbPassword() {
        return get("database.mysql.dbpass", DB_PASSWORD_ENV);
    }

    private String get(String key, String envVar) {
        String value = env.apply(envVar);
        if (value != null && !value.isEmpty()) {
            return value;
        }

        return config.getString(key);
    }
}
