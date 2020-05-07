package dev.nicho.rolesync.permissionapis;

import dev.nicho.rolesync.util.MojangAPI;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class LuckPermsAPI extends PermissionsAPI {

    private LuckPerms lp = null;

    public LuckPermsAPI() throws PermPluginNotFoundException {
        RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);

        if (provider == null) {
            throw new PermPluginNotFoundException("LuckPerms is not loaded.");
        } else {
            lp = provider.getProvider();
        }
    }

    @Override
    public void setPermissions(String uuid, List<String> permissions, List<String> managed) {
        CompletableFuture<User> userFuture = lp.getUserManager().loadUser(UUID.fromString(uuid));
        userFuture.thenAcceptAsync(user -> {
            user.getNodes().forEach(node -> {
                if (managed.contains(node.getKey())) {
                    user.data().remove(node);
                }
            });

            permissions.forEach(perm -> {
                Node node = Node.builder(perm).value(true).build();
                System.out.println("node for " + perm + " being added"); // TODO remove
                user.data().add(node);
            });

            lp.getUserManager().saveUser(user);
        });
    }
}
