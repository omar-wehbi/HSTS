package server.db;

import server.config.DbSettings;
import server.config.ServerConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Central JDBC configuration for the Data tier.
 *
 * <p>Until Phase 0.5 the host/port/database were compile-time constants, so the
 * server could only ever talk to one specific local setup. Now the active
 * {@link DbSettings} is resolved at runtime, strongest source first:
 * <ol>
 *   <li>{@link #configure(DbSettings)} — the startup connection dialog
 *       ({@code ServerConnectionDialog}) or a test calling it directly</li>
 *   <li>system properties {@code hsts.db.*} / {@code server.properties} /
 *       built-in defaults — via {@link ServerConfig#loadSettings()}</li>
 * </ol>
 *
 * <p>Both JDBC ({@link #getConnection()}) and Hibernate ({@code HibernateUtil})
 * read {@link #getSettings()}, so the whole Data tier always points at the same
 * database. <b>Order matters at startup:</b> call {@code configure(...)} before
 * the first {@code getConnection()}/SessionFactory boot (i.e. before
 * {@code new HSTSServer(...)}, whose constructor fail-fast boots the ORM).
 */
public final class DatabaseConfig {

    /** Dialog/test-provided settings; null → fall back to ServerConfig resolution. */
    private static volatile DbSettings configured;

    private DatabaseConfig() {
        // utility class — no instances
    }

    /**
     * Pins the connection profile for the rest of the server's lifetime
     * (used by the startup connection dialog before the server boots).
     */
    public static void configure(DbSettings settings) {
        if (settings == null) {
            throw new NullPointerException("settings must not be null");
        }
        configured = settings;
        System.out.println("[DatabaseConfig] Using " + settings);
    }

    /** @return the active connection profile (dialog-configured, else resolved). */
    public static DbSettings getSettings() {
        DbSettings s = configured;
        return (s != null) ? s : ServerConfig.loadSettings();
    }

    /**
     * Opens a new JDBC connection to the HSTS database.
     *
     * @return a live {@link Connection}; caller is responsible for closing it.
     */
    public static Connection getConnection() throws SQLException {
        DbSettings s = getSettings();
        return DriverManager.getConnection(s.jdbcUrl(), s.user(), s.password());
    }

    /** Clears dialog/test settings so tests never leak state into each other. */
    static void resetForTests() {
        configured = null;
    }
}
