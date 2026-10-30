package dev.nicho.rolesync.db;

import dev.nicho.rolesync.RoleSync;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SQLiteHandlerTest {

    @TempDir
    File tempDir;

    private MockedStatic<Bukkit> bukkit;
    private RoleSync plugin;

    @BeforeEach
    void setUp() {
        bukkit = Mockito.mockStatic(Bukkit.class);
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);

        FileConfiguration config = Mockito.mock(FileConfiguration.class);
        Mockito.when(config.getString("database.tablePrefix")).thenReturn("drs");

        plugin = Mockito.mock(RoleSync.class);
        Mockito.when(plugin.getConfig()).thenReturn(config);
    }

    @AfterEach
    void tearDown() {
        bukkit.close();
    }

    @Test
    void testClose() throws IOException, SQLException {
        SQLiteHandler db = new SQLiteHandler(plugin, new File(tempDir, "database.db"));
        db.linkUser("123", "00000000-0000-0000-0000-000000000001");
        assertEquals(1, db.getLinkedUserCount());

        db.close();
        assertThrows(SQLException.class, db::getLinkedUserCount);

        // Closing twice is harmless
        db.close();

        // The data was saved and the file can be opened again
        SQLiteHandler reopened = new SQLiteHandler(plugin, new File(tempDir, "database.db"));
        assertEquals(1, reopened.getLinkedUserCount());
        reopened.close();
    }
}
