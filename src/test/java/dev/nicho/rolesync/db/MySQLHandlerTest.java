package dev.nicho.rolesync.db;

import dev.nicho.rolesync.RoleSync;
import org.bukkit.configuration.file.FileConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.SQLException;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertThrows;

class MySQLHandlerTest {

    @Test
    void testConnectionFailureThrowsSQLException() {
        FileConfiguration config = Mockito.mock(FileConfiguration.class);
        Mockito.when(config.getString("database.tablePrefix")).thenReturn("drs");

        RoleSync plugin = Mockito.mock(RoleSync.class);
        Mockito.when(plugin.getConfig()).thenReturn(config);
        Mockito.when(plugin.getLogger()).thenReturn(Logger.getLogger("MySQLHandlerTest"));

        // There's no MySQL driver in the tests, so the pool fails to start without connecting.
        // onEnable() only handles SQLException, so anything else would crash the plugin on startup.
        assertThrows(SQLException.class,
                () -> new MySQLHandler(plugin, "127.0.0.1", 3306, "drs", "user", "password"));
    }
}
