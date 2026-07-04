package server.config;

/**
 * Immutable connection profile for the HSTS database (Person 2, Phase 0.5).
 *
 * <p>The single source of truth every database connection is built from — both
 * plain JDBC ({@link server.db.DatabaseConfig#getConnection()}) and Hibernate
 * ({@code HibernateUtil}) derive their URL and credentials from one instance of
 * this record, so the server can point at <em>any</em> MySQL (a teammate's
 * machine, the lab PC at the defense) without touching code.
 *
 * <p><b>Pattern: Value Object</b> — immutable, validated at construction,
 * compared by value. Invalid settings are impossible to construct, so code that
 * receives a {@code DbSettings} never re-validates.
 *
 * @param host     MySQL host name or IP (trimmed, never blank)
 * @param port     TCP port, 1..65535
 * @param database schema name (trimmed, never blank)
 * @param user     MySQL user (trimmed, never blank)
 * @param password MySQL password; may be empty (passwordless local root), never null
 */
public record DbSettings(String host, int port, String database, String user, String password) {

    /** Extra JDBC params: TLS off for LAN dev, sane timezone handling. */
    private static final String PARAMS =
            "useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

    public DbSettings {
        host = requireNonBlank(host, "host");
        database = requireNonBlank(database, "database");
        user = requireNonBlank(user, "user");
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be 1..65535, got: " + port);
        }
        password = (password == null) ? "" : password;
    }

    /** The prototype's local setup: {@code localhost:3306/hsts_a3_db}, root/root. */
    public static DbSettings defaults() {
        return new DbSettings("localhost", 3306, "hsts_a3_db", "root", "root");
    }

    /** @return the full JDBC URL for these settings (driver params included). */
    public String jdbcUrl() {
        return "jdbc:mysql://" + host + ":" + port + "/" + database + "?" + PARAMS;
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    /** Password is masked — this record ends up in logs via the startup banner. */
    @Override
    public String toString() {
        return "DbSettings[" + user + "@" + host + ":" + port + "/" + database + ", password=***]";
    }
}
