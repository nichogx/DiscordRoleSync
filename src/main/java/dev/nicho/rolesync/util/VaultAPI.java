package dev.nicho.rolesync.util;

import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

public class VaultAPI {

    private Permission permProvider = null;
    private List<String> managedGroups = null;

    public VaultAPI(List<String> managedGroups) throws APIException {
        RegisteredServiceProvider<Permission> rsp = Bukkit.getServicesManager().getRegistration(Permission.class);

        if (rsp == null) {
            throw new APIException("Vault is not loaded.");
        } else {
            permProvider = rsp.getProvider();
        }

        this.managedGroups = managedGroups;
    }

    public void setPermissions(String uuid, @Nullable List<String> permissions) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(UUID.fromString(uuid));

        for (String managedPerm : managedGroups) {
            permProvider.playerRemoveGroup(null, player, managedPerm);
        }

        if (permissions != null) permissions.forEach(perm -> {
            permProvider.playerAddGroup(null, player, perm);
        });
    }
}
