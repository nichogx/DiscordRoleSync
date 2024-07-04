package dev.nicho.rolesync.minecraft;

import dev.nicho.rolesync.RoleSync;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MojangAPITest {

    @Test
    void testGetMojangApiUrl() {
        FileConfiguration config = Mockito.mock(FileConfiguration.class);

        JavaPlugin plugin = Mockito.mock(RoleSync.class);
        Mockito.when(plugin.getConfig()).thenReturn(config);

        Logger logger = Mockito.mock(Logger.class);
        Mockito.when(plugin.getLogger()).thenReturn(logger);

        MojangAPI mojang = new MojangAPI(plugin);

        Mockito.when(config.getString(Mockito.anyString())).thenReturn(null);
        assertEquals("https://api.mojang.com", mojang.getMojangApiUrl().toString());
        Mockito.verify(logger, Mockito.never()).warning(Mockito.anyString());
        Mockito.clearInvocations(logger);

        Mockito.when(config.getString(Mockito.anyString())).thenReturn("");
        assertEquals("https://api.mojang.com", mojang.getMojangApiUrl().toString());
        Mockito.verify(logger, Mockito.never()).warning(Mockito.anyString());
        Mockito.clearInvocations(logger);

        Mockito.when(config.getString(Mockito.anyString())).thenReturn("test");
        assertEquals("https://api.mojang.com", mojang.getMojangApiUrl().toString());
        Mockito.verify(logger, Mockito.times(1)).warning(
                "Unable to use server 'test'. URL cannot be parsed."
        );
        Mockito.clearInvocations(logger);

        Mockito.when(config.getString(Mockito.anyString())).thenReturn("test.com");
        assertEquals("https://api.mojang.com", mojang.getMojangApiUrl().toString());
        Mockito.verify(logger, Mockito.times(1)).warning(
                "Unable to use server 'test.com'. URL cannot be parsed."
        );
        Mockito.clearInvocations(logger);

        Mockito.when(config.getString(Mockito.anyString())).thenReturn("https://test.com");
        assertEquals("https://test.com", mojang.getMojangApiUrl().toString());
        Mockito.verify(logger, Mockito.never()).warning(Mockito.anyString());
        Mockito.clearInvocations(logger);
    }
}
