package dev.nicho.rolesync.util;

import org.bukkit.Bukkit;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.UUID;

public class MojangAPI {

    private URL url = null;

    /**
     * Creates MojangAPI with the default server
     */
    public MojangAPI() {
        setMojang();
    }

    /**
     * Creates MojangAPI with an alternative server.
     * If the URL is invalid, the default server will be used.
     *
     * @param alternateServer the url of the alternative server
     */
    public MojangAPI(String alternateServer) {
        try {
            this.url = new URL(alternateServer);
        } catch (MalformedURLException e) {
            e.printStackTrace();
            setMojang();
        }
    }

    /**
     * Sets the url to Mojang's url
     */
    private void setMojang() {
        try {
            this.url = new URL("https://api.mojang.com");
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Checks Mojang's servers for the username of an online UUID
     *
     * @param uuid the UUID of the user (with dashes)
     * @return a search result with the username and the UUID (with dashes) - all properties will be null if not found
     * @throws IOException if an error occurs while looking for the user
     */
    public MojangSearchResult onlineUuidToName(String uuid) throws IOException {
        URL reqUrl = new URL(this.url,"user/profiles/" + uuidRemoveDashes(uuid) + "/names");
        HttpURLConnection c = (HttpURLConnection) reqUrl.openConnection();
        c.setRequestMethod("GET");
        InputStream response = c.getInputStream();
        c.connect();
        if (c.getResponseCode() == 200) {
            Scanner scanner = new Scanner(response);

            JSONArray json = new JSONArray(scanner.useDelimiter("\\A").next());
            scanner.close();
            response.close();
            return new MojangSearchResult(
                    json.getJSONObject(json.length() - 1).getString("name"),
                    uuidAddDashes(uuid)
            );
        }


        return new MojangSearchResult();
    }

    /**
     * Converts a username to an UUID - online or offline, depending on server mode
     *
     * @param name the username
     * @return a MojangSearchResult with the name and the uuid. All properties will be null if not found.
     * The name will have the correct capitalization if running on online mode
     * @throws IOException if an error occurs while looking for user
     */
    public MojangSearchResult nameToUUID(String name) throws IOException {
        if (!Bukkit.getOnlineMode()) return new MojangSearchResult(
                name,
                UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)).toString()
        );


        URL reqUrl = new URL(this.url,"users/profiles/minecraft/" + name);
        HttpURLConnection c = (HttpURLConnection) reqUrl.openConnection();
        c.setRequestMethod("GET");
        InputStream response = c.getInputStream();

        c.connect();
        if (c.getResponseCode() == 200) {
            Scanner scanner = new Scanner(response);

            JSONObject body = new JSONObject(scanner.useDelimiter("\\A").next());
            scanner.close();
            response.close();
            return new MojangSearchResult(
                    body.getString("name"),
                    uuidAddDashes(body.getString("id"))
            );
        }

        return new MojangSearchResult();
    }

    /**
     * Adds dashes to a UUID
     *
     * @param uuid the UUID without dashes
     * @return the UUID with dashes
     */
    public static String uuidAddDashes(String uuid) {
        return UUID.fromString(uuid.replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5"
        )).toString();
    }

    /**
     * Removes dashes from a UUID
     *
     * @param uuid the UUID with dashes
     * @return the UUID without dashes
     */
    public static String uuidRemoveDashes(String uuid) {
        return uuid.replace("-", "");
    }

    /**
     * A search result for usernames and UUIDs
     */
    public static class MojangSearchResult {
        public final String name;
        public final String uuid;

        /**
         * Creates an empty search result
         */
        MojangSearchResult() {
            this(null, null);
        }

        /**
         * Creates a search result with a name and uuid
         * @param name the username
         * @param uuid the UUID
         */
        MojangSearchResult(String name, String uuid) {
            this.name = name;
            this.uuid = uuid;
        }
    }
}
