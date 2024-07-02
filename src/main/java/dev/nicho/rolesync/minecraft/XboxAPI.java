package dev.nicho.rolesync.minecraft;

import org.bukkit.plugin.java.JavaPlugin;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.util.Scanner;

import static dev.nicho.rolesync.minecraft.UserSearch.uuidAddDashes;

public class XboxAPI {

    private final JavaPlugin plugin;

    public XboxAPI(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public UserSearchResult searchName(String name) throws IOException {
        URL reqUrl;
        try {
            reqUrl = new URI("https://xbl.io/api/v2/search/" + name).toURL();
        } catch (URISyntaxException e) {
            throw new IOException("Incorrectly formatted name");
        }

        HttpURLConnection c = (HttpURLConnection) reqUrl.openConnection();
        c.setRequestMethod("GET");

        String xblApiKey = plugin.getConfig().getString("experimental.geyser.openXblApiKey");
        c.setRequestProperty("X-Authorization", xblApiKey);

        InputStream response = c.getInputStream();

        c.connect();
        int responseCode = c.getResponseCode();
        if (responseCode == 404) {
            return null;
        }

        if (responseCode != 200) {
            throw new IOException(String.format("Error contacting Mojang API (HTTP %s): %s",
                    responseCode, c.getResponseMessage()
            ));
        }

        Scanner scanner = new Scanner(response);

        JSONObject body = new JSONObject(scanner.useDelimiter("\\A").next());
        scanner.close();
        response.close();

        JSONArray arr = body.getJSONArray("people");
        if (arr.length() != 1) {
            return null;
        }

        JSONObject user = arr.getJSONObject(0);
        String xuid = user.getString("xuid");

        // The Geyser UUID is the hex XUID, with all leading zeros
        String uuid = String.format("%032X", Long.parseLong(xuid));

        return new UserSearchResult(
                "." + user.getString("gamertag"),
                uuidAddDashes(uuid)
        );
    }
}
