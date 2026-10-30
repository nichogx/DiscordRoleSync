package dev.nicho.rolesync.db.migrations;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseMigratorTest {

    private static final Logger LOGGER = Logger.getLogger("DatabaseMigratorTest");
    private static final String TEST_DIR = "db/test_migrations";

    private Connection c;

    @BeforeEach
    void setUp() throws SQLException {
        c = DriverManager.getConnection("jdbc:sqlite::memory:");
    }

    @AfterEach
    void tearDown() throws SQLException {
        c.close();
    }

    private DatabaseMigrator testMigrator(String... names) {
        return new DatabaseMigrator(LOGGER, Dialect.SQLITE, "drs", TEST_DIR, List.of(names));
    }

    private int count(String sql) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet res = ps.executeQuery()) {
            res.next();
            return res.getInt(1);
        }
    }

    @Test
    void testFreshDatabase() throws SQLException {
        DatabaseMigrator migrator = new DatabaseMigrator(LOGGER, Dialect.SQLITE, "drs");
        migrator.migrate(c);

        assertEquals(migrator.getLatestVersion(), migrator.getCurrentVersion(c));
        assertEquals(0, count("SELECT COUNT(*) FROM drs_discordmcusers"));
    }

    @Test
    void testAdoptsDatabaseFromBeforeMigrations() throws SQLException {
        // The table as created by older plugin versions
        try (PreparedStatement ps = c.prepareStatement("CREATE TABLE IF NOT EXISTS `drs_discordmcusers` ("
                + "`discord_id` varchar(24) NOT NULL,"
                + "`minecraft_uuid` varchar(36) NOT NULL,"
                + "`whitelisted` boolean NOT NULL DEFAULT false,"
                + "`verification_code` INT NOT NULL DEFAULT 0,"
                + "`verified` boolean NOT NULL DEFAULT false,"
                + "`username_when_linked` TEXT NULL,"
                + "PRIMARY KEY (`discord_id`));")) {
            ps.execute();
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO drs_discordmcusers (discord_id, minecraft_uuid) VALUES ('123', 'uuid')")) {
            ps.execute();
        }

        DatabaseMigrator migrator = new DatabaseMigrator(LOGGER, Dialect.SQLITE, "drs");
        migrator.migrate(c);

        assertEquals(migrator.getLatestVersion(), migrator.getCurrentVersion(c));
        assertEquals(1, count("SELECT COUNT(*) FROM drs_discordmcusers"));
    }

    @Test
    void testMigratingTwiceIsNoOp() throws SQLException {
        DatabaseMigrator migrator = testMigrator("001_create", "002_insert");
        migrator.migrate(c);
        migrator.migrate(c);

        assertEquals(2, migrator.getCurrentVersion(c));
        assertEquals(2, count("SELECT COUNT(*) FROM drs_items"));
        assertEquals(2, count("SELECT COUNT(*) FROM drs_schema_history"));
    }

    @Test
    void testAppliesOnlyPendingMigrations() throws SQLException {
        testMigrator("001_create").migrate(c);
        assertEquals(0, count("SELECT COUNT(*) FROM drs_items"));

        testMigrator("001_create", "002_insert").migrate(c);
        assertEquals(2, count("SELECT COUNT(*) FROM drs_items"));
    }

    @Test
    void testPrefersDialectSpecificFile() throws SQLException {
        testMigrator("001_create").migrate(c);

        // 001_create.sqlite.sql creates drs_items, 001_create.sql would create drs_mysql_only
        assertEquals(1, count("SELECT COUNT(*) FROM sqlite_master WHERE name = 'drs_items'"));
        assertEquals(0, count("SELECT COUNT(*) FROM sqlite_master WHERE name = 'drs_mysql_only'"));
    }

    @Test
    void testFallsBackToSharedFile() {
        DatabaseMigrator migrator = new DatabaseMigrator(LOGGER, Dialect.MYSQL, "drs", TEST_DIR, List.of("001_create"));

        assertTrue(migrator.readMigration("001_create").contains("mysql_only"));
    }

    /**
     * Mocks a MySQL connection where GET_LOCK returns lockResult, and every other query fails.
     */
    private Connection mockMySQL(int lockResult) throws SQLException {
        return mockMySQL(lockResult, false);
    }

    /**
     * Mocks a MySQL connection where GET_LOCK returns lockResult, and every other query fails.
     * RELEASE_LOCK also fails if releaseFails is true.
     */
    private Connection mockMySQL(int lockResult, boolean releaseFails) throws SQLException {
        Connection mysql = Mockito.mock(Connection.class);
        Mockito.when(mysql.prepareStatement(Mockito.anyString())).thenAnswer(inv -> {
            String sql = inv.getArgument(0);
            if (sql.startsWith("SELECT RELEASE_LOCK") && releaseFails) {
                throw new SQLException("Release failed");
            }

            if (!sql.startsWith("SELECT GET_LOCK") && !sql.startsWith("SELECT RELEASE_LOCK")) {
                throw new SQLException("Query failed");
            }

            ResultSet res = Mockito.mock(ResultSet.class);
            Mockito.when(res.next()).thenReturn(true);
            Mockito.when(res.getInt(1)).thenReturn(lockResult);

            PreparedStatement ps = Mockito.mock(PreparedStatement.class);
            Mockito.when(ps.executeQuery()).thenReturn(res);
            return ps;
        });

        return mysql;
    }

    @Test
    void testMySQLReleasesLockOnFailure() throws SQLException {
        Connection mysql = mockMySQL(1);
        DatabaseMigrator migrator = new DatabaseMigrator(LOGGER, Dialect.MYSQL, "drs");

        SQLException e = assertThrows(SQLException.class, () -> migrator.migrate(mysql));
        assertEquals("Query failed", e.getMessage());

        InOrder order = Mockito.inOrder(mysql);
        order.verify(mysql).prepareStatement("SELECT GET_LOCK(?, ?)");
        order.verify(mysql).prepareStatement(Mockito.startsWith("CREATE TABLE IF NOT EXISTS `drs_schema_history`"));
        order.verify(mysql).prepareStatement("SELECT RELEASE_LOCK(?)");
    }

    @Test
    void testMySQLReleaseFailureKeepsOriginalError() throws SQLException {
        Connection mysql = mockMySQL(1, true);
        DatabaseMigrator migrator = new DatabaseMigrator(LOGGER, Dialect.MYSQL, "drs");

        SQLException e = assertThrows(SQLException.class, () -> migrator.migrate(mysql));
        assertEquals("Query failed", e.getMessage());
        assertEquals("Release failed", e.getSuppressed()[0].getMessage());
    }

    @Test
    void testRollbackFailureKeepsOriginalError() throws SQLException {
        Connection spy = Mockito.spy(c);
        Mockito.doThrow(new SQLException("Rollback failed")).when(spy).rollback();

        DatabaseMigrator migrator = testMigrator("001_create", "002_insert", "003_broken");
        SQLException e = assertThrows(SQLException.class, () -> migrator.migrate(spy));
        assertTrue(e.getMessage().contains("003_broken"));
        assertEquals("Rollback failed", e.getSuppressed()[0].getMessage());
        assertTrue(c.getAutoCommit());
    }

    /**
     * Mocks a MySQL connection on schema version 0, where every migration statement fails with errorCode.
     */
    private Connection mockMySQLStatementError(int errorCode) throws SQLException {
        Connection mysql = Mockito.mock(Connection.class);
        Mockito.when(mysql.getAutoCommit()).thenReturn(true);
        Mockito.when(mysql.prepareStatement(Mockito.anyString())).thenAnswer(inv -> {
            String sql = inv.getArgument(0);

            ResultSet res = Mockito.mock(ResultSet.class);
            Mockito.when(res.next()).thenReturn(true);
            // GET_LOCK succeeds, MAX(version) is 0
            Mockito.when(res.getInt(1)).thenReturn(sql.startsWith("SELECT GET_LOCK") ? 1 : 0);

            PreparedStatement ps = Mockito.mock(PreparedStatement.class);
            Mockito.when(ps.executeQuery()).thenReturn(res);
            return ps;
        });

        Statement st = Mockito.mock(Statement.class);
        Mockito.when(st.execute(Mockito.anyString())).thenThrow(new SQLException("Statement failed", "42000", errorCode));
        Mockito.when(mysql.createStatement()).thenReturn(st);

        return mysql;
    }

    @Test
    void testMySQLSkipsAlreadyAppliedStatements() throws SQLException {
        // e.g. a retried migration whose CREATE TABLE was committed before it failed
        Connection mysql = mockMySQLStatementError(1050);
        new DatabaseMigrator(LOGGER, Dialect.MYSQL, "drs", TEST_DIR, List.of("001_create")).migrate(mysql);

        Mockito.verify(mysql).prepareStatement(Mockito.startsWith("INSERT INTO drs_schema_history"));
        Mockito.verify(mysql).commit();
        Mockito.verify(mysql, Mockito.never()).rollback();
    }

    @Test
    void testMySQLFailsOnOtherErrors() throws SQLException {
        // Syntax error
        Connection mysql = mockMySQLStatementError(1064);
        DatabaseMigrator migrator = new DatabaseMigrator(LOGGER, Dialect.MYSQL, "drs", TEST_DIR, List.of("001_create"));

        SQLException e = assertThrows(SQLException.class, () -> migrator.migrate(mysql));
        assertTrue(e.getMessage().contains("001_create"));
        Mockito.verify(mysql).rollback();
        Mockito.verify(mysql, Mockito.never()).commit();
    }

    @Test
    void testSQLiteDoesNotSkipAlreadyAppliedStatements() throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("CREATE TABLE drs_items (id INT)")) {
            ps.execute();
        }

        // SQLite rolls back failed migrations, so an existing table is a real error
        assertThrows(SQLException.class, () -> testMigrator("001_create").migrate(c));
        assertEquals(0, testMigrator("001_create").getCurrentVersion(c));
    }

    @Test
    void testMySQLLockTimeout() throws SQLException {
        Connection mysql = mockMySQL(0);
        DatabaseMigrator migrator = new DatabaseMigrator(LOGGER, Dialect.MYSQL, "drs");

        SQLException e = assertThrows(SQLException.class, () -> migrator.migrate(mysql));
        assertTrue(e.getMessage().contains("Timed out"));

        // Nothing else runs without the lock
        Mockito.verify(mysql, Mockito.times(1)).prepareStatement(Mockito.anyString());
    }

    @Test
    void testFailedMigrationIsRolledBack() throws SQLException {
        DatabaseMigrator migrator = testMigrator("001_create", "002_insert", "003_broken");
        SQLException e = assertThrows(SQLException.class, () -> migrator.migrate(c));
        assertTrue(e.getMessage().contains("003_broken"));

        // 001 and 002 stay applied, the first statement of 003 is rolled back
        assertEquals(2, migrator.getCurrentVersion(c));
        assertEquals(0, count("SELECT COUNT(*) FROM drs_items WHERE id = 3"));
        assertTrue(c.getAutoCommit());
    }

    @Test
    void testRejectsNewerDatabase() throws SQLException {
        testMigrator("001_create", "002_insert").migrate(c);

        SQLException e = assertThrows(SQLException.class, () -> testMigrator("001_create").migrate(c));
        assertTrue(e.getMessage().contains("version 2"));
    }

    @Test
    void testRejectsInvalidMigrationLists() {
        assertThrows(IllegalArgumentException.class, () -> testMigrator());
        assertThrows(IllegalArgumentException.class, () -> testMigrator("001_create", "003_broken"));
        assertThrows(IllegalArgumentException.class, () -> testMigrator("002_insert", "001_create"));
        assertThrows(IllegalArgumentException.class, () -> testMigrator("create"));
    }

    @Test
    void testMissingFile() {
        assertThrows(IllegalStateException.class, () -> testMigrator("001_missing").migrate(c));
    }

    @Test
    void testParseStatements() {
        List<String> statements = testMigrator("001_create").parseStatements(
                "-- comment\nCREATE TABLE ${prefix}_a (id INT);\n\n  -- another\nDROP TABLE ${prefix}_b;\n");

        assertEquals(List.of("CREATE TABLE drs_a (id INT)", "DROP TABLE drs_b"), statements);
    }

    @Test
    void testParseTrailingComments() {
        List<String> statements = testMigrator("001_create").parseStatements(
                "CREATE TABLE a (id INT); -- trailing; comment\r\nDROP TABLE b; -- last\r\n");

        assertEquals(List.of("CREATE TABLE a (id INT)", "DROP TABLE b"), statements);
    }

    @Test
    void testParseQuotes() {
        List<String> statements = testMigrator("001_create").parseStatements(
                "INSERT INTO `a;b` VALUES ('it''s; -- not a comment', \"x;y\");\nDROP TABLE c;");

        assertEquals(List.of("INSERT INTO `a;b` VALUES ('it''s; -- not a comment', \"x;y\")", "DROP TABLE c"), statements);
    }

    @Test
    void testParseUnterminatedQuote() {
        assertThrows(IllegalArgumentException.class,
                () -> testMigrator("001_create").parseStatements("INSERT INTO a VALUES ('oops);"));
    }

    @Test
    void testAllMigrationsHaveFilesForEveryDialect() {
        for (Dialect dialect : Dialect.values()) {
            DatabaseMigrator migrator = new DatabaseMigrator(LOGGER, dialect, "drs");
            for (String name : DatabaseMigrator.MIGRATIONS) {
                assertFalse(migrator.parseStatements(migrator.readMigration(name)).isEmpty(),
                        name + " has no statements for " + dialect);
            }
        }
    }

    @Test
    void testAllMigrationFilesAreRegistered() {
        // Tests run from the project root
        File dir = new File("src/main/resources/db/migrations");
        Set<String> files = Arrays.stream(Objects.requireNonNull(dir.listFiles()))
                .map(f -> f.getName().replaceAll("(\\.(mysql|sqlite))?\\.sql$", ""))
                .collect(Collectors.toSet());

        assertEquals(Set.copyOf(DatabaseMigrator.MIGRATIONS), files);
    }
}
