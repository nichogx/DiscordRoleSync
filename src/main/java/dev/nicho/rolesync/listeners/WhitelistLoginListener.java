package dev.nicho.rolesync.listeners;

import dev.nicho.rolesync.db.DatabaseHandler;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;

public class WhitelistLoginListener implements Listener {

    private final DatabaseHandler db;
    private final YamlConfiguration lang;
    private final JavaPlugin plugin;

    public WhitelistLoginListener(DatabaseHandler db, YamlConfiguration lang, JavaPlugin plugin) {
        this.db = db;
        this.lang = lang;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(AsyncPlayerPreLoginEvent event) {
        DatabaseHandler.LinkedUserInfo usrInfo;
        try {
            usrInfo = db.getLinkedUserInfo(event.getUniqueId().toString());
        } catch (SQLException e) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST, lang.getString("whitelistErrorKickMsg"));
            plugin.getLogger().severe("Error while checking if a user is whitelisted.\n" +
                    e.getMessage());

            return;
        }

        if (usrInfo == null) {
            // user not linked
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST, lang.getString("pleaseLink")
                    + " " + plugin.getConfig().getString("discordUrl"));
        } else if (plugin.getConfig().getBoolean("requireVerification") && !usrInfo.verified) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST, lang.getString("whitelistNotVerifiedKickMsg")
                    + " " + ChatColor.AQUA + usrInfo.code);
        } else if (!usrInfo.whitelisted) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST, lang.getString("notWhitelistedKickMsg"));
        }
    }
}
