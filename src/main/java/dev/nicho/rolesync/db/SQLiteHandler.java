package dev.nicho.rolesync.db;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.sql.*;

public class SQLiteHandler extends DatabaseHandler {

    private final File db;
    private Connection connection;

    public SQLiteHandler(JavaPlugin plugin, File db) throws IOException, SQLException {
        super(plugin);

        if (!db.exists()) {
            if (!db.createNewFile()) {
                throw new IllegalStateException("Unable to create new SQLite database.");
            }
        }

        this.db = db;

        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Unable to load SQLite JBDC driver. This is probably a bug with the plugin and should be reported.\n" +
                    e.getMessage());
        }

        this.initialize();
    }

    @Override
    protected Connection getConnection() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            return connection;
        }

        connection = DriverManager.getConnection("jdbc:sqlite:" + db);
        return connection;
    }

    @Override
    protected void closeConnection(Connection c) {
        // do nothing. SQLite's connection will be kept open forever
    }
}
