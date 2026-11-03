package dev.nicho.rolesync.bot;

import dev.nicho.rolesync.RoleSync;
import net.dv8tion.jda.api.entities.Guild;
import org.bukkit.configuration.file.FileConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyncBotTest {

    private final List<LogRecord> warnings = new ArrayList<>();
    private SyncBot bot;

    @BeforeEach
    void setUp() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel().intValue() >= Level.WARNING.intValue()) warnings.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });

        FileConfiguration config = Mockito.mock(FileConfiguration.class);
        Mockito.when(config.getString("bot.server")).thenReturn("100000000000000001");

        RoleSync plugin = Mockito.mock(RoleSync.class);
        Mockito.when(plugin.getConfig()).thenReturn(config);
        Mockito.when(plugin.getLogger()).thenReturn(logger);

        bot = new SyncBot(plugin);
    }

    private static Guild guild(String id, String name) {
        Guild guild = Mockito.mock(Guild.class);
        Mockito.when(guild.getId()).thenReturn(id);
        Mockito.when(guild.getName()).thenReturn(name);
        return guild;
    }

    @Test
    void noWarningWhenOnlyInConfiguredServer() {
        bot.warnAboutOtherServers(List.of(guild("100000000000000001", "Main")));

        assertEquals(0, warnings.size());
    }

    @Test
    void warnsAboutOtherServers() {
        bot.warnAboutOtherServers(List.of(
                guild("100000000000000001", "Main"),
                guild("100000000000000002", "Other"),
                guild("100000000000000003", "Another")
        ));

        assertEquals(1, warnings.size());
        String message = warnings.get(0).getMessage();
        assertTrue(message.contains("Other (100000000000000002), Another (100000000000000003)"), message);
        assertTrue(message.contains("100000000000000001"), message);
        assertFalse(message.contains("Main"), message);
    }
}
