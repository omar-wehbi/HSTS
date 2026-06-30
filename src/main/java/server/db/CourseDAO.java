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

    /** All courses, ordered by name. */
    public List<Course> getAll() {
        List<Course> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id, name FROM Courses ORDER BY name");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new Course(rs.getInt("id"), rs.getString("name")));
            }
        } catch (SQLException e) {
            System.err.println("[CourseDAO] getAll failed: " + e.getMessage());
        }
        return list;
    }
}
