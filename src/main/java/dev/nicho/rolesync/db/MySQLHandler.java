package dev.nicho.rolesync.db;

import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class MySQLHandler extends DatabaseHandler {

    private String host = null;
    private int port = 3306;
    private String db = null;
    private String user = null;
    private String passwd = null;

    public MySQLHandler(JavaPlugin plugin, String host, int port, String db, String user, String passwd) throws SQLException {
        super(plugin);

        this.host = host;
        this.port = port;
        this.db = db;
        this.user = user;
        this.passwd = passwd;

        try {
            Class.forName("com.mysql.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        Connection c = getConnection();
        this.initialize();
    }

    @Override
    protected Connection getConnection() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            return connection;
        }

        connection = DriverManager.getConnection("jdbc:mysql://" + host + ":" + port + "/" + db, user, passwd);
        return connection;
    }
}
