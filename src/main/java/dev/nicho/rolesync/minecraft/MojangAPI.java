package dev.nicho.rolesync.minecraft;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.bukkit.plugin.java.JavaPlugin;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;

import static dev.nicho.rolesync.minecraft.UserSearch.encodePathSegment;
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

    /**
     * Searches for a Java Edition user by name.
     *
     * @param name the username to search for
     * @return the user, or null if not found
     * @throws IOException if an error occurs while contacting the API
     */
    @SuppressFBWarnings(
            value = "URLCONNECTION_SSRF_FD",
            justification = "The host comes from the admin's config, and the name is percent-encoded so it can only change the last path segment"
    )
    public UserSearchResult searchName(String name) throws IOException {
        if (name.isEmpty()) {
            return null;
        }

        URL reqUrl = new URL(getMojangApiUrl(), "users/profiles/minecraft/" + encodePathSegment(name));
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
        try (InputStream response = c.getInputStream()) {
            body = new JSONObject(new String(response.readAllBytes(), StandardCharsets.UTF_8));
        }

        return new UserSearchResult(
                body.getString("name"),
                uuidAddDashes(body.getString("id"))
        );
    }
}
