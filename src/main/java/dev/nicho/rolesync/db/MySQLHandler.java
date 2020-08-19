package dev.nicho.rolesync.db;

import org.apache.commons.dbcp.BasicDataSource;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class MySQLHandler extends DatabaseHandler {

    private final BasicDataSource ds;

    public MySQLHandler(JavaPlugin plugin, String host, int port, String db, String user, String passwd) throws SQLException {
        super(plugin);

        this.ds = new BasicDataSource();

        if (plugin.getConfig().getBoolean("database.mysql.disableSSL")) {
            this.ds.addConnectionProperty("useSSL", "false");
        }

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

    @Override
    protected boolean hasColumn(String table, String column) throws SQLException {
        Connection c = this.getConnection();

        PreparedStatement ps = c.prepareStatement("SHOW COLUMNS FROM " + table + " LIKE ?");

        ps.setString(1, column);

        ResultSet res = ps.executeQuery();

        boolean ret = false;
        if (res.next() && res.getString(1).equalsIgnoreCase(column)) {
            ret = true;
        }

        closeConnection(c);

        return ret;
    }
}
