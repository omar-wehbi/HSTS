package server.db;

import common.entities.Role;
import common.entities.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Data Access Object for the {@code Users} / {@code Enrollments} tables (Data tier).
 *
 * <p>Only class that runs SQL for authentication. Uses parameterized queries, so
 * a login attempt can never inject SQL. The returned {@link User} never carries
 * the password.
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
        String sql = "SELECT id, username, role, display_name, id_number " +
                     "FROM Users WHERE username = ? AND password = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, PasswordHasher.sha256(password));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new User(
                            rs.getInt("id"),
                            rs.getString("username"),
                            Role.valueOf(rs.getString("role")),
                            rs.getString("display_name"),
                            rs.getString("id_number"));
                }
            }
        } catch (SQLException e) {
            System.err.println("[UserDAO] authenticate failed: " + e.getMessage());
        }
        return null;
    }

    /** @return true if the user is enrolled in the given course (scenario 14). */
    public boolean isEnrolled(int userId, int courseId) {
        String sql = "SELECT 1 FROM Enrollments WHERE user_id = ? AND course_id = ?";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, courseId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("[UserDAO] isEnrolled failed: " + e.getMessage());
            return false;
        }
    }
}
