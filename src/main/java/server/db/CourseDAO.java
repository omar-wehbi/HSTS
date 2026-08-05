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

    /** Courses assigned to this teacher via {@code CourseTeachers}, ordered by name. */
    public List<Course> listForTeacher(int teacherId) {
        List<Course> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT c.id, c.name, c.subject_id, s.code AS subject_code "
                             + "FROM Courses c "
                             + "JOIN CourseTeachers ct ON ct.course_id = c.id "
                             + "LEFT JOIN Subjects s ON s.id = c.subject_id "
                             + "WHERE ct.teacher_id=? ORDER BY c.name")) {
            ps.setInt(1, teacherId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Integer sid = rs.getObject("subject_id") == null
                            ? null : rs.getInt("subject_id");
                    Integer scode = rs.getObject("subject_code") == null
                            ? null : rs.getInt("subject_code");
                    list.add(new Course(rs.getInt("id"), rs.getString("name"), sid, scode));
                }
            }
        } catch (SQLException e) {
            System.err.println("[CourseDAO] listForTeacher failed: " + e.getMessage());
        }
        return list;
    }

    /** Courses belonging to a subject, ordered by name. */
    public List<Course> listBySubject(int subjectId) {
        List<Course> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT c.id, c.name, c.subject_id, s.code AS subject_code "
                             + "FROM Courses c "
                             + "LEFT JOIN Subjects s ON s.id = c.subject_id "
                             + "WHERE c.subject_id=? ORDER BY c.name")) {
            ps.setInt(1, subjectId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Integer sid = rs.getObject("subject_id") == null
                            ? null : rs.getInt("subject_id");
                    Integer scode = rs.getObject("subject_code") == null
                            ? null : rs.getInt("subject_code");
                    list.add(new Course(rs.getInt("id"), rs.getString("name"), sid, scode));
                }
            }
        } catch (SQLException e) {
            System.err.println("[CourseDAO] listBySubject failed: " + e.getMessage());
        }
        return list;
    }

    /**
     * Course ids under this coordinator, optionally narrowed by subject and/or course.
     * Empty list means no matching courses (not "all courses").
     */
    public List<Integer> courseIdsForCoordinator(int coordinatorId,
                                                 Integer subjectId,
                                                 Integer courseId) {
        List<Integer> ids = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT c.id FROM Courses c "
                        + "JOIN Subjects s ON s.id = c.subject_id "
                        + "WHERE s.coordinator_id=?");
        if (subjectId != null) {
            sql.append(" AND c.subject_id=?");
        }
        if (courseId != null) {
            sql.append(" AND c.id=?");
        }
        sql.append(" ORDER BY c.id");
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int i = 1;
            ps.setInt(i++, coordinatorId);
            if (subjectId != null) {
                ps.setInt(i++, subjectId);
            }
            if (courseId != null) {
                ps.setInt(i, courseId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt(1));
                }
            }
        } catch (SQLException e) {
            System.err.println("[CourseDAO] courseIdsForCoordinator failed: " + e.getMessage());
        }
        return ids;
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
