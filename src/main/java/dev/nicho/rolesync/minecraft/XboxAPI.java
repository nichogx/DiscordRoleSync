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
        if (responseCode == 404) {
            return null;
        }

        if (responseCode != 200) {
            throw new IOException(String.format("Error contacting GeyserMC API (HTTP %s): %s",
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

        // The Geyser UUID is the hex XUID, with all leading zeros
        String uuid = String.format("%032X", body.getLong("xuid"));

        return new UserSearchResult(
                name,
                uuidAddDashes(uuid)
        );
    }
}
