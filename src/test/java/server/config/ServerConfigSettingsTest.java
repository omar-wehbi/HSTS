package server.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the {@link ServerConfig} settings-resolution logic
 * (Person 2, Phase 0.5 — DB connection window).
 *
 * <p>{@link ServerConfig#resolve(Properties, Properties)} is a pure function
 * (no file system, no statics), so precedence is tested without touching the
 * real {@code server.properties}. Precedence per key, strongest first:
 * <ol>
 *   <li>system property {@code hsts.db.*} (CI / tests / scripted runs)</li>
 *   <li>{@code server.properties} key {@code db.*}</li>
 *   <li>built-in default (the prototype's localhost setup)</li>
 * </ol>
 */
class ServerConfigSettingsTest {

    private static final Properties EMPTY = new Properties();

    private static Properties props(String... kv) {
        Properties p = new Properties();
        for (int i = 0; i < kv.length; i += 2) p.setProperty(kv[i], kv[i + 1]);
        return p;
    }

    // ===== precedence =====================================================

    @Test
    void noSourcesMeansDefaults() {
        assertThat(ServerConfig.resolve(EMPTY, EMPTY)).isEqualTo(DbSettings.defaults());
    }

    @Test
    void fileValuesOverrideDefaults() {
        Properties file = props(
                "db.host", "192.168.1.20",
                "db.port", "3307",
                "db.name", "hsts_demo",
                "db.user", "amjad",
                "db.password", "s3cret");
        DbSettings s = ServerConfig.resolve(file, EMPTY);
        assertThat(s).isEqualTo(new DbSettings("192.168.1.20", 3307, "hsts_demo", "amjad", "s3cret"));
    }

    @Test
    void systemPropertiesOverrideFileValues() {
        Properties file = props("db.host", "from-file", "db.user", "file-user");
        Properties sys  = props("hsts.db.host", "from-sysprop");
        DbSettings s = ServerConfig.resolve(file, sys);
        assertThat(s.host()).isEqualTo("from-sysprop");   // sysprop wins
        assertThat(s.user()).isEqualTo("file-user");      // untouched keys still come from the file
    }

    @Test
    void legacyFileWithOnlyUserAndPasswordStillWorks() {
        // Amjad's current server.properties has just db.user/db.password —
        // host/port/name must quietly fall back to defaults.
        Properties file = props("db.user", "root", "db.password", "root");
        DbSettings s = ServerConfig.resolve(file, EMPTY);
        assertThat(s.host()).isEqualTo("localhost");
        assertThat(s.port()).isEqualTo(3306);
        assertThat(s.database()).isEqualTo("hsts_a3_db");
    }

    // ===== robustness =====================================================

    @Test
    void invalidPortInFileFallsBackToDefaultInsteadOfCrashing() {
        Properties file = props("db.port", "not-a-number");
        assertThat(ServerConfig.resolve(file, EMPTY).port()).isEqualTo(3306);
    }

    @Test
    void invalidPortInSyspropFallsBackToFileValue() {
        Properties file = props("db.port", "3311");
        Properties sys  = props("hsts.db.port", "zzz");
        assertThat(ServerConfig.resolve(file, sys).port()).isEqualTo(3311);
    }

    @Test
    void blankFileValuesAreIgnored() {
        Properties file = props("db.host", "   ", "db.name", "");
        DbSettings s = ServerConfig.resolve(file, EMPTY);
        assertThat(s.host()).isEqualTo("localhost");
        assertThat(s.database()).isEqualTo("hsts_a3_db");
    }

    // ===== save / load round-trip (dialog's "save as defaults") ==========

    @Test
    void savedSettingsLoadBackIdentically(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("server.properties");
        DbSettings original = new DbSettings("10.0.0.7", 3310, "hsts_lab", "demo", "pw123");

        ServerConfig.save(original, file);

        Properties reloaded = new Properties();
        try (var in = Files.newInputStream(file)) { reloaded.load(in); }
        assertThat(ServerConfig.resolve(reloaded, EMPTY)).isEqualTo(original);
    }

    @Test
    void saveWritesAllFiveKeys(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("server.properties");
        ServerConfig.save(DbSettings.defaults(), file);
        String text = Files.readString(file);
        assertThat(text)
                .contains("db.host=").contains("db.port=").contains("db.name=")
                .contains("db.user=").contains("db.password=");
    }

    @Test
    void savePathIsTheExternalServerPropertiesLocation() {
        // Where the dialog's "save as defaults" writes: beside the JAR / project root.
        assertThat(ServerConfig.externalConfigPath().getFileName().toString())
                .isEqualTo("server.properties");
    }

    // ===== legacy credentials API (Person 1) ==============================

    @Test
    void legacyCredentialsLoaderStillWorks() {
        // ServerConfig.load() predates DbSettings and has no callers left in
        // main code since Phase 0.5, but it is Person 1's public API — keep it
        // working until the team agrees to remove it.
        ServerConfig.Credentials creds = ServerConfig.load();
        assertThat(creds.user()).isNotBlank();
        assertThat(creds.password()).isNotNull();
    }
}
