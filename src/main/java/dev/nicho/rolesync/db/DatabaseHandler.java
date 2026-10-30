package dev.nicho.rolesync.db;

import dev.nicho.rolesync.RoleSync;
import dev.nicho.rolesync.db.migrations.DatabaseMigrator;
import dev.nicho.rolesync.db.migrations.Dialect;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.bukkit.Bukkit;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.Consumer;

public abstract class DatabaseHandler {

    protected final RoleSync plugin;
    protected final SecureRandom random;

    /**
     * Creates a new database handler
     *
     * @param plugin a link to the plugin
     */
    protected DatabaseHandler(RoleSync plugin) {
        this.plugin = plugin;
        this.random = new SecureRandom();
    }

    /**
     * Gets the name of the table, from database.tablePrefix in the config.
     * The table name is added to queries directly. It is validated by ConfigLinter.
     *
     * @return the table name
     */
    private String getTableName() {
        return getTablePrefix() + "_discordmcusers";
    }

    /**
     * Gets the table prefix, from database.tablePrefix in the config. It is validated by ConfigLinter.
     *
     * @return the table prefix
     */
    private String getTablePrefix() {
        return plugin.getConfig().getString("database.tablePrefix");
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
     * Closes the database, releasing its connections and file locks.
     * The handler can't be used after this.
     *
     * @throws SQLException if an SQL error occurs
     */
    public abstract void close() throws SQLException;

    /**
     * Gets the SQL dialect of this database, used to pick dialect-specific migrations.
     *
     * @return the dialect
     */
    protected abstract Dialect getDialect();

    /**
     * Initializes the database, migrating its schema to the latest version.
     *
     * @throws SQLException if an SQL error occurs
     */
    protected void initialize() throws SQLException {
        DatabaseMigrator migrator = new DatabaseMigrator(plugin.getLogger(), getDialect(), getTablePrefix());

        Connection c = this.getConnection();
        try {
            migrator.migrate(c);
        } finally {
            this.closeConnection(c);
        }
    }

    /**
     * Gets the number of linked users.
     *
     * @return the number of linked users
     * @throws SQLException if an SQL error occurs
     */
    @SuppressFBWarnings(value = "SQL_INJECTION_JDBC", justification = "The table prefix is validated by ConfigLinter. All other values use parameters")
    public int getLinkedUserCount() throws SQLException {
        checkAsync();

        Connection c = this.getConnection();
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM "
                + getTableName());
             ResultSet res = ps.executeQuery()) {
            int ret = 0;
            if (res.next()) ret = res.getInt(1);

            return ret;
        } finally {
            this.closeConnection(c);
        }
    }

    /**
     * Links a user.
     *
     * @param discordID     the Discord ID of the user
     * @param minecraftUUID the Minecraft UUID of the user
     * @throws SQLException if an SQL error occurs
     */
    @SuppressFBWarnings(value = "SQL_INJECTION_JDBC", justification = "The table prefix is validated by ConfigLinter. All other values use parameters")
    public void linkUser(String discordID, String minecraftUUID) throws SQLException {
        checkAsync();

        Connection c = this.getConnection();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO " + getTableName() + " (discord_id, minecraft_uuid, verification_code) SELECT ?, ?, ? FROM (SELECT 1) " +
                        "as A WHERE NOT EXISTS(SELECT * FROM " + getTableName() + " WHERE minecraft_uuid = ?);"
        )) {
            ps.setString(1, discordID);
            ps.setString(2, minecraftUUID);
            ps.setInt(3, random.nextInt(900000) + 100000);
            ps.setString(4, minecraftUUID);

            ps.execute();
        } finally {
            this.closeConnection(c);
        }
    }

    /**
     * Updates a user's Minecraft username on the table
     *
     * @param identifier        the Discord ID or Minecraft UUID of the user
     * @param minecraftUsername the Minecraft username of the user
     * @throws SQLException if an SQL error occurs
     */
    @SuppressFBWarnings(value = "SQL_INJECTION_JDBC", justification = "The table prefix is validated by ConfigLinter. All other values use parameters")
    public void updateUsername(String identifier, String minecraftUsername) throws SQLException {
        checkAsync();

        Connection c = this.getConnection();
        try (PreparedStatement ps = c.prepareStatement("UPDATE "
                + getTableName() + " "
                + "SET username_when_linked = ? WHERE discord_id = ? OR minecraft_uuid = ?"
        )) {
            ps.setString(1, minecraftUsername);
            ps.setString(2, identifier);
            ps.setString(3, identifier);

            ps.execute();
        } finally {
            this.closeConnection(c);
        }
    }

    /**
     * Sets a user as being whitelisted.
     *
     * @param uuid the UUID of the user to whitelist.
     * @throws SQLException if an SQL error occurs
     */
    @SuppressFBWarnings(value = "SQL_INJECTION_JDBC", justification = "The table prefix is validated by ConfigLinter. All other values use parameters")
    public void addToWhitelist(String uuid) throws SQLException {
        checkAsync();

        Connection c = this.getConnection();
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE " + getTableName() + " " +
                        "SET whitelisted = 1 WHERE minecraft_uuid = ?")) {
            ps.setString(1, uuid);

            ps.execute();
        } finally {
            this.closeConnection(c);
        }
    }

    /**
     * Sets a user as not being whitelisted.
     *
     * @param uuid the UUID of the user to unwhitelist.
     * @throws SQLException if an SQL error occurs
     */
    @SuppressFBWarnings(value = "SQL_INJECTION_JDBC", justification = "The table prefix is validated by ConfigLinter. All other values use parameters")
    public void removeFromWhitelist(String uuid) throws SQLException {
        checkAsync();

        Connection c = this.getConnection();
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE " + getTableName() + " " +
                        "SET whitelisted = 0 WHERE minecraft_uuid = ?")) {
            ps.setString(1, uuid);

            ps.execute();
        } finally {
            this.closeConnection(c);
        }
    }

    /**
     * Unlinks a user.
     *
     * @param uuid the UUID of the user to unlink
     * @throws SQLException if an SQL error occurs
     */
    @SuppressFBWarnings(value = "SQL_INJECTION_JDBC", justification = "The table prefix is validated by ConfigLinter. All other values use parameters")
    public void unlink(String uuid) throws SQLException {
        checkAsync();

        Connection c = this.getConnection();
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM "
                + getTableName() + " WHERE minecraft_uuid = ?")) {
            ps.setString(1, uuid);

            ps.execute();
        } finally {
            this.closeConnection(c);
        }

        removeFromWhitelist(uuid);
    }

    /**
     * Runs a callback for every linked user.
     *
     * @param callback the callback. Receives a LinkedUserInfo object.
     * @throws SQLException if an SQL error occurs
     */
    @SuppressFBWarnings(value = "SQL_INJECTION_JDBC", justification = "The table prefix is validated by ConfigLinter. All other values use parameters")
    public void forAllLinkedUsers(Consumer<LinkedUserInfo> callback) throws SQLException {
        checkAsync();

        Connection c = this.getConnection();
        try (PreparedStatement ps = c.prepareStatement("SELECT discord_id, minecraft_uuid, whitelisted, verification_code, verified, username_when_linked FROM "
                + getTableName());
             ResultSet rs = ps.executeQuery()) {
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
        } finally {
            this.closeConnection(c);
        }
    }

    /**
     * Gets info from a linked user
     *
     * @param identifier the Discord ID or Minecraft UUID of the user
     * @return A LinkedUserInfo object with information about the user
     * @throws SQLException if an SQL error occurs
     */
    @SuppressFBWarnings(value = "SQL_INJECTION_JDBC", justification = "The table prefix is validated by ConfigLinter. All other values use parameters")
    public LinkedUserInfo getLinkedUserInfo(String identifier) throws SQLException {
        checkAsync();

        Connection c = this.getConnection();
        try {
            LinkedUserInfo ret;
            try (PreparedStatement ps = c.prepareStatement("SELECT discord_id, minecraft_uuid, whitelisted, verification_code, verified, username_when_linked FROM "
                    + getTableName() + " "
                    + "WHERE discord_id = ? OR minecraft_uuid = ?"
            )) {
                ps.setString(1, identifier);
                ps.setString(2, identifier);

                try (ResultSet res = ps.executeQuery()) {
                    if (!res.next()) return null;

                    ret = new LinkedUserInfo(
                            res.getString(1), // discord id
                            res.getString(2), // minecraft uuid
                            res.getBoolean(3), // whitelisted
                            res.getBoolean(5), // verified
                            res.getInt(4), // verification code
                            res.getString(6) // username when linked
                    );
                }
            }

            if (ret.code != 0) return ret;

            // probably from old version (migrated database)
            // verification info does not exist. Create and return new.
            int verificationCode = random.nextInt(900000) + 100000;
            try (PreparedStatement ps = c.prepareStatement("UPDATE "
                    + getTableName() + " "
                    + "SET verification_code = ? WHERE discord_id = ? OR minecraft_uuid = ?"
            )) {
                ps.setInt(1, verificationCode);
                ps.setString(2, identifier);
                ps.setString(3, identifier);

                ps.execute();
            }

            return new LinkedUserInfo(
                    ret.discordId,
                    ret.uuid,
                    ret.whitelisted,
                    false, // verified
                    verificationCode,
                    ret.username
            );
        } finally {
            this.closeConnection(c);
        }
    }

    /**
     * Tries to verify a user.
     *
     * @param identifier       the UUID or Discord ID of the user
     * @param verificationCode the code the user entered
     * @return true if the verification was successful or false otherwise (user not linked or code incorrect)
     * @throws SQLException if an SQL error occurs
     */
    @SuppressFBWarnings(value = "SQL_INJECTION_JDBC", justification = "The table prefix is validated by ConfigLinter. All other values use parameters")
    public boolean verify(String identifier, int verificationCode) throws SQLException {
        LinkedUserInfo userInfo = getLinkedUserInfo(identifier);
        if (userInfo == null) return false;

        if (userInfo.code != verificationCode) return false;

        Connection c = this.getConnection();
        try (PreparedStatement ps = c.prepareStatement("UPDATE "
                + getTableName() + " "
                + "SET verified = ? WHERE discord_id = ? OR minecraft_uuid = ?"
        )) {
            ps.setBoolean(1, true);
            ps.setString(2, identifier);
            ps.setString(3, identifier);

            ps.execute();
        } finally {
            closeConnection(c);
        }

        return true;
    }

    private void checkAsync() {
        if (Bukkit.isPrimaryThread()) {
            plugin.debugLog("Database operation called from main Bukkit thread.");
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
         * The username the player had last time they were linked
         */
        public final String username;

        /**
         * Constructor
         *
         * @param discordId   of the user
         * @param uuid        of the user
         * @param whitelisted if the user is whitelisted
         * @param verified    if the user is verified
         * @param code        the verification code for that user
         * @param username    the username of that user
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
