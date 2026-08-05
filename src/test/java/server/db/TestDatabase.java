package server.db;

import server.config.DbSettings;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Builds a throw-away MySQL schema for the test suite so destructive fixtures
 * never touch the demo database {@code hsts_a3_db}.
 */
public final class TestDatabase {

    private static final String SEED_RESOURCE = "/seed_test_scenarios.sql";
    private static final String SEED_DB_NAME = "hsts_a3_db";
    private static boolean ready;

    private TestDatabase() { }

    /** Builds the test schema once per JVM from the team's own seed script. */
    public static synchronized void ensureReady() {
        if (ready) return;
        DbSettings s = DatabaseConfig.getSettings();
        requireTestDatabase();

        String script;
        try (InputStream in = TestDatabase.class.getResourceAsStream(SEED_RESOURCE)) {
            if (in == null) throw new IllegalStateException("missing " + SEED_RESOURCE);
            script = new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replace(SEED_DB_NAME, s.database());
        } catch (Exception e) {
            throw new IllegalStateException("cannot read " + SEED_RESOURCE, e);
        }

        // connect with NO database selected — the script's CREATE DATABASE runs first
        String url = "jdbc:mysql://" + s.host() + ":" + s.port()
                + "/?allowMultiQueries=true&serverTimezone=UTC"
                + "&allowPublicKeyRetrieval=true&useSSL=false";
        try (Connection c = DriverManager.getConnection(url, s.user(), s.password());
             Statement st = c.createStatement()) {
            // Recreate from scratch so schema upgrades in the seed (e.g. Courses.subject_id)
            // always apply — CREATE TABLE IF NOT EXISTS would keep a stale table.
            st.execute("DROP DATABASE IF EXISTS `" + s.database() + "`");
            st.execute(script);
            while (st.getMoreResults() || st.getUpdateCount() != -1) { /* drain */ }
        } catch (SQLException e) {
            throw new IllegalStateException("could not build test database "
                    + s.database() + ": " + e.getMessage(), e);
        }
        ready = true;
        System.out.println("[TestDatabase] '" + s.database() + "' ready (demo DB untouched)");
    }

    /** Refuses to let a destructive fixture run anywhere but a *_test schema. */
    public static void requireTestDatabase() {
        String db = DatabaseConfig.getSettings().database();
        if (!db.endsWith("_test")) {
            throw new IllegalStateException("Refusing to run destructive fixtures against '"
                    + db + "'. Run through Maven so -Dhsts.db.name=hsts_a3_test is set.");
        }
    }
}
