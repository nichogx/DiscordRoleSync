package dev.nicho.rolesync.bot.discord;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import org.bukkit.configuration.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks if the bot has the Discord permissions needed by the features enabled in the config.
 * Role sync, the whitelist and the commands don't need any permission, so only the optional
 * features are checked.
 */
public final class BotPermissionChecker {

    private BotPermissionChecker() {
    }

    /**
     * Returns a warning for each enabled feature that won't work with the bot's current permissions.
     *
     * @param config The plugin config
     * @param guild  The configured Discord server
     * @return The warnings to show to the admin, empty if everything is fine
     */
    public static List<String> check(Configuration config, Guild guild) {
        List<String> warnings = new ArrayList<>();
        Member self = guild.getSelfMember();

        if (config.getBoolean("giveLinkedRole")) {
            Role linkedRole = guild.getRoleById(config.getString("linkedRole", ""));
            if (!self.hasPermission(Permission.MANAGE_ROLES)) {
                warnings.add("Bot does not have the Manage Roles permission, so it can't give or remove the linked role (giveLinkedRole).");
            } else if (linkedRole == null) {
                warnings.add("The linked role (linkedRole) does not exist in the Discord server, so it can't be given to linked users.");
            } else if (!self.canInteract(linkedRole)) {
                warnings.add("The linked role (linkedRole) is above the bot's highest role, so the bot can't give or remove it. Move the bot's role above it in the Discord server settings.");
            }
        }

        if (!config.getString("discordRename.template", "").isEmpty()
                && !self.hasPermission(Permission.NICKNAME_MANAGE)) {
            warnings.add("Bot does not have the Manage Nicknames permission, so it can't rename linked users (discordRename.template).");
        }

        return warnings;
    }
}
