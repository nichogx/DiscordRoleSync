package dev.nicho.rolesync.util.vault;

import dev.nicho.rolesync.RoleSync;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VaultAPITest {
    static final Server server = Mockito.mock(Server.class);
    static final OfflinePlayer player = Mockito.mock(OfflinePlayer.class);
    static final String PLAYER_UUID = "069a79f4-44e9-4726-a5be-fca90e38aaf5";

    private final Set<String> playerGroups = new HashSet<>();
    private VaultAPI vault;

    @BeforeAll
    static void setUpBukkit() {
        Mockito.when(server.getLogger()).thenReturn(Logger.getLogger("tests"));
        Bukkit.setServer(server);
    }

    @BeforeEach
    void setUp() throws InvalidConfigurationException {
        Mockito.when(server.getOfflinePlayer(UUID.fromString(PLAYER_UUID)))
                .thenReturn(player);

        // Keep the player's groups in memory, so tests can check the end result
        Permission permProvider = Mockito.mock(Permission.class);
        Mockito.when(permProvider.playerInGroup(Mockito.isNull(), Mockito.eq(player), Mockito.anyString()))
                .thenAnswer(inv -> playerGroups.contains(inv.<String>getArgument(2)));
        Mockito.when(permProvider.playerAddGroup(Mockito.isNull(), Mockito.eq(player), Mockito.anyString()))
                .thenAnswer(inv -> playerGroups.add(inv.getArgument(2)));
        Mockito.when(permProvider.playerRemoveGroup(Mockito.isNull(), Mockito.eq(player), Mockito.anyString()))
                .thenAnswer(inv -> playerGroups.remove(inv.<String>getArgument(2)));

        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(String.join("\n",
                "groups:",
                "  admin:",
                "    - '000000000000000000'",
                "  vip:",
                "    - '111111111111111111'",
                "    - '222222222222222222'",
                "  unused: []"
        ));

        RoleSync plugin = Mockito.mock(RoleSync.class);
        Mockito.when(plugin.getConfig()).thenReturn(config);

        vault = new VaultAPI(plugin, permProvider);
    }

    @AfterEach
    void reset() {
        Mockito.reset(server);
    }

    @Test
    void addsListedGroupsAndRemovesOtherManagedGroups() {
        playerGroups.add("admin");

        vault.setGroups(PLAYER_UUID, List.of("vip"));

        assertEquals(Set.of("vip"), playerGroups);
    }

    @Test
    void keepsGroupsNotInConfig() {
        playerGroups.addAll(List.of("builder", "admin"));

        vault.setGroups(PLAYER_UUID, Collections.emptyList());

        assertEquals(Set.of("builder"), playerGroups);
    }

    @Test
    void nullRemovesAllManagedGroups() {
        playerGroups.addAll(List.of("admin", "vip", "builder"));

        vault.setGroups(PLAYER_UUID, null);

        assertEquals(Set.of("builder"), playerGroups);
    }

    @Test
    void groupsWithNoRolesAreNotManaged() {
        playerGroups.add("unused");

        vault.setGroups(PLAYER_UUID, Collections.emptyList());

        assertEquals(Set.of("unused"), playerGroups);
    }
}
