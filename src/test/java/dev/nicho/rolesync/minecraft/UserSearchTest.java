package dev.nicho.rolesync.minecraft;

import dev.nicho.rolesync.RoleSync;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class UserSearchTest {

    @Test
    void testUUIDTypeForName() {
        FileConfiguration config = Mockito.mock(FileConfiguration.class);

        JavaPlugin plugin = Mockito.mock(RoleSync.class);
        Mockito.when(plugin.getConfig()).thenReturn(config);

        Logger logger = Mockito.mock(Logger.class);
        Mockito.when(plugin.getLogger()).thenReturn(logger);

        UserSearch us = new UserSearch(plugin);

        Mockito.when(config.getBoolean("experimental.geyser.enableGeyserSupport", false)).thenReturn(true);

        // Online mode
        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlineMode).thenReturn(true);
            Mockito.when(config.getBoolean("alwaysOnlineMode")).thenReturn(false);
            assertEquals(UUIDType.AUTHENTICATED, us.UUIDTypeForName("testuser"));

            Mockito.when(config.getBoolean("alwaysOnlineMode")).thenReturn(true);
            assertEquals(UUIDType.AUTHENTICATED, us.UUIDTypeForName("testuser"));

            assertEquals(UUIDType.BEDROCK, us.UUIDTypeForName(".testuser"));

            Mockito.when(config.getBoolean("experimental.geyser.enableGeyserSupport", false)).thenReturn(false);
            assertEquals(UUIDType.AUTHENTICATED, us.UUIDTypeForName(".testuser"));
        }

        // Offline mode
        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlineMode).thenReturn(false);
            Mockito.when(config.getBoolean("alwaysOnlineMode")).thenReturn(false);
            assertEquals(UUIDType.NOT_AUTHENTICATED, us.UUIDTypeForName("testuser"));

            Mockito.when(config.getBoolean("alwaysOnlineMode")).thenReturn(true);
            assertEquals(UUIDType.AUTHENTICATED, us.UUIDTypeForName("testuser"));
        }
    }

    @Test
    void testUUIDAddDashes() {
        String uuid = "00000000-0000-0000-0000-000000000000";
        assertEquals(uuid, UserSearch.uuidAddDashes(uuid.replace("-", "")));
    }
}