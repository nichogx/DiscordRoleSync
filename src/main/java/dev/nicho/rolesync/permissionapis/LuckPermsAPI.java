package dev.nicho.rolesync.permissionapis;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class LuckPermsAPI extends PermissionsAPI {

    private LuckPerms lp = null;

    public LuckPermsAPI(List<String> managedPerms) throws PermPluginNotFoundException {
        RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);

        if (provider == null) {
            throw new PermPluginNotFoundException("LuckPerms is not loaded.");
        } else {
            lp = provider.getProvider();
        }

        this.managedPerms = managedPerms;
    }

    @Override
    public void setPermissions(String uuid, @Nullable List<String> permissions) {
        CompletableFuture<User> userFuture = lp.getUserManager().loadUser(UUID.fromString(uuid));
        userFuture.thenAcceptAsync(user -> {
            user.getNodes().forEach(node -> {
                if (managedPerms.contains(node.getKey())) {
                    user.data().remove(node);
                }
            });

            if (permissions != null) permissions.forEach(perm -> {
                Node node = Node.builder(perm).value(true).build();
                user.data().add(node);
            });

            lp.getUserManager().saveUser(user);
        });
    }
}
