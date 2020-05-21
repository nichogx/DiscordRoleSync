package dev.nicho.rolesync.listeners;

import dev.nicho.rolesync.util.MojangAPI;
import dev.nicho.rolesync.util.Util;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.json.JSONArray;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Scanner;

public class PlayerJoinListener implements Listener {

    private final YamlConfiguration lang;
    private final JavaPlugin plugin;

    public PlayerJoinListener(YamlConfiguration lang, JavaPlugin plugin) {
        this.lang = lang;
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (event.getPlayer().hasPermission("discordrolesync.notifyupdates")) {
            // check for updates and send if available
            String version = plugin.getDescription().getVersion();
            String latestVersion;
            try {
                latestVersion = Util.getLatestVersion();
            } catch (IOException e) {
                e.printStackTrace();

                return;
            }

            if (!latestVersion.equalsIgnoreCase(version)) {
                String message = ChatColor.BLUE + "[DRS] " + ChatColor.AQUA + lang.getString("notLatestVersion") + "\n" +
                        ChatColor.BLUE + "[DRS] " + ChatColor.AQUA + lang.getString("current") + " " + ChatColor.RED + version + ChatColor.AQUA + "\n" +
                        ChatColor.BLUE + "[DRS] " + ChatColor.AQUA + lang.getString("latest") + " " + ChatColor.GREEN + latestVersion;

                event.getPlayer().sendMessage(message);
            }
        }
    }
}
