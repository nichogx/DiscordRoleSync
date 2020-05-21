package dev.nicho.rolesync.db;

import org.bukkit.plugin.java.JavaPlugin;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public abstract class DatabaseHandler {

    protected final JavaPlugin plugin;
    protected final SecureRandom random;

    protected DatabaseHandler(JavaPlugin plugin) {
        this.plugin = plugin;
        this.random = new SecureRandom();
    }

    protected abstract Connection getConnection() throws SQLException;
    protected abstract void closeConnection(Connection c) throws SQLException;
    protected abstract boolean hasColumn(String table, String column) throws SQLException;

    protected void initialize() throws SQLException {
        Connection c = this.getConnection();
        PreparedStatement ps = c.prepareStatement(
                "CREATE TABLE IF NOT EXISTS `" + plugin.getConfig().getString("database.tablePrefix") + "_discordmcusers` ("
                        + "`discord_id` varchar(18) NOT NULL,"
                        + "`minecraft_uuid` varchar(36) NOT NULL,"
                        + "`whitelisted` boolean NOT NULL DEFAULT false,"
                        + "`verification_code` INT NOT NULL DEFAULT 0,"
                        + "`verified` boolean NOT NULL DEFAULT false,"
                        + "`username_when_linked` TEXT NULL,"
                        + "PRIMARY KEY (`discord_id`));"
        );

        ps.execute();

        this.closeConnection(c);
    }

    public int getLinkedUserCount() throws SQLException {
        Connection c = this.getConnection();
        PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM "
                + plugin.getConfig().getString("database.tablePrefix") + "_discordmcusers");

        ResultSet res = ps.executeQuery();
        int ret = 0;
        if (res.next()) ret = res.getInt(1);

        this.closeConnection(c);

        return ret;
    }

    public void linkUser(String discordID, String minecraftUUID) throws SQLException {
        Connection c = this.getConnection();
        PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + plugin.getConfig().getString("database.tablePrefix") +
                        "_discordmcusers (discord_id, minecraft_uuid, verification_code) SELECT ?, ?, ? FROM (SELECT 1) " +
                        "as A WHERE NOT EXISTS(SELECT * FROM " + plugin.getConfig().getString("database.tablePrefix") +
                        "_discordmcusers WHERE minecraft_uuid = ?);"
        );

        ps.setString(1, discordID);
        ps.setString(2, minecraftUUID);
        ps.setInt(3, random.nextInt(900000) + 100000);
        ps.setString(4, minecraftUUID);

        ps.execute();

        this.closeConnection(c);
    }

    public void addToWhitelist(String uuid) throws SQLException {
        Connection c = this.getConnection();
        PreparedStatement ps = c.prepareStatement(
                "UPDATE " + plugin.getConfig().getString("database.tablePrefix") + "_discordmcusers " +
                        "SET whitelisted = true WHERE minecraft_uuid = ?");

        ps.setString(1, uuid);

        ps.execute();

        this.closeConnection(c);
    }

    public void removeFromWhitelist(String uuid) throws SQLException {
        Connection c = this.getConnection();
        PreparedStatement ps = c.prepareStatement(
                "UPDATE " + plugin.getConfig().getString("database.tablePrefix") + "_discordmcusers " +
                        "SET whitelisted = false WHERE minecraft_uuid = ?");

        ps.setString(1, uuid);

        ps.execute();

        this.closeConnection(c);
    }

    public void unlink(String uuid) throws SQLException {
        Connection c = this.getConnection();
        PreparedStatement ps = c.prepareStatement("DELETE FROM "
                + plugin.getConfig().getString("database.tablePrefix") + "_discordmcusers WHERE minecraft_uuid = ?");

        ps.setString(1, uuid);

        ps.execute();

        this.closeConnection(c);

        removeFromWhitelist(uuid);
    }

    public void forAllLinkedUsers(Consumer<LinkedUserInfo> callback) throws SQLException {
        Connection c = this.getConnection();
        PreparedStatement ps = c.prepareStatement("SELECT discord_id, minecraft_uuid, whitelisted, verification_code, verified, username_when_linked FROM "
                + plugin.getConfig().getString("database.tablePrefix") + "_discordmcusers");

        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            callback.accept(new LinkedUserInfo(
                    rs.getString(1), // discord id
                    rs.getString(2), // minecraft uuid
                    rs.getBoolean(3), // whitelisted
                    rs.getBoolean(5), // verified
                    rs.getInt(4), // verification code
                    rs.getString(6) // username when linked
            ));
        }

        this.closeConnection(c);
    }

    public LinkedUserInfo getLinkedUserInfo(String identifier) throws SQLException {
        Connection c = this.getConnection();
        PreparedStatement ps = c.prepareStatement("SELECT discord_id, minecraft_uuid, whitelisted, verification_code, verified, username_when_linked FROM "
                + plugin.getConfig().getString("database.tablePrefix") + "_discordmcusers "
                + "WHERE discord_id = ? OR minecraft_uuid = ?"
        );

        ps.setString(1, identifier);
        ps.setString(2, identifier);

        ResultSet res = ps.executeQuery();

        LinkedUserInfo ret = null;
        if (res.next()) {
            int verificationCode = res.getInt(4);

            if (verificationCode != 0) {
                ret = new LinkedUserInfo(
                        res.getString(1), // discord id
                        res.getString(2), // minecraft uuid
                        res.getBoolean(3), // whitelisted
                        res.getBoolean(5), // verified
                        verificationCode,
                        res.getString(6) // username when linked
                );
            } else {
                // probably from old version (migrated database)
                // verification info does not exist. Create and return new.
                verificationCode = random.nextInt(900000) + 100000;
                ret = new LinkedUserInfo(
                        res.getString(1), // discord id
                        res.getString(2), // minecraft uuid
                        res.getBoolean(3), // whitelisted
                        false, // verified
                        verificationCode,
                        res.getString(6) // username when linked
                );

                ps.close();
                ps = c.prepareStatement("UPDATE "
                        + plugin.getConfig().getString("database.tablePrefix") + "_discordmcusers "
                        + "SET verification_code = ? WHERE discord_id = ? OR minecraft_uuid = ?"
                );

                ps.setInt(1, verificationCode);
                ps.setString(2, identifier);
                ps.setString(3, identifier);

                ps.execute();
            }
        }

        this.closeConnection(c);

        return ret;
    }

    public boolean verify(String identifier, int verificationCode) throws SQLException {
        LinkedUserInfo userInfo = getLinkedUserInfo(identifier);
        if (userInfo == null) return false;

        if (userInfo.code != verificationCode) return false;

        Connection c = this.getConnection();

        PreparedStatement ps = c.prepareStatement("UPDATE "
                + plugin.getConfig().getString("database.tablePrefix") + "_discordmcusers "
                + "SET verified = ? WHERE discord_id = ? OR minecraft_uuid = ?"
        );

        ps.setBoolean(1, true);
        ps.setString(2, identifier);
        ps.setString(3, identifier);

        ps.execute();

        closeConnection(c);

        return true;
    }

    public boolean migrate() throws SQLException {
        boolean migrated = false;

        // <= 1.0.0-BETA.11 to 1.0.0-BETA.12
        if (!this.hasColumn(plugin.getConfig().getString("database.tablePrefix") + "_discordmcusers", "verified")) {
            // not upgraded yet
            Connection c = this.getConnection();
            PreparedStatement ps = c.prepareStatement("ALTER TABLE "
                    + plugin.getConfig().getString("database.tablePrefix") + "_discordmcusers "
                    + "ADD `verification_code` INT NOT NULL DEFAULT 0");

            ps.execute();
            ps.close();

            ps = c.prepareStatement("ALTER TABLE "
                    + plugin.getConfig().getString("database.tablePrefix") + "_discordmcusers "
                    + "ADD `verified` BOOLEAN NOT NULL DEFAULT false");

            ps.execute();
            ps.close();

            ps = c.prepareStatement("ALTER TABLE "
                    + plugin.getConfig().getString("database.tablePrefix") + "_discordmcusers "
                    + "ADD `username_when_linked` TEXT NULL");

            ps.execute();
            ps.close();

            migrated = true;

            closeConnection(c);
        }

        return migrated;
    }

    public static class LinkedUserInfo {
        public final int code;
        public final boolean verified;
        public final boolean whitelisted;
        public final String discordId;
        public final String uuid;
        public final String username;

        LinkedUserInfo(String discordId, String uuid, boolean whitelisted, boolean verified, int code, String username) {
            this.code = code;
            this.verified = verified;
            this.whitelisted = whitelisted;
            this.discordId = discordId;
            this.uuid = uuid;
            this.username = username;
        }
    }
}
