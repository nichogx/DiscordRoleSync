package dev.nicho.rolesync.minecraft;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class UserSearch {

    private final JavaPlugin plugin;

    private final MojangAPI mojangAPI;
    private final XboxAPI xboxAPI;

    /**
     * Creates MojangAPI with an alternative server.
     * If the URL is invalid or empty, the default server will be used.
     *
     * @param plugin a reference to the JavaPlugin so we can extract configs
     */
    public UserSearch(JavaPlugin plugin) {
        this.plugin = plugin;
        this.mojangAPI = new MojangAPI(plugin);
        this.xboxAPI = new XboxAPI();
    }

    /**
     * Defines which type of UUID we should use for this Minecraft name.
     *
     * @param name the Minecraft name
     * @return the UUIDType
     */
    public UUIDType UUIDTypeForName(String name) {
        if (name.startsWith(".") && plugin.getConfig().getBoolean("experimental.geyser.enableGeyserSupport", false)) {
            return UUIDType.BEDROCK;
        }

        if (!Bukkit.getOnlineMode() && !plugin.getConfig().getBoolean("alwaysOnlineMode"))
            return UUIDType.NOT_AUTHENTICATED;

        return UUIDType.AUTHENTICATED;
    }

    /**
     * Converts a username to a UUID - online or offline, depending on server mode
     *
     * @param name the username
     * @return a MojangSearchResult with the name and the uuid. All properties will be null if not found.
     * The name will have the correct capitalization if running on online mode
     * @throws IOException if an error occurs while looking for user
     */
    public @Nullable UserSearchResult nameToUUID(String name) throws IOException {
        UUIDType uuidType = UUIDTypeForName(name);

        if (uuidType == UUIDType.NOT_AUTHENTICATED) {
            return new UserSearchResult(
                    name,
                    UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)).toString()
            );
        }

        if (uuidType == UUIDType.BEDROCK) {
            // Experimental! Geyser support
            return xboxAPI.searchName(name);
        }

        // Authenticated
        return mojangAPI.searchName(name);
    }

    /**
     * Adds dashes to a UUID
     *
     * @param uuid the UUID without dashes
     * @return the UUID with dashes
     */
    static String uuidAddDashes(String uuid) {
        return UUID.fromString(uuid.replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5"
        )).toString();
    }
}
