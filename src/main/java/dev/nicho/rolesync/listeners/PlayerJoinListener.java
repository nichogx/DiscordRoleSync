package dev.nicho.rolesync.listeners;

import dev.nicho.rolesync.db.DatabaseHandler;
import dev.nicho.rolesync.db.DatabaseHandler.LinkedUserInfo;
import dev.nicho.rolesync.util.plugin_meta.PluginVersion;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.sql.SQLException;

public class PlayerJoinListener implements Listener {

    private final DatabaseHandler db;
    private final YamlConfiguration lang;
    private final JavaPlugin plugin;

    private final String chatPrefix;

    public PlayerJoinListener(DatabaseHandler db, YamlConfiguration lang, JavaPlugin plugin) {
        this.db = db;
        this.lang = lang;
        this.plugin = plugin;

        this.chatPrefix = plugin.getConfig().getString("chatPrefix.text", "[DRS]") + " ";
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String uuid = player.getUniqueId().toString();
        String username = player.getName();

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                LinkedUserInfo usrInfo = db.getLinkedUserInfo(uuid);
                if (usrInfo != null && !username.equals(usrInfo.username)) {
                    plugin.getLogger().info(
                            String.format("User with UUID %s has changed names from '%s' to '%s', updating in the database...", uuid, usrInfo.username, username)
                    );
                    db.updateUsername(uuid, username);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Error while checking/update newly joined user's username.\n" +
                        e.getMessage());
            }
        });

        if (player.hasPermission("discordrolesync.notifyupdates")) {
            String installedVersion = plugin.getDescription().getVersion();
            PluginVersion.VersionType versionType = PluginVersion.getVersionType(installedVersion);

            if (versionType != PluginVersion.VersionType.RELEASE) {
                // Server is running an unsupported version. Alert the user.
                String message = ChatColor.BLUE + this.chatPrefix + ChatColor.RED
                        + lang.getString("nonReleaseVersion.running." + versionType.toString());

                event.getPlayer().sendMessage(message);
                return;
            }

            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                // Server is running a release version. Check for updates and send if available.
                PluginVersion v = new PluginVersion();
                boolean isOldVersion;
                String latestVersion;
                try {
                    isOldVersion = v.isOldRelease(installedVersion);
                    latestVersion = v.getLatestVersion();
                } catch (IOException e) {
                    plugin.getLogger().warning("Error while checking for latest version." + e.getMessage());
                    return;
                }

                if (isOldVersion) {
                    String message = ChatColor.BLUE + this.chatPrefix + ChatColor.AQUA
                            + lang.getString("notLatestVersion") + "\n" +
                            ChatColor.BLUE + this.chatPrefix + ChatColor.AQUA + lang.getString("current") + " "
                            + ChatColor.RED + installedVersion + ChatColor.AQUA + "\n" +
                            ChatColor.BLUE + this.chatPrefix + ChatColor.AQUA + lang.getString("latest") + " "
                            + ChatColor.GREEN + latestVersion;

                    event.getPlayer().sendMessage(message);
                }
            });
        }
    }
}
