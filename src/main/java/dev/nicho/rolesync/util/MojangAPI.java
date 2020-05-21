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

    public MojangAPI() {
        setMojang();
    }

    public MojangAPI(String alternateServer) {
        try {
            this.url = new URL(alternateServer);
        } catch (MalformedURLException e) {
            e.printStackTrace();
            setMojang();
        }
    }

    private void setMojang() {
        try {
            this.url = new URL("https://api.mojang.com");
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
    }

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

    public static String uuidAddDashes(String uuid) {
        return UUID.fromString(uuid.replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5"
        )).toString();
    }

    public static String uuidRemoveDashes(String uuid) {
        return uuid.replace("-", "");
    }

    public static class MojangSearchResult {
        public final String name;
        public final String uuid;

        MojangSearchResult() {
            this(null, null);
        }

        MojangSearchResult(String name, String uuid) {
            this.name = name;
            this.uuid = uuid;
        }
    }
}
