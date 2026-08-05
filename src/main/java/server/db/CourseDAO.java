package server.db;

import common.entities.Course;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Data Access Object for the {@code Courses} table (Data tier). */
public class CourseDAO {

    /** All courses, ordered by name (includes subject linkage when present). */
    public List<Course> getAll() {
        List<Course> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT c.id, c.name, c.subject_id, s.code AS subject_code "
                             + "FROM Courses c LEFT JOIN Subjects s ON s.id = c.subject_id "
                             + "ORDER BY c.name");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Integer sid = rs.getObject("subject_id") == null ? null : rs.getInt("subject_id");
                Integer scode = rs.getObject("subject_code") == null ? null : rs.getInt("subject_code");
                list.add(new Course(rs.getInt("id"), rs.getString("name"), sid, scode));
            }
        } catch (SQLException e) {
            // Fallback if Subjects / subject_id not yet migrated.
            try (Connection conn = DatabaseConfig.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT id, name FROM Courses ORDER BY name");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Course(rs.getInt("id"), rs.getString("name")));
                }
            } catch (SQLException e2) {
                System.err.println("[CourseDAO] getAll failed: " + e2.getMessage());
            }
        }
        return list;
    }

    /** Course-teacher assignment is authoritative data imported from the external user system. */
    public boolean isTeacherAssigned(int teacherId, int courseId) {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT 1 FROM CourseTeachers WHERE teacher_id=? AND course_id=?")) {
            ps.setInt(1, teacherId); ps.setInt(2, courseId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException e) {
            System.err.println("[CourseDAO] isTeacherAssigned failed: " + e.getMessage());
            return false;
        }
    }
}
