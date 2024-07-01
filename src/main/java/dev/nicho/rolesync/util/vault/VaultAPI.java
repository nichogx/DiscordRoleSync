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
     * @param uuid the UUID of the user to manage permissions
     * @param groups the list of groups to set or null to remove all managed
     */
    public void setGroups(String uuid, @Nullable List<String> groups) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(UUID.fromString(uuid));

        for (String managedPerm : getManagedGroups()) {
            permProvider.playerRemoveGroup(null, player, managedPerm);
        }

        if (groups != null) groups.forEach(perm -> permProvider.playerAddGroup(null, player, perm));
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
