package dev.nicho.rolesync.minecraft;

/**
 * A search result for usernames and UUIDs
 */
public class UserSearchResult {
    public final String name;
    public final String uuid;

    /**
     * Creates a search result with a name and uuid
     *
     * @param name the username
     * @param uuid the UUID
     */
    UserSearchResult(String name, String uuid) {
        this.name = name;
        this.uuid = uuid;
    }
}
