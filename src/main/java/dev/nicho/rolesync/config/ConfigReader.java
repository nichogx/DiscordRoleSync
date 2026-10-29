package dev.nicho.rolesync.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class ConfigReader {
    public static FileConfiguration getConfigFromResource(String resourcePath) throws IOException {
        try (InputStream stream = ConfigReader.class.getResourceAsStream(withLeadingSlash(resourcePath))) {
            if (stream == null) {
                throw new FileNotFoundException("Resource " + resourcePath + " not found in classpath.");
            }

            FileConfiguration config = new YamlConfiguration();
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                config.load(reader);
            } catch (InvalidConfigurationException e) {
                throw new IllegalArgumentException("Pattern resource " + resourcePath + " is not in the expected format.");
            }

            return config;
        }
    }

    private static String withLeadingSlash(String path) {
        return (path.charAt(0) == '/' ? "" : "/") + path;
    }
}
