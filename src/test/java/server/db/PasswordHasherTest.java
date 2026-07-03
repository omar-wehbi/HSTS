package server.db;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link PasswordHasher} (Person 1). Pure logic — no database.
 */
class PasswordHasherTest {

    @Test
    void matchesKnownSha256Vector() {
        // Same value MySQL produces for SHA2('1234', 256) — keeps Java and the
        // seed scripts in agreement.
        assertEquals("03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4",
                PasswordHasher.sha256("1234"));
    }

    @Test
    void isDeterministic() {
        assertEquals(PasswordHasher.sha256("secret"), PasswordHasher.sha256("secret"));
    }

    @Test
    void differentInputsGiveDifferentHashes() {
        assertNotEquals(PasswordHasher.sha256("1234"), PasswordHasher.sha256("12345"));
    }

    @Test
    void neverReturnsThePlaintext() {
        assertNotEquals("1234", PasswordHasher.sha256("1234"));
    }
}
