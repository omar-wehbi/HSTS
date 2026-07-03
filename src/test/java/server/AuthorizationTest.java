package server;

import common.entities.Role;
import common.entities.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Authorization} (Person 1). Pure logic — no database,
 * no network, no mocks.
 */
class AuthorizationTest {

    private User userWithRole(Role role) {
        return new User(1, "u", role, "Test User", null);
    }

    @Test
    void hasRole_matchesExactRole() {
        assertTrue(Authorization.hasRole(userWithRole(Role.TEACHER), Role.TEACHER));
    }

    @Test
    void hasRole_matchesAnyOfSeveral() {
        assertTrue(Authorization.hasRole(userWithRole(Role.COORDINATOR),
                Role.TEACHER, Role.COORDINATOR));
    }

    @Test
    void hasRole_rejectsWrongRole() {
        assertFalse(Authorization.hasRole(userWithRole(Role.STUDENT), Role.TEACHER));
    }

    @Test
    void hasRole_rejectsNullUser() {
        assertFalse(Authorization.hasRole(null, Role.TEACHER));
    }

    @Test
    void requireRole_passesForAllowedRole() {
        assertDoesNotThrow(() ->
                Authorization.requireRole(userWithRole(Role.TEACHER), Role.TEACHER));
    }

    @Test
    void requireRole_throwsForWrongRole() {
        assertThrows(AuthorizationException.class, () ->
                Authorization.requireRole(userWithRole(Role.STUDENT), Role.TEACHER));
    }

    @Test
    void requireRole_throwsWhenNotLoggedIn() {
        assertThrows(AuthorizationException.class, () ->
                Authorization.requireRole(null, Role.TEACHER));
    }
}
