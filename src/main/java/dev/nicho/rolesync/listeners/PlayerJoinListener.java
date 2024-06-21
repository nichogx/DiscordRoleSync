package dev.nicho.rolesync.listeners;

import dev.nicho.rolesync.db.DatabaseHandler;
import dev.nicho.rolesync.db.DatabaseHandler.LinkedUserInfo;
import dev.nicho.rolesync.util.SpigotPlugin;
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
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Player player = event.getPlayer();
                String uuid = player.getUniqueId().toString();

                LinkedUserInfo usrInfo = db.getLinkedUserInfo(uuid);

                String username = event.getPlayer().getName();
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

        if (event.getPlayer().hasPermission("discordrolesync.notifyupdates")) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                // check for updates and send if available
                String version = plugin.getDescription().getVersion();
                String latestVersion;
                try {
                    latestVersion = SpigotPlugin.getLatestVersion();
                } catch (IOException e) {
                    plugin.getLogger().warning("Error while checking for latest version." + e.getMessage());

                    return;
                }

                if (!latestVersion.equalsIgnoreCase(version)) {
                    String message = ChatColor.BLUE + this.chatPrefix + ChatColor.AQUA
                            + lang.getString("notLatestVersion") + "\n" +
                            ChatColor.BLUE + this.chatPrefix + ChatColor.AQUA + lang.getString("current") + " "
                            + ChatColor.RED + version + ChatColor.AQUA + "\n" +
                            ChatColor.BLUE + this.chatPrefix + ChatColor.AQUA + lang.getString("latest") + " "
                            + ChatColor.GREEN + latestVersion;

                    event.getPlayer().sendMessage(message);
                }
            });
        }
    }
}
