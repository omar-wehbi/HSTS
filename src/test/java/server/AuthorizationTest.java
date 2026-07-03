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

    // ===== course-level checks (enrollment via lambda — no database) =======

    private static final Authorization.EnrollmentCheck ENROLLED     = (u, c) -> true;
    private static final Authorization.EnrollmentCheck NOT_ENROLLED = (u, c) -> false;

    @Test
    void requireEnrollment_passesForEnrolledStudent() {
        assertDoesNotThrow(() ->
                Authorization.requireEnrollment(userWithRole(Role.STUDENT), 1, ENROLLED));
    }

    @Test
    void requireEnrollment_throwsForNotEnrolledStudent() {
        assertThrows(AuthorizationException.class, () ->
                Authorization.requireEnrollment(userWithRole(Role.STUDENT), 3, NOT_ENROLLED));
    }

    @Test
    void requireEnrollment_throwsWhenNotLoggedIn() {
        assertThrows(AuthorizationException.class, () ->
                Authorization.requireEnrollment(null, 1, ENROLLED));
    }

    @Test
    void requireCourseAccess_staffAlwaysAllowed() {
        for (Role staff : new Role[]{Role.TEACHER, Role.COORDINATOR, Role.PRINCIPAL}) {
            assertDoesNotThrow(() ->
                    Authorization.requireCourseAccess(userWithRole(staff), 3, NOT_ENROLLED),
                    staff + " must access any course");
        }
    }

    @Test
    void requireCourseAccess_enrolledStudentAllowed() {
        assertDoesNotThrow(() ->
                Authorization.requireCourseAccess(userWithRole(Role.STUDENT), 1, ENROLLED));
    }

    @Test
    void requireCourseAccess_notEnrolledStudentRejected() {
        assertThrows(AuthorizationException.class, () ->
                Authorization.requireCourseAccess(userWithRole(Role.STUDENT), 3, NOT_ENROLLED));
    }
}
