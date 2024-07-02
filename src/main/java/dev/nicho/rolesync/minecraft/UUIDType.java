package dev.nicho.rolesync.minecraft;

import java.util.UUID;

public enum UUIDType {
    UNKNOWN,

    // Authenticated UUIDs (online mode)
    AUTHENTICATED,

    // Unauthenticated UUIDs (offline mode)
    NOT_AUTHENTICATED,

    // Bedrock XUIDs (via Geyser)
    BEDROCK;

    /**
     * Gets a UUIDType given a Minecraft UUID.
     *
     * @param uuid the UUID.
     * @return the UUIDType for this Minecraft UUID.
     */
    public static UUIDType getTypeForUUID(UUID uuid) {
        switch (uuid.version()) {
            case 4:
                return AUTHENTICATED;
            case 3:
                return NOT_AUTHENTICATED;
            case 0:
                return BEDROCK;
            default:
                return UNKNOWN;
        }
    }

    /**
     * Gets a UUIDType given a Minecraft UUID as a String.
     *
     * @param uuid the UUID as a String.
     * @return the UUIDType for this Minecraft UUID.
     * @throws IllegalArgumentException if the String can't be parsed as a UUID
     */
    public static UUIDType getTypeForUUID(String uuid) throws IllegalArgumentException {
        return getTypeForUUID(UUID.fromString(uuid));
    }
}
