package dev.nicho.rolesync.bot.mojang;

import dev.nicho.rolesync.RoleSync;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class MojangAPITest {

    @Test
    void testGetServerUrl() {
        FileConfiguration config = Mockito.mock(FileConfiguration.class);
        Mockito.when(config.getString(Mockito.anyString())).thenReturn("");

        JavaPlugin plugin = Mockito.mock(RoleSync.class);
        Mockito.when(plugin.getConfig()).thenReturn(config);

        Logger logger = Mockito.mock(Logger.class);
        Mockito.when(plugin.getLogger()).thenReturn(logger);

        MojangAPI mojang = new MojangAPI(plugin);

        assertEquals("https://api.mojang.com", mojang.getServerUrl(null).toString());
        Mockito.verify(logger, Mockito.never()).warning(Mockito.anyString());
        Mockito.clearInvocations(logger);

        assertEquals("https://api.mojang.com", mojang.getServerUrl("").toString());
        Mockito.verify(logger, Mockito.never()).warning(Mockito.anyString());
        Mockito.clearInvocations(logger);

        assertEquals("https://api.mojang.com", mojang.getServerUrl("test").toString());
        Mockito.verify(logger, Mockito.times(1)).warning(
                "Unable to use server 'test'. URL cannot be parsed."
        );
        Mockito.clearInvocations(logger);

        assertEquals("https://api.mojang.com", mojang.getServerUrl("test.com").toString());
        Mockito.verify(logger, Mockito.times(1)).warning(
                "Unable to use server 'test.com'. URL cannot be parsed."
        );
        Mockito.clearInvocations(logger);

        assertEquals("https://test.com", mojang.getServerUrl("https://test.com").toString());
        Mockito.verify(logger, Mockito.never()).warning(Mockito.anyString());
        Mockito.clearInvocations(logger);
    }
}
