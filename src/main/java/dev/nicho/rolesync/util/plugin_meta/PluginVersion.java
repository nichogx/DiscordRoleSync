package dev.nicho.rolesync.util.plugin_meta;

import dev.nicho.rolesync.RoleSync;
import org.jetbrains.annotations.TestOnly;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PluginVersion {

    private String latestVersion;
    private final RoleSync plugin;

    public PluginVersion() {
        this(null);
    }

    public PluginVersion(RoleSync plugin) {
        this.plugin = plugin;
    }

    public String getLatestVersion() throws IOException {
        if (latestVersion == null) {
            this.refreshLatestVersion();
        }

        if (plugin != null) {
            plugin.debugLog("getLatestVersion: %s", latestVersion);
        }
        return latestVersion;
    }

    /**
     * Sets the latest version. Used only for tests.
     *
     * @param version The version to set as latest.
     */
    @TestOnly
    protected void setLatestVersion(String version) {
        this.latestVersion = version;
    }

    /**
     * Refreshes the latest version number from Spigot
     *
     * @throws IOException if an error occurs while connecting to the API
     */
    public void refreshLatestVersion() throws IOException {
        URL reqUrl = new URL("https://api.modrinth.com/v2/project/discordrolesync/version");

        HttpURLConnection c = (HttpURLConnection) reqUrl.openConnection();
        c.setRequestMethod("GET");
        c.connect();

        if (c.getResponseCode() != 200) {
            throw new IOException("Error getting latest version from Modrinth.");
        }

        JSONArray body;
        try (
                InputStream response = c.getInputStream();
                Scanner scanner = new Scanner(response)
        ) {
            body = new JSONArray(scanner.useDelimiter("\\A").next());
        }

        if (body.isEmpty()) {
            throw new IOException("No versions found on Modrinth.");
        }

        JSONObject latestVersion = body.getJSONObject(0);
        this.latestVersion = latestVersion.getString("version_number");

        if (plugin != null) {
            plugin.debugLog("refreshed latest version: %s", this.latestVersion);
        }
    }

    /**
     * Gets the version type for this String.
     *
     * @param version the version String
     * @return a VersionType enum value that represents this version
     */
    public static VersionType getVersionType(String version) {
        Pattern release = Pattern.compile("^\\d+\\.\\d+\\.\\d+$");
        if (release.matcher(version).find()) {
            return VersionType.RELEASE;
        }

        Pattern releaseCandidate = Pattern.compile("^\\d+\\.\\d+\\.\\d+-.+$");
        if (releaseCandidate.matcher(version).find()) {
            return VersionType.RELEASE_CANDIDATE;
        }

        Pattern devPreview = Pattern.compile("^master-.+$");
        if (devPreview.matcher(version).find()) {
            return VersionType.DEVELOPMENT_PREVIEW;
        }

        return VersionType.UNKNOWN;
    }

    /**
     * Compares the passed version to the latest release.
     *
     * @param version the version to compare
     * @return true if the latest release is a clean release, later than the installed one. False otherwise.
     * @throws IOException in case we can't get the latest version
     */
    public boolean isOldRelease(String version) throws IOException {
        SemanticVersion current;
        try {
            current = parseSemanticVersion(version);
        } catch (IllegalArgumentException e) {
            // If the currently running version is not a clean release or a release candidate,
            // don't prompt the user to update.
            return false;
        }

        SemanticVersion latest;
        try {
            String latestRaw = getLatestVersion();
            if (getVersionType(latestRaw) != VersionType.RELEASE) {
                // If the latest one is not a clean release, don't prompt the user
                // to update.
                return false;
            }

            latest = parseSemanticVersion(latestRaw);
        } catch (IllegalArgumentException e) {
            // Shouldn't happen, but if anything is wrong with the latest release, don't
            // prompt the user to update.
            return false;
        }

        if (latest.major > current.major) return true;
        if (latest.major < current.major) return false;

        if (latest.minor > current.minor) return true;
        if (latest.minor < current.minor) return false;

        return latest.patch > current.patch;
    }

    /**
     * Parses a semantic version string into an object.
     *
     * @param version the version string
     * @return a semantic version object
     * @throws IllegalArgumentException if the passed in string is not a semantic version
     */
    protected static SemanticVersion parseSemanticVersion(String version) throws IllegalArgumentException {
        Pattern pattern = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)(?:-(.+))?$");
        Matcher matcher = pattern.matcher(version);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Must be a semantic version formatted string.");
        }

        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        int patch = Integer.parseInt(matcher.group(3));

        return new SemanticVersion(major, minor, patch, matcher.group(4));
    }

    protected static class SemanticVersion {
        public final int major;
        public final int minor;
        public final int patch;
        public final String additional;

        public SemanticVersion(int major, int minor, int patch, String additional) {
            this.major = major;
            this.minor = minor;
            this.patch = patch;
            this.additional = additional;
        }
    }

    public enum VersionType {
        UNKNOWN,
        DEVELOPMENT_PREVIEW,
        RELEASE_CANDIDATE,
        RELEASE,
    }
}
