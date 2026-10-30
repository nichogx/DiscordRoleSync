package dev.nicho.rolesync.db.migrations;

/**
 * The SQL dialect of a database. Used to pick dialect-specific migration files.
 */
public enum Dialect {
    MYSQL("mysql"),
    SQLITE("sqlite");

    private final String fileSuffix;

    Dialect(String fileSuffix) {
        this.fileSuffix = fileSuffix;
    }

    /**
     * @return the suffix of migration files specific to this dialect, e.g. "mysql" for 001_baseline.mysql.sql
     */
    public String getFileSuffix() {
        return fileSuffix;
    }
}
