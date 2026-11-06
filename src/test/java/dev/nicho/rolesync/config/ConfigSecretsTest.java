package dev.nicho.rolesync.config;

import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigSecretsTest {

    @Test
    void testEnvironmentVariablesOverrideConfig() {
        Map<String, String> env = new HashMap<>();
        env.put("DRS_BOT_TOKEN", "token from env");
        env.put("DRS_DB_PASSWORD", "password from env");
        ConfigSecrets secrets = new ConfigSecrets(newConfig(), env::get);

        assertEquals("token from env", secrets.getBotToken());
        assertEquals("password from env", secrets.getDbPassword());
    }

    @Test
    void testConfigIsUsedWhenEnvironmentVariablesAreUnset() {
        ConfigSecrets secrets = new ConfigSecrets(newConfig(), name -> null);

        assertEquals("token from config", secrets.getBotToken());
        assertEquals("password from config", secrets.getDbPassword());
    }

    @Test
    void testConfigIsUsedWhenEnvironmentVariablesAreEmpty() {
        ConfigSecrets secrets = new ConfigSecrets(newConfig(), name -> "");

        assertEquals("token from config", secrets.getBotToken());
        assertEquals("password from config", secrets.getDbPassword());
    }

    @Test
    void testVariablesOnlyOverrideTheirOwnKey() {
        Map<String, String> env = new HashMap<>();
        env.put("DRS_BOT_TOKEN", "token from env");
        ConfigSecrets secrets = new ConfigSecrets(newConfig(), env::get);

        assertEquals("token from env", secrets.getBotToken());
        assertEquals("password from config", secrets.getDbPassword());
    }

    private Configuration newConfig() {
        Configuration config = new MemoryConfiguration();
        config.set("bot.token", "token from config");
        config.set("database.mysql.dbpass", "password from config");
        return config;
    }
}
