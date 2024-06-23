package dev.nicho.rolesync.util.plugin_meta;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class PluginVersionTest {

    @Test
    void testGetVersionType() {
        assertEquals(PluginVersion.VersionType.RELEASE, PluginVersion.getVersionType("1.1.1"));
        assertEquals(PluginVersion.VersionType.RELEASE, PluginVersion.getVersionType("12.12.12"));

        assertEquals(PluginVersion.VersionType.RELEASE_CANDIDATE, PluginVersion.getVersionType("1.3.0-rc.1"));
        assertEquals(PluginVersion.VersionType.RELEASE_CANDIDATE, PluginVersion.getVersionType("1.3.0-SNAPSHOT"));

        assertEquals(PluginVersion.VersionType.DEVELOPMENT_PREVIEW, PluginVersion.getVersionType("master-7a8a23d5"));

        assertEquals(PluginVersion.VersionType.UNKNOWN, PluginVersion.getVersionType("branch-7a8a23d5"));
        assertEquals(PluginVersion.VersionType.UNKNOWN, PluginVersion.getVersionType("develop"));
        assertEquals(PluginVersion.VersionType.UNKNOWN, PluginVersion.getVersionType("some random version"));
    }

    @Test
    void testParseSemanticVersion() {
        PluginVersion.SemanticVersion version = PluginVersion.parseSemanticVersion("1.3.0");
        assertEquals(1, version.major);
        assertEquals(3, version.minor);
        assertEquals(0, version.patch);
        assertNull(version.additional);

        version = PluginVersion.parseSemanticVersion("1.3.0-rc.1");
        assertEquals(1, version.major);
        assertEquals(3, version.minor);
        assertEquals(0, version.patch);
        assertEquals("rc.1", version.additional);

        assertThrows(IllegalArgumentException.class, () -> PluginVersion.parseSemanticVersion("1.3.0rc.1"));
        assertThrows(IllegalArgumentException.class, () -> PluginVersion.parseSemanticVersion("some string"));
    }

    @Test
    void testIsOldRelease() {
        // Invalid current version
        assertFalse(assertDoesNotThrow(() -> isOldReleaseWrapper("invalid", "1.3.1")));

        // Invalid latest version
        assertFalse(assertDoesNotThrow(() -> isOldReleaseWrapper("1.3.0", "invalid")));
        assertFalse(assertDoesNotThrow(() -> isOldReleaseWrapper("1.3.0", "1.3.1-rc.1")));

        // Latest is not higher
        assertFalse(assertDoesNotThrow(() -> isOldReleaseWrapper("2.0.0", "1.3.0")));
        assertFalse(assertDoesNotThrow(() -> isOldReleaseWrapper("1.4.1", "1.3.0")));
        assertFalse(assertDoesNotThrow(() -> isOldReleaseWrapper("1.3.1", "1.3.0")));
        assertFalse(assertDoesNotThrow(() -> isOldReleaseWrapper("1.3.1", "1.3.1")));

        // Latest is higher
        assertTrue(assertDoesNotThrow(() -> isOldReleaseWrapper("1.3.0", "2.0.0")));
        assertTrue(assertDoesNotThrow(() -> isOldReleaseWrapper("1.3.0", "1.4.0")));
        assertTrue(assertDoesNotThrow(() -> isOldReleaseWrapper("1.3.0", "1.3.1")));
    }

    private boolean isOldReleaseWrapper(String currentVersion, String latestVersion) throws IOException {
        PluginVersion v = new PluginVersion();
        v.setLatestVersion(latestVersion);
        return v.isOldRelease(currentVersion);
    }
}
