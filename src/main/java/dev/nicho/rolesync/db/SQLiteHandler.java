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
            db.createNewFile();
        }

        this.db = db;

        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
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
        // do nothing. SQLite connection will be kept open forever
    }
}
