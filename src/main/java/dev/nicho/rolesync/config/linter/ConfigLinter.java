package dev.nicho.rolesync.config.linter;

import dev.nicho.rolesync.RoleSync;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ConfigLinter {

    private static final String discordIdRegex = "^\\d{16,}$";
    private static final String placeholderAPIRegex = "%\\w+%";
    private final RoleSync plugin;

    /**
     * Creates a config linter linked to the plugin.
     *
     * @param plugin a reference to the plugin
     */
    public ConfigLinter(RoleSync plugin) {
        this.plugin = plugin;
    }

    /**
     * Creates a config linter without linking to the plugin.
     * Intended for CI, some features such as warn logging are not available.
     */
    public ConfigLinter() {
        this(null);
    }

    /**
     * The list of lint rules that will run.
     */
    private final LintRule[] lintRules = {
            new LintRule("Requires config version",
                    c -> {
                        List<String> result = new ArrayList<>();

                        if (!c.isInt("configVersion")) {
                            result.add("configVersion is set incorrectly. Please do not modify this field.");
                        }

                        return result;
                    }),
            new LintRule("Validate bot essential config",
                    c -> {
                        List<String> result = new ArrayList<>();

                        String token = c.getString("bot.token");
                        if (token == null || token.isEmpty()) {
                            result.add("Invalid or empty token set in 'bot.token'");
                        }

                        String serverId = c.getString("bot.server");
                        if (!isDiscordId(serverId)) {
                            result.add(String.format("'%s' is not a valid Discord ID for field bot.server", serverId));
                            return result;
                        }

                        if (serverId.equals("000000000000000000")) {
                            result.add("The bot.server value has not been modified from the default. Please set it in your config.yml.");
                        }

                        return result;
                    }),
            new LintRule("Validate groups",
                    c -> {
                        List<String> result = new ArrayList<>();

                        ConfigurationSection groups = c.getConfigurationSection("groups");
                        if (groups == null) {
                            result.add("At least one group configuration is required in 'groups:'");
                            return result;
                        }

                        Set<String> keys = groups.getKeys(true);
                        if (keys.isEmpty()) {
                            result.add("At least one group configuration is required in 'groups:'");
                        }

                        for (String group : keys) {
                            List<String> ids = groups.getStringList(group);
                            for (String id : ids) {
                                if (!isDiscordId(id)) {
                                    result.add(String.format("'%s' is not a valid Discord ID for field 'groups.%s'", id, group));
                                }
                            }
                        }

                        return result;
                    }),
            new LintRule("Validate database config",
                    c -> {
                        List<String> result = new ArrayList<>();

                        String dbType = c.getString("database.type");
                        if (dbType.equalsIgnoreCase("mysql")) {
                            if (c.getString("database.mysql.dbhost").isEmpty()) {
                                result.add("You must configure `database.mysql` when using database type `mysql`.");
                            }

                            if (c.getBoolean("database.mysql.disableSSL")) {
                                warn("WARNING! Using MySQL with SSL disabled. This is very unsafe. Traffic to the database is not encrypted.");
                            }
                        } else if (!dbType.equalsIgnoreCase("sqlite")) {
                            result.add(String.format("Invalid database type '%s', must be mysql or sqlite", dbType));
                        }

                        return result;
                    }),
            new LintRule("Validate whitelist config",
                    c -> {
                        List<String> result = new ArrayList<>();
                        if (!c.getBoolean("manageWhitelist")) return result;

                        List<String> roles = c.getStringList("whitelistRoles");
                        if (roles == null || roles.isEmpty()) {
                            result.add("whitelistRoles must not be empty when manageWhitelist is enabled");
                            return result;
                        }

                        for (String role : roles) {
                            if (!isDiscordId(role)) {
                                result.add(String.format("'%s' is not a valid Discord ID for field 'whitelistRoles'", role));
                            }
                        }

                        return result;
                    }),
            new LintRule("Validate linked role config",
                    c -> {
                        List<String> result = new ArrayList<>();
                        if (!c.getBoolean("giveLinkedRole")) return result;

                        String role = c.getString("linkedRole");
                        if (role == null || role.isEmpty() || !role.matches(discordIdRegex)) {
                            result.add(String.format("'%s' is not a valid Discord ID for field 'linkedRole'", role));
                        }

                        return result;
                    }),
            new LintRule("Validate bot activity config",
                    c -> {
                        List<String> result = new ArrayList<>();
                        if (!c.getBoolean("botActivity.enable")) return result;

                        String pattern = c.getString("botActivity.status");
                        if (pattern == null || pattern.isEmpty()) {
                            result.add("botActivity.status must not be empty when botActivity is enabled");
                            return result;
                        }

                        if (pattern.matches(placeholderAPIRegex) && !c.getBoolean("integrations.plugins.PlaceholderAPI")) {
                            result.add("botActivity.status contains a PlaceholderAPI placeholder, but the PlaceholderAPI integration is disabled. Please set integrations.plugins.PlaceholderAPI to true.");
                        }

                        return result;
                    }),
            new LintRule("Validate discordRename",
                    c -> {
                        List<String> result = new ArrayList<>();

                        String pattern = c.getString("discordRename.template");
                        if (pattern == null || pattern.isEmpty()) {
                            return result;
                        }

                        if (pattern.matches(placeholderAPIRegex) && !c.getBoolean("integrations.plugins.PlaceholderAPI")) {
                            result.add("discordRename.template contains a PlaceholderAPI placeholder, but the PlaceholderAPI integration is disabled. Please set integrations.plugins.PlaceholderAPI to true.");
                        }

                        return result;
                    }),
            new LintRule("Validate embed config",
                    c -> {
                        List<String> result = new ArrayList<>();
                        if (!c.getBoolean("embed.useEmbed")) return result;

                        String pattern = c.getString("embed.title");
                        if (pattern == null || pattern.isEmpty()) {
                            result.add("embed.title must not be empty when embed.useEmbed is enabled");
                        }

                        ConfigurationSection colors = c.getConfigurationSection("embed.colors");
                        if (colors == null) {
                            result.add("embed.colors must not be empty when embed.useEmbed is enabled");
                            return result;
                        }

                        Set<String> keys = colors.getKeys(true);
                        if (keys.isEmpty()) {
                            result.add("embed.colors must not be empty when embed.useEmbed is enabled");
                        }

                        for (String key : keys) {
                            String color = colors.getString(key);
                            if (!color.matches("^#[0-9A-Fa-f]{1,6}$")) {
                                try {
                                    Class.forName("java.awt.Color").getField(color);
                                } catch (ClassNotFoundException | NoSuchFieldException e) {
                                    result.add(String.format("Color '%s' is not valid in field 'embed.colors.%s'", color, key));
                                }
                            }
                        }

                        return result;
                    }),
    };

    /**
     * Runs the linter on the given configuration.
     *
     * @param config the Bukkit configuration
     * @return A LintResult with the results from this run
     */
    public LintResult run(Configuration config) {
        LintResult results = new LintResult();

        for (LintRule rule : lintRules) {
            List<String> result = rule.run(config);
            if (result != null && !result.isEmpty()) {
                results.add(rule.getName(), result);
            }
        }

        return results;
    }

    /**
     * Logs a config warning, if the plugin is available.
     *
     * @param msg The message to log.
     */
    private void warn(String msg) {
        if (plugin == null) return;

        plugin.getLogger().warning(msg);
    }

    /**
     * @param id A potential Discord ID
     * @return true if the given string matches the Discord ID format, false if not
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean isDiscordId(String id) {
        return id != null &&
                !id.isEmpty() &&
                id.matches(discordIdRegex);
    }
}
