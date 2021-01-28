package dev.nicho.rolesync.db;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.Consumer;

public abstract class DatabaseHandler {

    protected final JavaPlugin plugin;
    protected final SecureRandom random;

    /**
     * Creates a new database handler
     *
     * @param plugin a link to the plugin
     */
    protected DatabaseHandler(JavaPlugin plugin) {
        this.plugin = plugin;
        this.random = new SecureRandom();
    }

    /**
     * Gets a connection for querying
     *
     * @return the connection
     * @throws SQLException if an SQL error occurs
     */
    protected abstract Connection getConnection() throws SQLException;

    /**
     * Closes a connection if it should be closed or returns it to the pool
     *
     * @param c the connection
     * @throws SQLException if an SQL error occurs
     */
    protected abstract void closeConnection(Connection c) throws SQLException;

    /**
     * Initializes the database, creating tables if needed.
     *
     * @throws SQLException if an SQL error occurs
     */
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

    /**
     * Gets the number of linked users.
     *
     * @return the number of linked users
     * @throws SQLException if an SQL error occurs
     */
    public int getLinkedUserCount() throws SQLException {
        checkAsync();

        Connection c = this.getConnection();
        PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM "
                + plugin.getConfig().getString("database.tablePrefix") + "_discordmcusers");

        ResultSet res = ps.executeQuery();
        int ret = 0;
        if (res.next()) ret = res.getInt(1);

        this.closeConnection(c);

        return ret;
    }

    /**
     * Links a user.
     *
     * @param discordID the Discord ID of the user
     * @param minecraftUUID the Minecraft UUID of the user
     * @throws SQLException if an SQL error occurs
     */
    public void linkUser(String discordID, String minecraftUUID) throws SQLException {
        checkAsync();

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

    /**
     * Sets a user as being whitelisted.
     *
     * @param uuid the UUID of the user to whitelist.
     * @throws SQLException if an SQL error occurs
     */
    public void addToWhitelist(String uuid) throws SQLException {
        checkAsync();

        Connection c = this.getConnection();
        PreparedStatement ps = c.prepareStatement(
                "UPDATE " + plugin.getConfig().getString("database.tablePrefix") + "_discordmcusers " +
                        "SET whitelisted = 1 WHERE minecraft_uuid = ?");

        ps.setString(1, uuid);

        ps.execute();

        this.closeConnection(c);
    }

    /**
     * Sets a user as not being whitelisted.
     *
     * @param uuid the UUID of the user to unwhitelist.
     * @throws SQLException if an SQL error occurs
     */
    public void removeFromWhitelist(String uuid) throws SQLException {
        checkAsync();

        Connection c = this.getConnection();
        PreparedStatement ps = c.prepareStatement(
                "UPDATE " + plugin.getConfig().getString("database.tablePrefix") + "_discordmcusers " +
                        "SET whitelisted = 0 WHERE minecraft_uuid = ?");

        ps.setString(1, uuid);

        ps.execute();

        this.closeConnection(c);
    }

    /**
     * Unlinks a user.
     *
     * @param uuid the UUID of the user to unlink
     * @throws SQLException if an SQL error occurs
     */
    public void unlink(String uuid) throws SQLException {
        checkAsync();

        Connection c = this.getConnection();
        PreparedStatement ps = c.prepareStatement("DELETE FROM "
                + plugin.getConfig().getString("database.tablePrefix") + "_discordmcusers WHERE minecraft_uuid = ?");

        ps.setString(1, uuid);

        ps.execute();

        this.closeConnection(c);

        removeFromWhitelist(uuid);
    }

    /**
     * Runs a callback for every linked user.
     *
     * @param callback the callback. Receives a LinkedUserInfo object.
     * @throws SQLException if an SQL error occurs
     */
    public void forAllLinkedUsers(Consumer<LinkedUserInfo> callback) throws SQLException {
        checkAsync();

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

    /**
     * Gets info from a linked user
     *
     * @param identifier the Discord ID or Minecraft UUID of the user
     * @return A LinkedUserInfo object with information about the user
     * @throws SQLException if an SQL error occurs
     */
    public LinkedUserInfo getLinkedUserInfo(String identifier) throws SQLException {
        checkAsync();

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

    /**
     * Tries to verify a user.
     *
     * @param identifier the UUID or Discord ID of the user
     * @param verificationCode the code the user entered
     * @return true if the verification was successful or false otherwise (user not linked or code incorrect)
     * @throws SQLException if an SQL error occurs
     */
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

    private final void checkAsync() {
        if (Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Attempted to execute a database operation from the server thread!");
        }
    }

    /**
     * A class with information about the user.
     */
    public static class LinkedUserInfo {
        public final int code;
        public final boolean verified;
        public final boolean whitelisted;
        public final String discordId;
        public final String uuid;

        /**
         * The username the player had WHEN THEY LINKED
         */
        public final String username;

        /**
         * Constructor
         *
         * @param discordId of the user
         * @param uuid of the user
         * @param whitelisted if the user is whitelisted
         * @param verified if the user is verified
         * @param code the verification code for that user
         * @param username the username of that user
         */
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
