package dev.nicho.rolesync.config.linter;

import dev.nicho.rolesync.config.ConfigReader;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class ConfigLinterTest {

    @Test
    void testBasicValidConfig() {
        ConfigLinter linter = new ConfigLinter();
        Configuration testConfig = newBasicValidConfig();
        assertEmpty(linter.run(testConfig));
    }

    @Test
    void testRequiresConfigVersion() {
        ConfigLinter linter = new ConfigLinter();
        Configuration testConfig = newBasicValidConfig();

        testConfig.set("configVersion", "not an int");
        LintResult result = linter.run(testConfig);
        assertEquals(1, result.asMap().size());
        assertContainsError(result, "Requires config version", "configVersion is set incorrectly. Please do not modify this field.");

        testConfig.set("configVersion", null);
        result = linter.run(testConfig);
        assertEquals(1, result.asMap().size());
        assertContainsError(result, "Requires config version", "configVersion is set incorrectly. Please do not modify this field.");
    }

    @Test
    void testValidateBotEssentialConfig() {
        ConfigLinter linter = new ConfigLinter();
        Configuration testConfig = newBasicValidConfig();

        testConfig.set("bot.token", null);
        LintResult result = linter.run(testConfig);
        assertEquals(1, result.asMap().size());
        assertContainsError(result, "Validate bot essential config", "Invalid or empty token set in 'bot.token'");

        testConfig.set("bot.token", "");
        result = linter.run(testConfig);
        assertEquals(1, result.asMap().size());
        assertContainsError(result, "Validate bot essential config", "Invalid or empty token set in 'bot.token'");


        testConfig = newBasicValidConfig();

        testConfig.set("bot.server", null);
        result = linter.run(testConfig);
        assertEquals(1, result.asMap().size());
        assertContainsError(result, "Validate bot essential config", "'null' is not a valid Discord ID for field bot.server");

        testConfig.set("bot.server", "");
        result = linter.run(testConfig);
        assertEquals(1, result.asMap().size());
        assertContainsError(result, "Validate bot essential config", "'' is not a valid Discord ID for field bot.server");

        testConfig.set("bot.server", "000000000000000000");
        result = linter.run(testConfig);
        assertEquals(1, result.asMap().size());
        assertContainsError(result, "Validate bot essential config", "The bot.server value has not been modified from the default. Please set it in your config.yml.");

        testConfig.set("bot.server", "abc");
        result = linter.run(testConfig);
        assertEquals(1, result.asMap().size());
        assertContainsError(result, "Validate bot essential config", "'abc' is not a valid Discord ID for field bot.server");
    }

    @Test
    void testValidateGroups() {
        ConfigLinter linter = new ConfigLinter();
        Configuration testConfig = newBasicValidConfig();
        LintResult result;

        testConfig.set("groups", new MemoryConfiguration());
        result = linter.run(testConfig);
        assertEquals(1, result.asMap().size());
        assertContainsError(result, "Validate groups", "At least one group configuration is required in 'groups:'");

        testConfig.set("groups", null);
        result = linter.run(testConfig);
        assertEquals(1, result.asMap().size());
        assertContainsError(result, "Validate groups", "At least one group configuration is required in 'groups:'");

        List<String> adminList = new ArrayList<>();
        adminList.add("asdasd");
        adminList.add("");
        adminList.add("1230293129301239");
        testConfig.set("groups.admin", adminList);
        result = linter.run(testConfig);
        assertEquals(1, result.asMap().size());
        assertEquals(2, result.asMap().get("Validate groups").size());
        assertContainsError(result, "Validate groups", "'asdasd' is not a valid Discord ID for field 'groups.admin'");
        assertContainsError(result, "Validate groups", "'' is not a valid Discord ID for field 'groups.admin'");
    }

    @Test
    void testValidateDatabaseConfig() {
        ConfigLinter linter = new ConfigLinter();
        Configuration testConfig = newBasicValidConfig();
        LintResult result;

        testConfig.set("database.type", "some other db");
        result = linter.run(testConfig);
        assertEquals(1, result.asMap().size());
        assertContainsError(result, "Validate database config", "Invalid database type 'some other db', must be mysql or sqlite");

        testConfig.set("database.type", "MySQL");
        result = linter.run(testConfig);
        assertEquals(1, result.asMap().size());
        assertContainsError(result, "Validate database config", "You must configure `database.mysql` when using database type `mysql`.");

        testConfig.set("database.mysql.dbhost", "some host");
        assertEmpty(linter.run(testConfig));

        testConfig.set("database.mysql.disableSSL", true);
        assertEmpty(linter.run(testConfig));
    }

    @Test
    void testValidateWhitelistConfig() {
        ConfigLinter linter = new ConfigLinter();
        Configuration testConfig = newBasicValidConfig();
        LintResult result;

        testConfig.set("manageWhitelist", true);
        testConfig.set("whitelistRoles", null);
        result = linter.run(testConfig);
        assertEquals(1, result.asMap().size());
        assertContainsError(result, "Validate whitelist config", "whitelistRoles must not be empty when manageWhitelist is enabled");

        List<String> roles = new ArrayList<>();
        roles.add("");
        roles.add("asdasd");
        roles.add("1230293129301239");
        testConfig.set("whitelistRoles", roles);
        result = linter.run(testConfig);
        assertEquals(1, result.asMap().size());
        assertEquals(2, result.asMap().get("Validate whitelist config").size());
        assertContainsError(result, "Validate whitelist config", "'' is not a valid Discord ID for field 'whitelistRoles'");
        assertContainsError(result, "Validate whitelist config", "'asdasd' is not a valid Discord ID for field 'whitelistRoles'");
    }

    @Test
    void testValidateLinkedRoleConfig() {
        ConfigLinter linter = new ConfigLinter();
        Configuration testConfig = newBasicValidConfig();
        LintResult result;

        testConfig.set("giveLinkedRole", true);
        testConfig.set("linkedRole", null);
        result = linter.run(testConfig);
        assertEquals(1, result.asMap().size());
        assertContainsError(result, "Validate linked role config", "'null' is not a valid Discord ID for field 'linkedRole'");

        testConfig.set("linkedRole", "");
        result = linter.run(testConfig);
        assertEquals(1, result.asMap().size());
        assertContainsError(result, "Validate linked role config", "'' is not a valid Discord ID for field 'linkedRole'");

        testConfig.set("linkedRole", "123");
        result = linter.run(testConfig);
        assertEquals(1, result.asMap().size());
        assertContainsError(result, "Validate linked role config", "'123' is not a valid Discord ID for field 'linkedRole'");

        testConfig.set("linkedRole", "1239991239123908");
        assertEmpty(linter.run(testConfig));
    }

    @Test
    void testValidateBotActivityConfig() {
        ConfigLinter linter = new ConfigLinter();
        Configuration testConfig = newBasicValidConfig();
        LintResult result;

        testConfig.set("botActivity.enable", true);
        testConfig.set("botActivity.status", null);
        result = linter.run(testConfig);
        assertEquals(1, result.asMap().size());
        assertContainsError(result, "Validate bot activity config", "botActivity.status must not be empty when botActivity is enabled");

        testConfig.set("botActivity.status", "");
        result = linter.run(testConfig);
        assertEquals(1, result.asMap().size());
        assertContainsError(result, "Validate bot activity config", "botActivity.status must not be empty when botActivity is enabled");

        testConfig.set("botActivity.status", "%without_papi%");
        result = linter.run(testConfig);
        assertEquals(1, result.asMap().size());
        assertContainsError(result, "Validate bot activity config", "botActivity.status contains a PlaceholderAPI placeholder, but the PlaceholderAPI integration is disabled. Please set integrations.plugins.PlaceholderAPI to true.");

        // A random `%` passes
        testConfig.set("botActivity.status", "% dajsidkads");
        assertEmpty(linter.run(testConfig));

        testConfig.set("botActivity.status", "%with_papi%");
        testConfig.set("integrations.plugins.PlaceholderAPI", true);
        assertEmpty(linter.run(testConfig));
    }

    @Test
    void testValidateDiscordRename() {
        ConfigLinter linter = new ConfigLinter();
        Configuration testConfig = newBasicValidConfig();
        LintResult result;

        testConfig.set("discordRename.template", "");
        assertEmpty(linter.run(testConfig));

        testConfig.set("discordRename.template", null);
        assertEmpty(linter.run(testConfig));

        testConfig.set("discordRename.template", "%without_papi%");
        result = linter.run(testConfig);
        assertEquals(1, result.asMap().size());
        assertContainsError(result, "Validate discordRename", "discordRename.template contains a PlaceholderAPI placeholder, but the PlaceholderAPI integration is disabled. Please set integrations.plugins.PlaceholderAPI to true.");

        // A random `%` passes
        testConfig.set("discordRename.template", "% dajsidkads");
        assertEmpty(linter.run(testConfig));

        testConfig.set("discordRename.template", "%with_papi%");
        testConfig.set("integrations.plugins.PlaceholderAPI", true);
        assertEmpty(linter.run(testConfig));
    }

    @Test
    void testValidateEmbedConfig() {
        ConfigLinter linter = new ConfigLinter();
        Configuration testConfig = newBasicValidConfig();
        LintResult result;

        // The default config works for enabling embed
        testConfig.set("embed.useEmbed", true);
        assertEmpty(linter.run(testConfig));

        testConfig.set("embed.title", null);
        result = linter.run(testConfig);
        assertEquals(1, result.asMap().size());
        assertContainsError(result, "Validate embed config", "embed.title must not be empty when embed.useEmbed is enabled");

        testConfig.set("embed.title", "");
        result = linter.run(testConfig);
        assertEquals(1, result.asMap().size());
        assertContainsError(result, "Validate embed config", "embed.title must not be empty when embed.useEmbed is enabled");

        testConfig.set("embed.title", "some title");
        testConfig.set("embed.colors", null);
        result = linter.run(testConfig);
        assertEquals(1, result.asMap().size());
        assertContainsError(result, "Validate embed config", "embed.colors must not be empty when embed.useEmbed is enabled");

        testConfig.set("embed.colors.INFO", "magenta");
        assertEmpty(linter.run(testConfig));

        testConfig.set("embed.colors.INFO", "#FFF");
        assertEmpty(linter.run(testConfig));

        testConfig.set("embed.colors.INFO", "fuchsia");
        result = linter.run(testConfig);
        assertEquals(1, result.asMap().size());
        assertContainsError(result, "Validate embed config", "Color 'fuchsia' is not valid in field 'embed.colors.INFO'");
    }

    private void assertContainsError(LintResult result, String ruleName, String error) {
        List<String> ruleResult = result.asMap().get(ruleName);
        if (ruleResult != null) for (String err : ruleResult) {
            if (err.equals(error)) return;
        }

        fail(String.format("LintResult does not contain error '%s': %s", error, result));
    }

    private void assertEmpty(LintResult r) {
        assertEquals(Collections.EMPTY_MAP, r.asMap());
    }

    private Configuration newBasicValidConfig() {
        final Configuration config;
        try {
            config =ConfigReader.getConfigFromResource("config.yml");
        } catch (Exception e) {
            throw new RuntimeException("Unable to read resource config.yml for creating test config");
        }

        // Set basic fields
        config.set("bot.token", "some invalid token that passes");
        config.set("bot.server", "100000000000000000");

        return config;
    }
}
