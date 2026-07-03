package server;

import common.entities.Role;
import common.entities.User;

/**
 * Role-based authorization helper (Logic tier, Person 1).
 *
 * <p>Handlers use this to guard role-restricted actions. Two styles:
 * <ul>
 *   <li>{@link #hasRole} — a boolean check.</li>
 *   <li>{@link #requireRole} — throws {@link AuthorizationException} (which the
 *       server turns into an {@code ERROR} reply) so a handler can guard in one
 *       line at the top.</li>
 * </ul>
 *
 * <p>Example (in a teammate's handler):
 * <pre>{@code
 *   User caller = sessions.getUser(client);
 *   Authorization.requireRole(caller, Role.TEACHER);   // only teachers past here
 * }</pre>
 */
public final class Authorization {

    private Authorization() { }

    /** @return true if {@code user} is logged in and has one of the {@code allowed} roles. */
    public static boolean hasRole(User user, Role... allowed) {
        if (user == null) return false;
        for (Role r : allowed) {
            if (user.getRole() == r) return true;
        }
        return false;
    }

    /**
     * Ensures the caller is logged in and holds one of the allowed roles.
     *
     * @throws AuthorizationException if not logged in or not authorized.
     */
    public static void requireRole(User user, Role... allowed) {
        if (user == null) {
            throw new AuthorizationException("You must be logged in.");
        }
        if (!hasRole(user, allowed)) {
            throw new AuthorizationException("You are not authorized to perform this action.");
        }
    }

    // ===== course-level checks ============================================

    /**
     * How enrollment is looked up. {@code UserDAO::isEnrolled} matches this
     * signature; tests can pass a simple lambda instead of a database.
     */
    @FunctionalInterface
    public interface EnrollmentCheck {
        boolean isEnrolled(int userId, int courseId);
    }

    /**
     * Ensures the caller is logged in and enrolled in the given course
     * (e.g. scenario 14: a student may use a course's study bot only if
     * enrolled in that course).
     *
     * @throws AuthorizationException if not logged in or not enrolled.
     */
    public static void requireEnrollment(User user, int courseId, EnrollmentCheck check) {
        if (user == null) {
            throw new AuthorizationException("You must be logged in.");
        }
        if (!check.isEnrolled(user.getId(), courseId)) {
            throw new AuthorizationException("You are not enrolled in this course.");
        }
    }

    /**
     * Ensures the caller may access the given course's content: staff roles
     * (TEACHER / COORDINATOR / PRINCIPAL) always may; a STUDENT only if
     * enrolled in that course.
     *
     * @throws AuthorizationException if not logged in or not allowed.
     */
    public static void requireCourseAccess(User user, int courseId, EnrollmentCheck check) {
        if (user == null) {
            throw new AuthorizationException("You must be logged in.");
        }
        if (hasRole(user, Role.TEACHER, Role.COORDINATOR, Role.PRINCIPAL)) {
            return;   // staff see all courses
        }
        requireEnrollment(user, courseId, check);
    }
}
