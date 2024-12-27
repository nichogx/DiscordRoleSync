package dev.nicho.rolesync.util.vault;

import dev.nicho.rolesync.RoleSync;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * VaultAPI is a class responsible for managing permissions and groups for players
 * using the Vault permissions API. It integrates with a specified Permission provider
 * to manage group assignments and permissions for players.
 */
public class VaultAPI {

    private final Permission permProvider;
    private final RoleSync plugin;

    /**
     * Creates a VaultAPI that will manage groups.
     *
     * @param plugin the JavaPlugin
     * @throws IllegalStateException if Vault is not loaded.
     */
    public VaultAPI(RoleSync plugin, Permission permProvider) throws IllegalStateException {
        this.permProvider = permProvider;
        this.plugin = plugin;
    }

    /**
     * Sets a user's groups to the ones in the list, removing any managed groups
     * that are not in the list.
     *
     * @param uuid   the UUID of the user to manage permissions
     * @param groups the list of groups to set or null to remove all managed
     */
    public void setGroups(String uuid, @Nullable List<String> groups) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(UUID.fromString(uuid));

        // Remove groups that shouldn't be there
        for (String managedPerm : getManagedGroups()) {
            if (groups == null || !groups.contains(managedPerm)) {
                removeGroup(player, managedPerm);
            }
        }

        // Add groups that should be there
        if (groups != null) groups.forEach(group -> addGroup(player, group));
    }

    /**
     * Adds a group to a player.
     */
    private void addGroup(OfflinePlayer player, String group) {
        plugin.debugLog("Adding group %s to player %s", group, player.getName());
        permProvider.playerAddGroup(null, player, group);
    }

    /**
     * Removes a group from a player.
     */
    private void removeGroup(OfflinePlayer player, String group) {
        if (permProvider.playerInGroup(null, player, group)) {
            plugin.debugLog("Removing group %s from player %s", group, player.getName());
            permProvider.playerRemoveGroup(null, player, group);
        }
    }

    /**
     * Checks if the specified player has the given permission.
     *
     * @param player the OfflinePlayer whose permission is to be checked
     * @param permission the permission node to check for the player
     * @return true if the player has the permission, false otherwise
     */
    public boolean hasPermission(OfflinePlayer player, String permission) {
        return permProvider.playerHas(null, player, permission);
    }

    /**
     * Gets the name of the permission plugin Vault detected
     *
     * @return the name of the plugin
     */
    public String getPermPluginName() {
        return this.permProvider.getName();
    }

    protected List<String> getManagedGroups() {
        ConfigurationSection perms = plugin.getConfig().getConfigurationSection("groups");
        List<String> managedGroups = new ArrayList<>();
        for (String perm : perms.getKeys(true)) {
            if (perms.getStringList(perm).isEmpty()) continue;
            managedGroups.add(perm);
        }

        return managedGroups;
    }
}
