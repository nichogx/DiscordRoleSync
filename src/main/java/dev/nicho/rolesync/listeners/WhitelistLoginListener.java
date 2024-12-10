package dev.nicho.rolesync.listeners;

import dev.nicho.rolesync.RoleSync;
import dev.nicho.rolesync.db.DatabaseHandler;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.sql.SQLException;

public class WhitelistLoginListener implements Listener {

    private final RoleSync plugin;

    public WhitelistLoginListener(RoleSync plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(AsyncPlayerPreLoginEvent event) {
        DatabaseHandler.LinkedUserInfo usrInfo;
        try {
            usrInfo = plugin.getDb().getLinkedUserInfo(event.getUniqueId().toString());
        } catch (SQLException e) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST, plugin.getLanguage().getString("whitelistErrorKickMsg"));
            plugin.getLogger().severe("Error while checking if a user is whitelisted.\n" +
                    e.getMessage());

            return;
        }

        if (usrInfo == null) {
            // user not linked
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST, plugin.getLanguage().getString("pleaseLink")
                    + " " + plugin.getConfig().getString("discordUrl"));
        } else if (plugin.getConfig().getBoolean("requireVerification") && !usrInfo.verified) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST, plugin.getLanguage().getString("verification.instructions")
                    .replace("$verify_command_name$", plugin.getConfig().getString("commandNames.verify", "verify"))
                    .replace("$verification_code$", ChatColor.AQUA + String.valueOf(usrInfo.code) + ChatColor.RESET));
        } else if (!usrInfo.whitelisted) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST, plugin.getLanguage().getString("notWhitelistedKickMsg"));
        }
    }
}
