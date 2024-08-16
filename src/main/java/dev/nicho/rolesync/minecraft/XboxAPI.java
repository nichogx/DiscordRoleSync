package dev.nicho.rolesync.minecraft;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Scanner;

import static dev.nicho.rolesync.minecraft.UserSearch.uuidAddDashes;

public class XboxAPI {

    public UserSearchResult searchName(String name) throws IOException {
        URL reqUrl;
        try {
            String xboxName = name
                    .replace("_", "%20") // Geyser replaces xbox spaces with underscores
                    .replace(".", ""); // remove the dot added by Geyser

            reqUrl = new URI("https://api.geysermc.org/v2/xbox/xuid/" + xboxName).toURL();
        } catch (URISyntaxException e) {
            throw new IOException("Incorrectly formatted name");
        }

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
        try (
                InputStream response = getErrorOrInputStream(c);
                Scanner scanner = new Scanner(response)
        ) {
            return new JSONObject(scanner.useDelimiter("\\A").next());
        }
    }

    private InputStream getErrorOrInputStream(HttpURLConnection c) throws IOException {
        if (c.getResponseCode() < HttpURLConnection.HTTP_BAD_REQUEST) {
            return c.getInputStream();
        }

        return c.getErrorStream();
    }
}
