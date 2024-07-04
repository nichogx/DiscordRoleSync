package dev.nicho.rolesync.minecraft;

import org.bukkit.plugin.java.JavaPlugin;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.util.Scanner;

import static dev.nicho.rolesync.minecraft.UserSearch.uuidAddDashes;

public class MojangAPI {

    private final JavaPlugin plugin;

    public MojangAPI(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Gets the server URL to use, given an alternateServer config string.
     * Will fall back to Mojang's servers if alternateServer is empty or invalid.
     *
     * @return the URL object to use.
     */
    protected URL getMojangApiUrl() {
        String alternateServer = plugin.getConfig().getString("alternativeServer");

        String[] servers = {
                alternateServer,
                "https://api.mojang.com",
        };

        for (String server : servers) {
            if (server == null || server.isEmpty())
                continue;

            try {
                return new URI(server).toURL();
            } catch (MalformedURLException | URISyntaxException | IllegalArgumentException e) {
                plugin.getLogger().warning(
                        String.format("Unable to use server '%s'. URL cannot be parsed.", server)
                );
            }
        }

        // Since the last server is hardcoded, this should never happen.
        return null;
    }

    public UserSearchResult searchName(String name) throws IOException {
        URL reqUrl = new URL(getMojangApiUrl(), "users/profiles/minecraft/" + name);
        HttpURLConnection c = (HttpURLConnection) reqUrl.openConnection();
        c.setRequestMethod("GET");

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

        JSONObject body;
        try (
                InputStream response = c.getInputStream();
                Scanner scanner = new Scanner(response)
        ) {
            body = new JSONObject(scanner.useDelimiter("\\A").next());
        }

        return new UserSearchResult(
                body.getString("name"),
                uuidAddDashes(body.getString("id"))
        );
    }
}
