package dev.nicho.rolesync.minecraft;

import dev.nicho.rolesync.RoleSync;
import org.bukkit.configuration.file.FileConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.URI;
import java.net.URLConnection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserSearchTest {

    @Test
    void testUUIDAddDashes() {
        String uuid = "00000000-0000-0000-0000-000000000000";
        assertEquals(uuid, UserSearch.uuidAddDashes(uuid.replace("-", "")));
    }

    @Test
    void testEncodePathSegment() {
        assertEquals("Notch_123", UserSearch.encodePathSegment("Notch_123"));
        assertEquals("some%20gamertag", UserSearch.encodePathSegment("some gamertag"));
        assertEquals("%2E%2E%2F%2E%2E%2Fevil", UserSearch.encodePathSegment("../../evil"));
        assertEquals("name%3Fx%3D1%23y", UserSearch.encodePathSegment("name?x=1#y"));
        assertEquals("n%C3%A4me", UserSearch.encodePathSegment("näme"));
    }

    @Test
    void testSetTimeouts() throws IOException {
        URLConnection c = URI.create("https://example.com").toURL().openConnection();
        assertEquals(0, c.getConnectTimeout());
        assertEquals(0, c.getReadTimeout());

        UserSearch.setTimeouts(c);
        assertTrue(c.getConnectTimeout() > 0);
        assertTrue(c.getReadTimeout() > 0);
    }

    @Test
    void testNameToUUIDBlankName() throws IOException {
        FileConfiguration config = Mockito.mock(FileConfiguration.class);
        Mockito.when(config.getString("userUUIDMode")).thenReturn("fallback");

        RoleSync plugin = Mockito.mock(RoleSync.class);
        Mockito.when(plugin.getConfig()).thenReturn(config);

        UserSearch userSearch = new UserSearch(plugin);
        assertNull(userSearch.nameToUUID("", false));
        assertNull(userSearch.nameToUUID("   ", false));
    }
}
