package server.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Loads server-side settings from {@code server.properties}.
 *
 * <p>Lookup order for the file itself:
 * <ol>
 *   <li>File beside the running JAR (or project root when launched from the IDE)</li>
 *   <li>Classpath resource {@code /server.properties} (bundled default)</li>
 *   <li>Hard-coded fallback ({@code root} / {@code root})</li>
 * </ol>
 *
 * <p>Since Phase 0.5 (Person 2) the whole connection profile is configurable, not
 * just the credentials. Precedence <em>per key</em>, strongest first:
 * <ol>
 *   <li>system property {@code hsts.db.*} (CI, tests, scripted runs)</li>
 *   <li>{@code server.properties} key {@code db.*}</li>
 *   <li>built-in default — the prototype's {@code localhost:3306/hsts_a3_db}</li>
 * </ol>
 * The startup connection dialog ({@code ServerConnectionDialog}) sits on top of
 * all three via {@link server.db.DatabaseConfig#configure(DbSettings)}.
 *
 * <p>Recognised keys: {@code db.host}, {@code db.port}, {@code db.name},
 * {@code db.user}, {@code db.password}. A file with only user/password (the
 * pre-Phase-0.5 format) keeps working — missing keys fall back to defaults.
 */
public final class ServerConfig {

    private static final String CONFIG_FILE = "server.properties";
    private static final String KEY_HOST = "db.host";
    private static final String KEY_PORT = "db.port";
    private static final String KEY_NAME = "db.name";
    private static final String KEY_USER = "db.user";
    private static final String KEY_PASSWORD = "db.password";
    private static final String DEFAULT_USER = "root";
    private static final String DEFAULT_PASSWORD = "root";

    /** Prefix turning a file key into its system-property override (db.host → hsts.db.host). */
    private static final String SYSPROP_PREFIX = "hsts.";

    private ServerConfig() {}

    /** Resolved JDBC credentials for the Data tier. */
    public record Credentials(String user, String password) {}

    public static Credentials load() {
        Properties props = new Properties();
        Path external = resolveExternalConfigPath();

        if (external != null && Files.isRegularFile(external)) {
            loadFromFile(props, external);
            System.out.println("[ServerConfig] Loaded " + CONFIG_FILE + " from " + external.toAbsolutePath());
        } else if (!loadFromClasspath(props)) {
            System.out.println("[ServerConfig] No " + CONFIG_FILE + " found — using default credentials");
        }

        String user = props.getProperty(KEY_USER, DEFAULT_USER).trim();
        String password = props.getProperty(KEY_PASSWORD, DEFAULT_PASSWORD);
        return new Credentials(user, password);
    }

    // ===== full connection profile (Phase 0.5, Person 2) =================

    /**
     * Resolves the complete connection profile: file beside the JAR (or bundled
     * default) merged with {@code hsts.db.*} system-property overrides.
     */
    public static DbSettings loadSettings() {
        Properties props = new Properties();
        Path external = resolveExternalConfigPath();
        if (external != null && Files.isRegularFile(external)) {
            loadFromFile(props, external);
            System.out.println("[ServerConfig] loadSettings from " + external.toAbsolutePath());
        } else if (!loadFromClasspath(props)) {
            System.out.println("[ServerConfig] No " + CONFIG_FILE + " found — using defaults / hsts.db.*");
        }
        return resolve(props, System.getProperties());
    }

    /**
     * Pure per-key merge of the three configuration levels — no I/O, no statics,
     * so precedence is directly unit-testable ({@code ServerConfigSettingsTest}).
     *
     * @param fileProps keys as they appear in {@code server.properties} ({@code db.*})
     * @param sysProps  keys with the {@code hsts.} prefix ({@code hsts.db.*})
     */
    public static DbSettings resolve(Properties fileProps, Properties sysProps) {
        DbSettings def = DbSettings.defaults();
        return new DbSettings(
                pick(sysProps, fileProps, KEY_HOST, def.host()),
                pickPort(sysProps, fileProps, def.port()),
                pick(sysProps, fileProps, KEY_NAME, def.database()),
                pick(sysProps, fileProps, KEY_USER, def.user()),
                pick(sysProps, fileProps, KEY_PASSWORD, def.password()));
    }

    /**
     * Persists a connection profile as {@code server.properties}-style keys —
     * the connection dialog's "save as defaults" action.
     */
    public static void save(DbSettings settings, Path file) throws IOException {
        Properties props = new Properties();
        props.setProperty(KEY_HOST, settings.host());
        props.setProperty(KEY_PORT, String.valueOf(settings.port()));
        props.setProperty(KEY_NAME, settings.database());
        props.setProperty(KEY_USER, settings.user());
        props.setProperty(KEY_PASSWORD, settings.password());
        try (OutputStream out = Files.newOutputStream(file)) {
            props.store(out, "HSTS server database settings (written by the connection dialog)");
        }
    }

    /** @return the path where {@link #save} should write: beside the JAR / project root. */
    public static Path externalConfigPath() {
        return resolveExternalConfigPath();
    }

    /** Strongest non-blank value: sysprop ({@code hsts.}+key) → file (key) → default. */
    private static String pick(Properties sys, Properties file, String key, String def) {
        String v = nonBlank(sys.getProperty(SYSPROP_PREFIX + key));
        if (v == null) v = nonBlank(file.getProperty(key));
        return v == null ? def : v;
    }

    /** Like {@link #pick} but each level must also parse as an int to be used. */
    private static int pickPort(Properties sys, Properties file, int def) {
        Integer v = parseInt(sys.getProperty(SYSPROP_PREFIX + KEY_PORT));
        if (v == null) v = parseInt(file.getProperty(KEY_PORT));
        return v == null ? def : v;
    }

    private static String nonBlank(String s) {
        return (s == null || s.trim().isEmpty()) ? null : s.trim();
    }

    private static Integer parseInt(String s) {
        if (s == null) return null;
        try {
            return Integer.valueOf(s.trim());
        } catch (NumberFormatException e) {
            System.err.println("[ServerConfig] Ignoring invalid db.port value: '" + s + "'");
            return null;
        }
    }

    private static Path resolveExternalConfigPath() {
        try {
            URI codeSource = ServerConfig.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path location = Paths.get(codeSource);
            if (Files.isRegularFile(location)) {
                // Running from a shaded JAR — file beside the JAR.
                return location.getParent().resolve(CONFIG_FILE);
            }
        } catch (Exception ignored) {
            // Fall through to cwd / project-root search (IDE / Maven tests).
        }
        // Prefer an absolute path under user.dir so relative resolution cannot
        // silently miss the file when the JVM cwd is unexpected.
        Path fromCwd = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize()
                .resolve(CONFIG_FILE);
        if (Files.isRegularFile(fromCwd)) {
            return fromCwd;
        }
        return Paths.get(CONFIG_FILE).toAbsolutePath().normalize();
    }

    private static void loadFromFile(Properties props, Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
        } catch (IOException e) {
            System.err.println("[ServerConfig] Could not read " + path + ": " + e.getMessage());
        }
    }

    private static boolean loadFromClasspath(Properties props) {
        try (InputStream in = ServerConfig.class.getResourceAsStream("/" + CONFIG_FILE)) {
            if (in == null) {
                return false;
            }
            props.load(in);
            System.out.println("[ServerConfig] Loaded bundled " + CONFIG_FILE);
            return true;
        } catch (IOException e) {
            System.err.println("[ServerConfig] Could not read bundled " + CONFIG_FILE + ": " + e.getMessage());
            return false;
        }
    }
}
