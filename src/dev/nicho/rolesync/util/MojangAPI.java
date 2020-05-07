package dev.nicho.rolesync.util;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;
import java.util.UUID;

public class MojangAPI {

    public static String uuidToName(String uuid) throws IOException {
        URL url = new URL("https://api.mojang.com/user/profiles/" + uuidRemoveDashes(uuid) + "/names");
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("GET");
        InputStream response = c.getInputStream();
        c.connect();
        if (c.getResponseCode() == 200) {
            Scanner scanner = new Scanner(response);

            JSONArray json = new JSONArray(scanner.useDelimiter("\\A").next());
            return json.getJSONObject(json.length() - 1).getString("name");
        }


        return null;
    }

    public static String nameToUUID(String name) throws IOException {
        URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + name);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("GET");
        InputStream response = c.getInputStream();

        c.connect();
        if (c.getResponseCode() == 200) {
            Scanner scanner = new Scanner(response);

            JSONObject body = new JSONObject(scanner.useDelimiter("\\A").next());
            return uuidAddDashes(body.getString("id"));
        }

        return null;
    }

    public static String uuidAddDashes(String uuid) {
        return UUID.fromString(uuid.replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5"
        )).toString();
    }

    public static String uuidRemoveDashes(String uuid) {
        return uuid.replace("-", "");
    }
}