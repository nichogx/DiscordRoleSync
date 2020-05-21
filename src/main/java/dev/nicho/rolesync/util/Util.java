package dev.nicho.rolesync.util;

import org.bukkit.ChatColor;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

public class Util {

    public static String getLatestVersion() throws IOException {
        URL reqUrl;
        reqUrl = new URL("https://api.spigotmc.org/legacy/update.php?resource=78829");

        HttpURLConnection c = (HttpURLConnection) reqUrl.openConnection();
        c.setRequestMethod("GET");
        InputStream response = c.getInputStream();
        c.connect();
        if (c.getResponseCode() == 200) {
            Scanner scanner = new Scanner(response);

            String latestVersion = scanner.next().trim();
            scanner.close();
            response.close();

            return latestVersion;
        }

        throw new IOException("Status was not 200.");
    }
}
