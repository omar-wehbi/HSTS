package server.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Tests for {@link DbSettings} (Person 2, Phase 0.5 — DB connection window).
 *
 * <p>Pure unit tests: no database, no I/O. Locks down the value object every
 * connection in the system (JDBC <em>and</em> Hibernate) is built from, so a
 * regression here would break the server on every machine at once.
 */
class DbSettingsTest {

    // ===== defaults =======================================================

    @Test
    void defaultsMatchThePrototypeSetup() {
        DbSettings d = DbSettings.defaults();
        assertThat(d.host()).isEqualTo("localhost");
        assertThat(d.port()).isEqualTo(3306);
        assertThat(d.database()).isEqualTo("hsts_a3_db");
        assertThat(d.user()).isEqualTo("root");
        assertThat(d.password()).isEqualTo("root");
    }

    // ===== JDBC URL building ==============================================

    @Test
    void jdbcUrlContainsHostPortAndDatabase() {
        DbSettings s = new DbSettings("db.school.org", 3307, "exams", "naji", "secret");
        assertThat(s.jdbcUrl()).startsWith("jdbc:mysql://db.school.org:3307/exams?");
    }

    @Test
    void jdbcUrlKeepsTheLocalDevParams() {
        // TLS off for LAN dev + explicit UTC — same params the prototype used.
        String url = DbSettings.defaults().jdbcUrl();
        assertThat(url)
                .contains("useSSL=false")
                .contains("allowPublicKeyRetrieval=true")
                .contains("serverTimezone=UTC");
    }

    // ===== validation =====================================================

    @Test
    void blankHostIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DbSettings(" ", 3306, "db", "root", "pw"))
                .withMessageContaining("host");
    }

    @Test
    void blankDatabaseIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DbSettings("localhost", 3306, "", "root", "pw"))
                .withMessageContaining("database");
    }

    @Test
    void blankUserIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DbSettings("localhost", 3306, "db", "", "pw"))
                .withMessageContaining("user");
    }

    @Test
    void portMustBeAValidTcpPort() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DbSettings("localhost", 0, "db", "root", "pw"))
                .withMessageContaining("port");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new DbSettings("localhost", 65536, "db", "root", "pw"))
                .withMessageContaining("port");
    }

    @Test
    void emptyPasswordIsAllowed() {
        // Some local MySQL installs use a passwordless root — must not be rejected.
        DbSettings s = new DbSettings("localhost", 3306, "db", "root", "");
        assertThat(s.password()).isEmpty();
    }

    @Test
    void nullPasswordBecomesEmpty() {
        DbSettings s = new DbSettings("localhost", 3306, "db", "root", null);
        assertThat(s.password()).isEmpty();
    }

    @Test
    void hostDatabaseAndUserAreTrimmed() {
        DbSettings s = new DbSettings(" localhost ", 3306, " db ", " root ", "pw");
        assertThat(s.host()).isEqualTo("localhost");
        assertThat(s.database()).isEqualTo("db");
        assertThat(s.user()).isEqualTo("root");
    }
}
