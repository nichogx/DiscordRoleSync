package dev.nicho.rolesync.db;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class SQLiteHandler extends DatabaseHandler {

    private File db = null;

    public SQLiteHandler(JavaPlugin plugin, File db) throws IOException, SQLException {
        if (!db.exists()) {
            db.createNewFile();
        }

        this.plugin = plugin;
        this.db = db;

        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        Connection c = this.getConnection();
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
}
