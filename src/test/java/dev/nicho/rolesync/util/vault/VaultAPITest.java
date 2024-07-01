package dev.nicho.rolesync.util.vault;

import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.*;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class VaultAPITest {
    static final Server server = Mockito.mock(Server.class);
    static final OfflinePlayer player = Mockito.mock(OfflinePlayer.class);

    @BeforeAll
    static void setUpBukkit() {
        Mockito.when(server.getLogger()).thenReturn(Logger.getLogger("tests"));
        Bukkit.setServer(server);
    }

    @BeforeEach
    void setUp() {
        Mockito.when(server.getOfflinePlayer(Mockito.any(UUID.class)))
                .thenReturn(player);
    }

    @AfterEach
    void reset() {
        Mockito.reset(server);
    }

    @Test
    void testSetGroups() {
        List<String> managedGroups = Arrays.asList(
                "test-group", "test-group-2"
        );

        Permission permProvider = Mockito.mock(Permission.class);
        VaultAPI vault = Mockito.spy(new VaultAPI(null, permProvider));
        Mockito.doReturn(managedGroups).when(vault).getManagedGroups();

        vault.setGroups(UUID.randomUUID().toString(), Collections.singletonList("test-group"));

        // Assert all groups were initially removed
        for (String group : managedGroups) {
            Mockito.verify(permProvider, Mockito.times(1))
                    .playerRemoveGroup(null, player, group);
        }

        // Assert only the required one was added
        Mockito.verify(permProvider, Mockito.times(1))
                .playerAddGroup(null, player, "test-group");
    }

    @Test
    void testGetPermPluginName() {
        Permission permProvider = Mockito.mock(Permission.class);
        Mockito.when(permProvider.getName()).thenReturn("TestPermPlugin");

        VaultAPI vault = new VaultAPI(null, permProvider);

        assertEquals(vault.getPermPluginName(), "TestPermPlugin", "Incorrect permission plugin name returned");
    }
}