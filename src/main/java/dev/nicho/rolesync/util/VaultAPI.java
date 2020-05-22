package dev.nicho.rolesync.util;

import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

public class VaultAPI {

    private final Permission permProvider;
    private final List<String> managedGroups;

    /**
     * Creates a VaultAPI that will manage the groups in the list.
     *
     * @param managedGroups the list of groups to manage
     * @throws APIException if Vault is not loaded.
     */
    public VaultAPI(List<String> managedGroups) throws APIException {
        RegisteredServiceProvider<Permission> rsp = Bukkit.getServicesManager().getRegistration(Permission.class);

        if (rsp == null) {
            throw new APIException("Vault is not loaded.");
        } else {
            permProvider = rsp.getProvider();
        }

        this.managedGroups = managedGroups;
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

        for (String managedPerm : managedGroups) {
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
}
