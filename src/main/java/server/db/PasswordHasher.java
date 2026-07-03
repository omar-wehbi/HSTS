package server.db;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Password hashing for the Data tier (Person 1).
 *
 * <p>Passwords are stored as SHA-256 hashes (lowercase hex, matching MySQL's
 * {@code SHA2(x, 256)}), never as plaintext — a leaked database does not expose
 * user passwords. The typed password is hashed here on the server before the
 * DAO compares it against the stored hash.
 *
 * <p>Production note: a real deployment would use a salted, slow KDF
 * (bcrypt / PBKDF2); plain SHA-256 is the course-prototype trade-off.
 */
public final class PasswordHasher {

    private PasswordHasher() { }

    /** @return the SHA-256 hash of {@code plaintext} as lowercase hex. */
    public static String sha256(String plaintext) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(plaintext.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandatory in every JRE; this cannot happen.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
