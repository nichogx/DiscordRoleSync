package dev.nicho.rolesync.db;

import org.apache.commons.dbcp.BasicDataSource;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.SQLException;

public class MySQLHandler extends DatabaseHandler {

    private BasicDataSource ds = null;

    public MySQLHandler(JavaPlugin plugin, String host, int port, String db, String user, String passwd) throws SQLException {
        super(plugin);

        this.ds = new BasicDataSource();

        ds.setUrl("jdbc:mysql://" + host + ":" + port + "/" + db);
        ds.setUsername(user);
        ds.setPassword(passwd);
        ds.setValidationQuery("SELECT 1");
        ds.setTestOnBorrow(true);

        this.initialize();
    }

    @Override
    protected Connection getConnection() throws SQLException {
        return ds.getConnection();
    }

    @Override
    protected void closeConnection(Connection c) throws SQLException {
        c.close();
    }
}
