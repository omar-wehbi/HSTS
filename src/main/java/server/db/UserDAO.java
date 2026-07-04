package server.db;

import common.entities.User;
import org.hibernate.Session;

/**
 * Data Access Object for users and enrollment (Data tier) — Hibernate ORM
 * implementation (the team's reference example for migrating a DAO to the ORM).
 *
 * <p>The public API is identical to the previous JDBC version, so the server and
 * all tests are unaffected by the persistence-technology swap — the point of the
 * DAO pattern. Queries are parameterized (named parameters), so login input can
 * never inject SQL. The returned {@link User} never carries the password: the
 * password column is not even mapped on the entity; authentication matches it
 * inside the query only.
 */
public class UserDAO {

    /**
     * Verifies credentials. The typed password is hashed (SHA-256) and compared
     * against the stored hash — plaintext passwords never touch the database.
     *
     * @return the matching {@link User} (without password) or {@code null} if the
     *         username/password pair is wrong.
     */
    public User authenticate(String username, String password) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createNativeQuery(
                            "SELECT id, username, role, display_name, id_number " +
                            "FROM Users WHERE username = :u AND password = :p", User.class)
                    .setParameter("u", username)
                    .setParameter("p", PasswordHasher.sha256(password))
                    .uniqueResult();
        } catch (Exception e) {
            System.err.println("[UserDAO] authenticate failed: " + e.getMessage());
            return null;
        }
    }

    /** @return true if the user is enrolled in the given course (scenario 14). */
    public boolean isEnrolled(int userId, int courseId) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Object hit = session.createNativeQuery(
                            "SELECT 1 FROM Enrollments WHERE user_id = :u AND course_id = :c",
                            Integer.class)
                    .setParameter("u", userId)
                    .setParameter("c", courseId)
                    .uniqueResult();
            return hit != null;
        } catch (Exception e) {
            System.err.println("[UserDAO] isEnrolled failed: " + e.getMessage());
            return false;
        }
    }
}
