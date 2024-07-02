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

    public UserSearchResult searchName(String name) throws IOException {
        URL reqUrl;
        try {
            reqUrl = new URI("https://api.geysermc.org/v2/xbox/xuid/" + name).toURL();
        } catch (URISyntaxException e) {
            throw new IOException("Incorrectly formatted name");
        }

        HttpURLConnection c = (HttpURLConnection) reqUrl.openConnection();
        c.setRequestMethod("GET");

        InputStream response = c.getInputStream();

        c.connect();
        int responseCode = c.getResponseCode();
        if (responseCode == 404) {
            return null;
        }

        if (responseCode != 200) {
            throw new IOException(String.format("Error contacting GeyserMC API (HTTP %s): %s",
                    responseCode, c.getResponseMessage()
            ));
        }

        Scanner scanner = new Scanner(response);

        JSONObject body = new JSONObject(scanner.useDelimiter("\\A").next());
        scanner.close();
        response.close();

        // The Geyser UUID is the hex XUID, with all leading zeros
        String uuid = String.format("%032X", body.getLong("xuid"));

        return new UserSearchResult(
                "." + name,
                uuidAddDashes(uuid)
        );
    }
}
