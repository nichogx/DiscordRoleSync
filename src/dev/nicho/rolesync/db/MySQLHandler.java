package dev.nicho.rolesync.db;

import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.SQLException;

public class MySQLHandler extends DatabaseHandler {

    public MySQLHandler(JavaPlugin plugin, String host, int port, String db, String user, String passwd) {

    }

    @Override
    protected Connection getConnection() throws SQLException {
        // TODO
        return null;
    }
}
