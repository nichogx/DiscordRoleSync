package dev.nicho.rolesync.listeners;

import dev.nicho.rolesync.RoleSync;
import dev.nicho.rolesync.db.DatabaseHandler;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;

public class WhitelistLoginListener implements Listener {

    private final RoleSync plugin;

    public WhitelistLoginListener(RoleSync plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(@NotNull AsyncPlayerPreLoginEvent event) {
        plugin.debugLog("Player %s (%s) is attempting to login.", event.getName(), event.getUniqueId());
        OfflinePlayer player = Bukkit.getOfflinePlayer(event.getUniqueId());
        if (player != null && plugin.getVault().hasPermission(player, "discordrolesync.bypasswhitelist")) {
            plugin.getLogger().info(String.format("Player %s (%s) has bypass whitelist permission, allowing login.", event.getName(), event.getUniqueId()));
            return;
        }

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
