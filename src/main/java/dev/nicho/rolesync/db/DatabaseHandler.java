package dev.nicho.rolesync.db;

import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public abstract class DatabaseHandler {

    protected JavaPlugin plugin = null;

    protected DatabaseHandler(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    protected abstract Connection getConnection() throws SQLException;

    protected void initialize() throws SQLException {
        Connection c = this.getConnection();
        PreparedStatement ps = c.prepareStatement(
                "CREATE TABLE IF NOT EXISTS `" + plugin.getConfig().getString("database.tablePrefix") + "_discordmcusers` ("
                        + "`discord_id` varchar(18) NOT NULL,"
                        + "`minecraft_uuid` varchar(36) NOT NULL,"
                        + "`whitelisted` boolean NOT NULL DEFAULT false,"
                        + "PRIMARY KEY (`discord_id`));"
        );

        ps.execute();
    }

    public String findUUIDByDiscordID(String id) throws SQLException {
        Connection c = this.getConnection();
        PreparedStatement ps = c.prepareStatement(
                "SELECT minecraft_uuid FROM " + plugin.getConfig().getString("database.tablePrefix") + "_discordmcusers " +
                        "WHERE discord_id = ?"
        );

        ps.setString(1, id);

        ResultSet res = ps.executeQuery();

        if (res.next()) return res.getString(1);

        return null;
    }

    public String findDiscordIDbyUUID(String uuid) throws SQLException {
        Connection c = this.getConnection();
        PreparedStatement ps = c.prepareStatement(
                "SELECT discord_id FROM " + plugin.getConfig().getString("database.tablePrefix") + "_discordmcusers " +
                        "WHERE minecraft_uuid = ?"
        );

        ps.setString(1, uuid);

        ResultSet res = ps.executeQuery();

        if (res.next()) return res.getString(1);

        return null;
    }

    public void linkUser(String discordID, String minecraftUUID) throws SQLException {
        Connection c = this.getConnection();
        PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + plugin.getConfig().getString("database.tablePrefix") +
                        "_discordmcusers (discord_id, minecraft_uuid) SELECT ?, ? FROM (SELECT 1) " +
                        "as A WHERE NOT EXISTS(SELECT * FROM " + plugin.getConfig().getString("database.tablePrefix") +
                        "_discordmcusers WHERE minecraft_uuid = ?);"
        );

        ps.setString(1, discordID);
        ps.setString(2, minecraftUUID);
        ps.setString(3, minecraftUUID);

        ps.execute();
    }

    public void addToWhitelist(String uuid) throws SQLException {
        Connection c = this.getConnection();
        PreparedStatement ps = c.prepareStatement(
                "UPDATE " + plugin.getConfig().getString("database.tablePrefix") + "_discordmcusers " +
                        "SET whitelisted = true WHERE minecraft_uuid = ?");

        ps.setString(1, uuid);

        ps.execute();
    }

    public void removeFromWhitelist(String uuid) throws SQLException {
        Connection c = this.getConnection();
        PreparedStatement ps = c.prepareStatement(
                "UPDATE " + plugin.getConfig().getString("database.tablePrefix") + "_discordmcusers " +
                        "SET whitelisted = false WHERE minecraft_uuid = ?");

        ps.setString(1, uuid);

        ps.execute();
    }

    public void unlink(String uuid) throws SQLException {
        Connection c = this.getConnection();
        PreparedStatement ps = c.prepareStatement("DELETE FROM "
                + plugin.getConfig().getString("database.tablePrefix") + "_discordmcusers WHERE minecraft_uuid = ?");

        ps.setString(1, uuid);

        ps.execute();

        removeFromWhitelist(uuid);
    }

    public ResultSet getAllLinkedUsers() throws SQLException {
        Connection c = this.getConnection();
        PreparedStatement ps = c.prepareStatement("SELECT discord_id, minecraft_uuid FROM "
                + plugin.getConfig().getString("database.tablePrefix") + "_discordmcusers");

        return ps.executeQuery();
    }

    public ResultSet getWhitelist() throws SQLException {
        Connection c = this.getConnection();
        PreparedStatement ps = c.prepareStatement("SELECT minecraft_uuid FROM "
                + plugin.getConfig().getString("database.tablePrefix") + "_discordmcusers WHERE whitelisted = true");

        return ps.executeQuery();
    }
}
