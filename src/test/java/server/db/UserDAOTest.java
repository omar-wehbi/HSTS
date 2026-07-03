package server.db;

import common.entities.Role;
import common.entities.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link UserDAO} (Person 1).
 *
 * <p>Integration test: runs against the local MySQL {@code hsts_a3_db} seeded by
 * {@code schema.sql}, {@code seed.sql} and {@code auth.sql}. MySQL must be running
 * and the seed users loaded.
 */
class UserDAOTest {

    private final UserDAO dao = new UserDAO();

    @Test
    void validCredentialsReturnUserWithRole() {
        User u = dao.authenticate("teacher", "1234");
        assertNotNull(u, "teacher/1234 should authenticate");
        assertEquals("teacher", u.getUsername());
        assertEquals(Role.TEACHER, u.getRole());
        assertNull(u.getIdNumber(), "a teacher has no id number");
    }

    @Test
    void studentHasIdNumber() {
        User u = dao.authenticate("maya", "1234");
        assertNotNull(u);
        assertEquals(Role.STUDENT, u.getRole());
        assertEquals("207570227", u.getIdNumber());
    }

    @Test
    void wrongPasswordReturnsNull() {
        assertNull(dao.authenticate("teacher", "wrong-password"));
    }

    @Test
    void unknownUserReturnsNull() {
        assertNull(dao.authenticate("does-not-exist", "1234"));
    }

    @Test
    void enrolledStudentIsRecognised() {
        User maya = dao.authenticate("maya", "1234");
        assertNotNull(maya);
        assertTrue(dao.isEnrolled(maya.getId(), 1), "maya is enrolled in course 1");
    }
}
