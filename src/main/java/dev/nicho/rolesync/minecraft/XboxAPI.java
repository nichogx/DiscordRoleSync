package dev.nicho.rolesync.minecraft;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static dev.nicho.rolesync.minecraft.UserSearch.encodePathSegment;
import static dev.nicho.rolesync.minecraft.UserSearch.uuidAddDashes;

public class XboxAPI {

    /**
     * Searches for a Bedrock user through GeyserMC's API.
     *
     * @param name the Geyser username, including the prefix added by Geyser
     * @return the user, or null if not found
     * @throws IOException if an error occurs while contacting the API
     */
    @SuppressFBWarnings(
            value = "URLCONNECTION_SSRF_FD",
            justification = "The host is hardcoded, and the name is percent-encoded so it can only change the last path segment"
    )
    public UserSearchResult searchName(String name) throws IOException {
        String gamertag = name
                .replace("_", " ") // Geyser replaces xbox spaces with underscores
                .replace(".", ""); // remove the dot added by Geyser
        if (gamertag.isBlank()) {
            return null;
        }

        URL reqUrl = URI.create("https://api.geysermc.org/v2/xbox/xuid/" + encodePathSegment(gamertag)).toURL();

        HttpURLConnection c = (HttpURLConnection) reqUrl.openConnection();
        c.setRequestMethod("GET");

        c.connect();
        int responseCode = c.getResponseCode();
        if (isNotFound(c)) {
            return null;
        }

        if (responseCode != 200) {
            throw new IOException(String.format("Error contacting GeyserMC API (HTTP %s): %s",
                    responseCode, c.getResponseMessage()
            ));
        }

        JSONObject body = getJSONBody(c);

        // The Geyser UUID is the hex XUID, with all leading zeros
        String uuid = String.format("%032X", body.getLong("xuid"));

        return new UserSearchResult(
                name,
                uuidAddDashes(uuid)
        );
    }

    private boolean isNotFound(HttpURLConnection c) throws IOException {
        if (c.getResponseCode() == 404) return true;

        // Geyser's API returns 503 when it cannot find the user...
        return c.getResponseCode() == 503
                && getJSONBody(c).getString("message").contains("Unable to find user in our cache");
    }

    private JSONObject getJSONBody(HttpURLConnection c) throws IOException {
        try (InputStream response = getErrorOrInputStream(c)) {
            return new JSONObject(new String(response.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private InputStream getErrorOrInputStream(HttpURLConnection c) throws IOException {
        if (c.getResponseCode() < HttpURLConnection.HTTP_BAD_REQUEST) {
            return c.getInputStream();
        }

        return c.getErrorStream();
    }
}
