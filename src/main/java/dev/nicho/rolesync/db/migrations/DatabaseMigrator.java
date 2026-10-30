package dev.nicho.rolesync.db.migrations;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DatabaseMigrator updates the database schema to the latest version, by running
 * the SQL files in resources/db/migrations in order. Applied migrations are recorded
 * in the {prefix}_schema_history table, so each one only runs once.
 * <p>
 * Migration files:
 * - Are named NNN_description.sql, e.g. 001_baseline.sql, and must be added to MIGRATIONS.
 * - May have a dialect-specific version, e.g. 001_baseline.mysql.sql or 001_baseline.sqlite.sql,
 * which is used instead of the shared file for that database.
 * - Use ${prefix} for the table prefix from the config.
 * - Are split into statements on ";". "--" starts a comment until the end of the line.
 * Both are ignored inside quotes. Escape quotes in strings by doubling them ('it''s'), not with backslashes.
 * <p>
 * Each migration runs in a transaction. MySQL commits schema changes (CREATE, ALTER...)
 * immediately, so they can't be rolled back there. To allow retrying a migration that
 * failed halfway, MySQL skips statements that fail because they were already applied
 * (e.g. an index that already exists). Data changes (INSERT, UPDATE, DELETE) must still
 * be safe to run again.
 * <p>
 * On MySQL, several servers may share the database and start at the same time. A named
 * lock makes sure only one of them migrates, and the others see the result.
 */
public final class DatabaseMigrator {

    /**
     * All migrations, in order. Never edit or remove a migration that has been released.
     */
    static final List<String> MIGRATIONS = List.of(
            "001_baseline",
            "002_unique_minecraft_uuid"
    );

    private static final String RESOURCE_DIR = "db/migrations";
    private static final Pattern NAME_PATTERN = Pattern.compile("^(\\d+)_[a-z0-9_]+$");
    private static final int LOCK_TIMEOUT_SECONDS = 60;

    /**
     * MySQL error codes for schema changes that were already applied: table already exists (1050),
     * duplicate column name (1060), duplicate key name (1061) and can't drop, doesn't exist (1091).
     */
    private static final Set<Integer> MYSQL_ALREADY_APPLIED_ERRORS = Set.of(1050, 1060, 1061, 1091);

    private final Logger logger;
    private final Dialect dialect;
    private final String prefix;
    private final String resourceDir;
    private final List<Migration> migrations = new ArrayList<>();

    /**
     * Creates a new DatabaseMigrator
     *
     * @param logger  the logger to log applied migrations to
     * @param dialect the dialect of the database
     * @param prefix  the table prefix. Added to queries directly, it must be validated by ConfigLinter.
     */
    public DatabaseMigrator(Logger logger, Dialect dialect, String prefix) {
        this(logger, dialect, prefix, RESOURCE_DIR, MIGRATIONS);
    }

    DatabaseMigrator(Logger logger, Dialect dialect, String prefix, String resourceDir, List<String> names) {
        if (names.isEmpty()) {
            throw new IllegalArgumentException("At least one migration is required");
        }

        this.logger = logger;
        this.dialect = dialect;
        this.prefix = prefix;
        this.resourceDir = resourceDir;

        int previousVersion = 0;
        for (String name : names) {
            Matcher m = NAME_PATTERN.matcher(name);
            if (!m.matches()) {
                throw new IllegalArgumentException("Invalid migration name " + name + ", must be like 001_description");
            }

            int version = Integer.parseInt(m.group(1));
            if (version != previousVersion + 1) {
                throw new IllegalArgumentException("Migration " + name + " is out of order, expected version " + (previousVersion + 1));
            }

            migrations.add(new Migration(version, name));
            previousVersion = version;
        }
    }

    /**
     * Gets the version the database will be on after migrating.
     *
     * @return the latest version
     */
    public int getLatestVersion() {
        return migrations.get(migrations.size() - 1).version;
    }

    /**
     * Applies all pending migrations.
     *
     * @param c the connection to the database
     * @throws SQLException if a migration fails, or the database is newer than this plugin supports
     */
    public void migrate(Connection c) throws SQLException {
        if (dialect != Dialect.MYSQL) {
            migrateLocked(c);
            return;
        }

        acquireLock(c);
        Exception failure = null;
        try {
            migrateLocked(c);
        } catch (SQLException | RuntimeException e) {
            failure = e;
            throw e;
        } finally {
            try {
                releaseLock(c);
            } catch (SQLException releaseError) {
                // Don't hide the original error
                if (failure == null) throw releaseError;
                failure.addSuppressed(releaseError);
            }
        }
    }

    private void migrateLocked(Connection c) throws SQLException {
        createHistoryTable(c);

        int current = getCurrentVersion(c);
        if (current > getLatestVersion()) {
            throw new SQLException(String.format("The database schema is on version %d, but this version of the plugin " +
                    "only supports up to version %d. Update the plugin, or restore a database backup from before the update.",
                    current, getLatestVersion()));
        }

        for (Migration m : migrations) {
            if (m.version > current) {
                apply(c, m);
            }
        }
    }

    /**
     * Gets the name of the MySQL lock held while migrating. MySQL limits lock names to 64 characters.
     * The prefix fits, as it must leave room for "_discordmcusers" within MySQL's 64 character table name limit.
     *
     * @return the lock name
     */
    private String getLockName() {
        return prefix + "_migrate";
    }

    private void acquireLock(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT GET_LOCK(?, ?)")) {
            ps.setString(1, getLockName());
            ps.setInt(2, LOCK_TIMEOUT_SECONDS);

            try (ResultSet res = ps.executeQuery()) {
                // 1 if the lock was acquired, 0 on timeout, NULL on error
                if (!res.next() || res.getInt(1) != 1) {
                    throw new SQLException(String.format("Timed out after %d seconds waiting for another server to " +
                            "finish migrating the database.", LOCK_TIMEOUT_SECONDS));
                }
            }
        }
    }

    private void releaseLock(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            ps.setString(1, getLockName());
            ps.executeQuery().close();
        }
    }

    /**
     * Gets the current schema version of the database.
     *
     * @param c the connection to the database
     * @return the version of the latest applied migration, or 0 if none were applied
     * @throws SQLException if an SQL error occurs
     */
    @SuppressFBWarnings(value = "SQL_INJECTION_JDBC", justification = "The table prefix is validated by ConfigLinter. All other values use parameters")
    int getCurrentVersion(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT MAX(version) FROM " + getHistoryTableName());
             ResultSet res = ps.executeQuery()) {
            return res.next() ? res.getInt(1) : 0;
        }
    }

    private String getHistoryTableName() {
        return prefix + "_schema_history";
    }

    @SuppressFBWarnings(value = "SQL_INJECTION_JDBC", justification = "The table prefix is validated by ConfigLinter. All other values use parameters")
    private void createHistoryTable(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "CREATE TABLE IF NOT EXISTS `" + getHistoryTableName() + "` ("
                        + "`version` INT NOT NULL,"
                        + "`name` varchar(255) NOT NULL,"
                        + "`applied_at` BIGINT NOT NULL,"
                        + "PRIMARY KEY (`version`));"
        )) {
            ps.execute();
        }
    }

    @SuppressFBWarnings(value = "SQL_INJECTION_JDBC", justification = "Migrations are SQL files bundled in the .jar. The table prefix is validated by ConfigLinter")
    private void apply(Connection c, Migration m) throws SQLException {
        List<String> statements = parseStatements(readMigration(m.name));
        logger.info("Applying database migration " + m.name);

        boolean autoCommit = c.getAutoCommit();
        c.setAutoCommit(false);
        SQLException failure = null;
        try {
            try (Statement st = c.createStatement()) {
                for (String sql : statements) {
                    execute(st, m, sql);
                }
            }

            try (PreparedStatement ps = c.prepareStatement("INSERT INTO " + getHistoryTableName()
                    + " (version, name, applied_at) VALUES (?, ?, ?)")) {
                ps.setInt(1, m.version);
                ps.setString(2, m.name);
                ps.setLong(3, System.currentTimeMillis());
                ps.execute();
            }

            c.commit();
        } catch (SQLException e) {
            failure = new SQLException("Database migration " + m.name + " failed: " + e.getMessage(), e);
            try {
                c.rollback();
            } catch (SQLException rollbackError) {
                failure.addSuppressed(rollbackError);
            }

            throw failure;
        } finally {
            try {
                c.setAutoCommit(autoCommit);
            } catch (SQLException restoreError) {
                // Don't hide the original error
                if (failure == null) throw restoreError;
                failure.addSuppressed(restoreError);
            }
        }
    }

    /**
     * Executes a statement from a migration. On MySQL, statements that fail because they were
     * already applied are skipped. Schema changes can't be rolled back there, so a migration
     * that failed halfway would otherwise fail again on every startup.
     *
     * @param st  the statement to execute it with
     * @param m   the migration it is from
     * @param sql the SQL to execute
     * @throws SQLException if an SQL error occurs
     */
    @SuppressFBWarnings(value = "SQL_INJECTION_JDBC", justification = "Migrations are SQL files bundled in the .jar. The table prefix is validated by ConfigLinter")
    private void execute(Statement st, Migration m, String sql) throws SQLException {
        try {
            st.execute(sql);
        } catch (SQLException e) {
            if (dialect != Dialect.MYSQL || !MYSQL_ALREADY_APPLIED_ERRORS.contains(e.getErrorCode())) {
                throw e;
            }

            logger.warning(String.format("Skipping statement in database migration %s, as it was already applied: %s",
                    m.name, e.getMessage()));
        }
    }

    /**
     * Reads a migration file, preferring the dialect-specific version.
     *
     * @param name the name of the migration
     * @return the contents of the file
     */
    String readMigration(String name) {
        String[] candidates = {
                String.format("/%s/%s.%s.sql", resourceDir, name, dialect.getFileSuffix()),
                String.format("/%s/%s.sql", resourceDir, name),
        };

        for (String path : candidates) {
            try (InputStream stream = DatabaseMigrator.class.getResourceAsStream(path)) {
                if (stream != null) {
                    return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                }
            } catch (IOException e) {
                throw new IllegalStateException("Error reading resource " + path + " from the .jar: " + e.getMessage());
            }
        }

        throw new IllegalStateException("No file found for database migration " + name);
    }

    /**
     * Splits a migration file into statements, removing comments and replacing ${prefix}.
     *
     * @param sql the contents of the file
     * @return the statements to execute
     */
    List<String> parseStatements(String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;

        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);

            if (quote != 0) {
                // Inside a string or quoted name. A doubled quote closes and reopens it.
                if (ch == quote) quote = 0;
                current.append(ch);
            } else if (ch == '\'' || ch == '"' || ch == '`') {
                quote = ch;
                current.append(ch);
            } else if (ch == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-') {
                // Skip the comment, keeping the line break
                while (i + 1 < sql.length() && sql.charAt(i + 1) != '\n') i++;
            } else if (ch == ';') {
                addStatement(statements, current);
            } else {
                current.append(ch);
            }
        }

        if (quote != 0) {
            throw new IllegalArgumentException("Unterminated quote " + quote + " in migration");
        }

        addStatement(statements, current);
        return statements;
    }

    private void addStatement(List<String> statements, StringBuilder statement) {
        String trimmed = statement.toString().trim();
        if (!trimmed.isEmpty()) {
            statements.add(trimmed.replace("${prefix}", prefix));
        }

        statement.setLength(0);
    }

    private static final class Migration {
        final int version;
        final String name;

        Migration(int version, String name) {
            this.version = version;
            this.name = name;
        }
    }
}
