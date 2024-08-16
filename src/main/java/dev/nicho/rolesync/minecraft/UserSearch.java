package dev.nicho.rolesync.minecraft;

import dev.nicho.rolesync.RoleSync;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class UserSearch {

    private final RoleSync plugin;

    private final MojangAPI mojangAPI;
    private final XboxAPI xboxAPI;

    /**
     * Creates MojangAPI with an alternative server.
     * If the URL is invalid or empty, the default server will be used.
     *
     * @param plugin a reference to the RoleSync so we can extract configs
     */
    public UserSearch(RoleSync plugin) {
        this.plugin = plugin;
        this.mojangAPI = new MojangAPI(plugin);
        this.xboxAPI = new XboxAPI();
    }

    /**
     *
     * @param name the Minecraft username
     * @return true if the user should be treated as a Geyser user, false otherwise
     */
    public boolean isGeyser(String name) {
        return name.startsWith(".") &&
                plugin.getConfig().getBoolean("experimental.geyser.enableGeyserSupport", false);
    }

    /**
     * Converts a username to a UUID - online or offline, depending on server mode
     *
     * @param name          the username
     * @param manualOffline if this request should be treated as offline when in manual mode
     * @return a MojangSearchResult with the name and the uuid. All properties will be null if not found.
     * The name will have the correct capitalization if running on online mode
     * @throws IOException if an error occurs while looking for user
     */
    public @Nullable UserSearchResult nameToUUID(String name, boolean manualOffline) throws IOException {
        // Experimental! Geyser support
        if (isGeyser(name)) {
            return xboxAPI.searchName(name);
        }

        UUIDMode mode = UUIDMode.fromCaseInsensitive(plugin.getConfig().getString("userUUIDMode"));
        boolean useOnline = false;
        if (mode == UUIDMode.DEFAULT) {
            useOnline = Bukkit.getOnlineMode();
        } else if (mode == UUIDMode.ONLINE) {
            useOnline = true;
        } else if (mode == UUIDMode.MANUAL) {
            useOnline = !manualOffline;
        }

        if (useOnline || mode == UUIDMode.FALLBACK) {
            UserSearchResult onlineResult = mojangAPI.searchName(name);

            // For fallback mode, if something was found here, return it. Otherwise,
            // fallback to the offline result below.
            if (useOnline || onlineResult != null) return onlineResult;
        }

        // Offline result
        return new UserSearchResult(
                name,
                UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)).toString()
        );
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
