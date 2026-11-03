package dev.nicho.rolesync.util.plugin_meta;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

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

        // Current is a release candidate
        assertTrue(assertDoesNotThrow(() -> isOldReleaseWrapper("1.3.0-rc.1", "1.3.0")));
        assertTrue(assertDoesNotThrow(() -> isOldReleaseWrapper("1.3.0-rc.1", "1.3.1")));
        assertFalse(assertDoesNotThrow(() -> isOldReleaseWrapper("1.3.0-rc.1", "1.2.9")));
    }

    @Test
    void testParseLatestVersion() throws IOException {
        assertEquals("2.4.0", parseLatestVersionWrapper(
                "[{\"version_number\":\"2.4.0\"},{\"version_number\":\"2.3.1\"}]"));
    }

    @Test
    void testParseLatestVersionInvalidResponse() {
        // Regression: invalid responses used to throw an unchecked exception that aborted plugin enable.
        assertThrows(IOException.class, () -> parseLatestVersionWrapper("<html><body>502 Bad Gateway</body></html>"));
        assertThrows(IOException.class, () -> parseLatestVersionWrapper(""));
        assertThrows(IOException.class, () -> parseLatestVersionWrapper("{\"error\":\"not_found\"}"));
        assertThrows(IOException.class, () -> parseLatestVersionWrapper("[]"));
        assertThrows(IOException.class, () -> parseLatestVersionWrapper("[\"2.4.0\"]"));
        assertThrows(IOException.class, () -> parseLatestVersionWrapper("[{\"name\":\"2.4.0\"}]"));
    }

    private String parseLatestVersionWrapper(String body) throws IOException {
        return PluginVersion.parseLatestVersion(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
    }

    private boolean isOldReleaseWrapper(String currentVersion, String latestVersion) throws IOException {
        PluginVersion v = new PluginVersion();
        v.setLatestVersion(latestVersion);
        return v.isOldRelease(currentVersion);
    }

    @Test
    void testRefreshLatestVersionTimesOutOnUnresponsiveServer() throws IOException {
        // Accepts the connection but never sends a response, like a hung Modrinth
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            Thread acceptor = new Thread(() -> {
                try (Socket ignored = server.accept()) {
                    Thread.sleep(30_000);
                } catch (IOException | InterruptedException ignored) {
                    // test finished
                }
            });
            acceptor.setDaemon(true);
            acceptor.start();

            PluginVersion v = new PluginVersion(null, "2.4.0",
                    "http://127.0.0.1:" + server.getLocalPort() + "/version", 500);

            assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
                    assertThrows(SocketTimeoutException.class, v::refreshLatestVersion));

            acceptor.interrupt();
        }
    }

    @Test
    void testRefreshLatestVersionSendsPluginUserAgent() throws IOException {
        AtomicReference<String> receivedUserAgent = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/version", exchange -> {
            receivedUserAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
            byte[] body = "[{\"version_number\":\"2.4.0\"}]".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();

        try {
            PluginVersion v = new PluginVersion(null, "2.3.3",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/version", 5_000);
            v.refreshLatestVersion();

            assertEquals("nichogx/DiscordRoleSync-plugin/2.3.3 (https://gitlab.com/nichogx/DiscordRoleSync)",
                    receivedUserAgent.get());
            assertEquals("2.4.0", v.getLatestVersion());
        } finally {
            server.stop(0);
        }
    }
}
