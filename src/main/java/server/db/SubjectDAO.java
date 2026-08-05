package server.db;

import common.entities.Subject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** JDBC access for Subjects and coordinator-of-course checks. */
public class SubjectDAO {

    /** Subjects this user coordinates, ordered by code. */
    public List<Subject> listByCoordinator(int coordinatorUserId) {
        List<Subject> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id, name, code, coordinator_id FROM Subjects "
                             + "WHERE coordinator_id=? ORDER BY code, name")) {
            ps.setInt(1, coordinatorUserId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Integer coord = rs.getObject("coordinator_id") == null
                            ? null : rs.getInt("coordinator_id");
                    list.add(new Subject(rs.getInt("id"), rs.getString("name"),
                            rs.getInt("code"), coord));
                }
            }
        } catch (SQLException e) {
            System.err.println("[SubjectDAO] listByCoordinator failed: " + e.getMessage());
        }
        return list;
    }

    /** Whether this user is the coordinator of the given subject. */
    public boolean isCoordinatorForSubject(int coordinatorUserId, int subjectId) {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT 1 FROM Subjects WHERE id=? AND coordinator_id=?")) {
            ps.setInt(1, subjectId);
            ps.setInt(2, coordinatorUserId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("[SubjectDAO] isCoordinatorForSubject failed: " + e.getMessage());
            return false;
        }
    }

    public Subject getById(int subjectId) {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id, name, code, coordinator_id FROM Subjects WHERE id=?")) {
            ps.setInt(1, subjectId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Integer coord = rs.getObject("coordinator_id") == null
                        ? null : rs.getInt("coordinator_id");
                return new Subject(rs.getInt("id"), rs.getString("name"),
                        rs.getInt("code"), coord);
            }
        } catch (SQLException e) {
            System.err.println("[SubjectDAO] getById failed: " + e.getMessage());
            return null;
        }
    }

    /** Subject code for a course (0 if unset / missing). */
    public int subjectCodeForCourse(int courseId) {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT s.code FROM Courses c "
                             + "JOIN Subjects s ON s.id = c.subject_id WHERE c.id=?")) {
            ps.setInt(1, courseId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            System.err.println("[SubjectDAO] subjectCodeForCourse failed: " + e.getMessage());
            return 0;
        }
    }

    /** Whether this user is the subject coordinator for the given course. */
    public boolean isCoordinatorForCourse(int coordinatorUserId, int courseId) {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT 1 FROM Courses c "
                             + "JOIN Subjects s ON s.id = c.subject_id "
                             + "WHERE c.id=? AND s.coordinator_id=?")) {
            ps.setInt(1, courseId);
            ps.setInt(2, coordinatorUserId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("[SubjectDAO] isCoordinatorForCourse failed: " + e.getMessage());
            return false;
        }
    }
}
