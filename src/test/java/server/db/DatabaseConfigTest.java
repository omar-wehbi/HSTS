package server.db;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.config.DbSettings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Tests for the runtime-configurable {@link DatabaseConfig}
 * (Person 2, Phase 0.5 — DB connection window).
 *
 * <p>No live database needed: these tests only exercise which {@link DbSettings}
 * the class hands out — dialog-configured settings must beat everything, and
 * clearing them must fall back to {@code server.properties} resolution.
 */
class DatabaseConfigTest {

    @AfterEach
    void cleanUp() {
        DatabaseConfig.resetForTests();   // never leak dialog settings between tests
    }

    @Test
    void configuredSettingsWinOverEverything() {
        DbSettings dialog = new DbSettings("lab-pc", 3399, "hsts_defense", "prof", "pw");
        DatabaseConfig.configure(dialog);
        assertThat(DatabaseConfig.getSettings()).isEqualTo(dialog);
    }

    @Test
    void withoutConfigureFallsBackToServerConfigResolution() {
        // Nothing configured → resolved from server.properties/defaults.
        // The repo's file sets only user/password, so host/port/name are defaults.
        DbSettings s = DatabaseConfig.getSettings();
        assertThat(s.host()).isEqualTo("localhost");
        assertThat(s.port()).isEqualTo(3306);
        assertThat(s.database()).isEqualTo("hsts_a3_db");
    }

    @Test
    void resetForTestsClearsDialogSettings() {
        DatabaseConfig.configure(new DbSettings("elsewhere", 4000, "other", "u", "p"));
        DatabaseConfig.resetForTests();
        assertThat(DatabaseConfig.getSettings().host()).isEqualTo("localhost");
    }

    @Test
    void configureRejectsNull() {
        assertThatNullPointerException().isThrownBy(() -> DatabaseConfig.configure(null));
    }
}
