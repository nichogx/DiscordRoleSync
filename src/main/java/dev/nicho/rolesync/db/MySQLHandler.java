package dev.nicho.rolesync.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.nicho.rolesync.RoleSync;
import dev.nicho.rolesync.db.migrations.Dialect;

import java.sql.Connection;
import java.sql.SQLException;

public final class MySQLHandler extends DatabaseHandler {

    private final HikariDataSource ds;

    public MySQLHandler(RoleSync plugin, String host, int port, String db, String user, String passwd) throws SQLException {
        super(plugin);

        HikariConfig config = new HikariConfig();
        config.setPoolName("DiscordRoleSync");
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + db);
        config.setUsername(user);
        config.setPassword(passwd);
        config.setMaximumPoolSize(8);
        // HikariCP keeps the pool full by default. Open connections only when needed instead, as
        // MySQL hosts often limit connections per user and other plugins may share the same user.
        config.setMinimumIdle(1);

        if (plugin.getConfig().getBoolean("database.mysql.disableSSL")) {
            // sslMode replaces useSSL in Connector/J 8.0.13+, and takes precedence when both are set.
            // useSSL is kept for servers that bundle older drivers.
            config.addDataSourceProperty("sslMode", "DISABLED");
            config.addDataSourceProperty("useSSL", "false");
        }

        try {
            this.ds = new HikariDataSource(config);
        } catch (RuntimeException e) {
            // HikariCP reports connection and driver lookup failures as unchecked exceptions
            throw new SQLException("Could not connect to the MySQL database: " + e.getMessage(), e);
        }

        try {
            this.initialize();
        } catch (SQLException | RuntimeException e) {
            // Don't leave the pool's threads and connections behind when the plugin disables itself
            ds.close();
            throw e;
        }
    }

    @Override
    protected Dialect getDialect() {
        return Dialect.MYSQL;
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
    public void close() throws SQLException {
        ds.close();
    }
}
