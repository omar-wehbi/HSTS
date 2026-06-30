package server.db;

import common.entities.Question;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for the {@code Questions} table (Data tier).
 *
 * <p>The only class that runs SQL for the question bank. Implements the four
 * bank operations from the assignment:
 * <ul>
 *   <li><b>view</b>  — {@link #getAllCurrent()} / {@link #getByCourse(int)}</li>
 *   <li><b>add</b>   — {@link #add(Question)}</li>
 *   <li><b>edit</b>  — {@link #update(Question)} (versioned: the old version stays)</li>
 *   <li><b>delete</b>— {@link #delete(int)}</li>
 * </ul>
 * Every query uses a {@link PreparedStatement} with bound parameters, so user
 * input can never be executed as SQL (SQL-injection safe).
 */
public class QuestionDAO {

    private static final String COLS =
            "id, course_id, question_text, answer_1, answer_2, answer_3, answer_4, " +
            "correct_answer, image_path, topic, difficulty, base_id, version, is_current";

    // ===== READ ===========================================================

    /** The current question bank (latest version of every question). */
    public List<Question> getAllCurrent() {
        return query("SELECT " + COLS + " FROM Questions WHERE is_current = TRUE ORDER BY base_id", -1);
    }

    /** Current questions for one course. */
    public List<Question> getByCourse(int courseId) {
        return query("SELECT " + COLS + " FROM Questions " +
                "WHERE is_current = TRUE AND course_id = ? ORDER BY base_id", courseId);
    }

    /** All versions of one question (its history), oldest first. */
    public List<Question> getHistory(int baseId) {
        return query("SELECT " + COLS + " FROM Questions " +
                "WHERE base_id = ? ORDER BY version", baseId);
    }

    // ===== ADD ============================================================

    /**
     * Inserts a brand-new question as version 1 and sets its version-family id
     * ({@code base_id}) to its own generated id.
     *
     * @return the same question with its new {@code id} and {@code baseId} filled in.
     */
    public Question add(Question q) {
        String sql = "INSERT INTO Questions " +
                "(course_id, question_text, answer_1, answer_2, answer_3, answer_4, " +
                " correct_answer, image_path, topic, difficulty, version, is_current) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,1,TRUE)";
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            bindCommonFields(ps, q);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int newId = keys.getInt(1);
                    q.setId(newId);
                    q.setBaseId(newId);
                    q.setVersion(1);
                    q.setCurrent(true);
                    // The first version's family id is its own id.
                    setBaseId(conn, newId, newId);
                }
            }
            return q;
        } catch (SQLException e) {
            System.err.println("[QuestionDAO] add failed: " + e.getMessage());
            return null;
        }
    }

    // ===== EDIT (versioned) ==============================================

    /**
     * Edits a question WITHOUT overwriting the old version. In one transaction:
     * the current version is marked {@code is_current = FALSE} and a new row is
     * inserted (same {@code base_id}, {@code version + 1}, {@code is_current = TRUE}).
     * The previous version stays in the bank, as the assignment requires.
     *
     * @param q the edited question; must carry the {@code baseId} of the family.
     * @return the new current version (with its new id/version), or null on failure.
     */
    public Question update(Question q) {
        Connection conn = null;
        try {
            conn = DatabaseConfig.getConnection();
            conn.setAutoCommit(false);   // transaction: both steps succeed or neither

            // 1) retire the current version
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE Questions SET is_current = FALSE WHERE base_id = ? AND is_current = TRUE")) {
                ps.setInt(1, q.getBaseId());
                ps.executeUpdate();
            }

            // 2) find the next version number
            int nextVersion = 1;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COALESCE(MAX(version),0) + 1 AS nv FROM Questions WHERE base_id = ?")) {
                ps.setInt(1, q.getBaseId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) nextVersion = rs.getInt("nv");
                }
            }

            // 3) insert the new current version
            String insert = "INSERT INTO Questions " +
                    "(course_id, question_text, answer_1, answer_2, answer_3, answer_4, " +
                    " correct_answer, image_path, topic, difficulty, base_id, version, is_current) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,TRUE)";
            int newId;
            try (PreparedStatement ps = conn.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)) {
                bindCommonFields(ps, q);
                ps.setInt(11, q.getBaseId());
                ps.setInt(12, nextVersion);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    newId = keys.getInt(1);
                }
            }

            conn.commit();
            q.setId(newId);
            q.setVersion(nextVersion);
            q.setCurrent(true);
            return q;
        } catch (SQLException e) {
            System.err.println("[QuestionDAO] update failed: " + e.getMessage());
            rollback(conn);
            return null;
        } finally {
            close(conn);
        }
    }

    // ===== DELETE =========================================================

    /**
     * Deletes a question and all of its versions (the whole family).
     *
     * @return true if at least one row was removed.
     */
    public boolean delete(int baseId) {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM Questions WHERE base_id = ?")) {
            ps.setInt(1, baseId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[QuestionDAO] delete failed: " + e.getMessage());
            return false;
        }
    }

    // ===== helpers ========================================================

    /** Binds the 10 shared question fields to positions 1..10 of a statement. */
    private void bindCommonFields(PreparedStatement ps, Question q) throws SQLException {
        ps.setInt(1, q.getCourseId());
        ps.setString(2, q.getQuestionText());
        ps.setString(3, q.getAnswer1());
        ps.setString(4, q.getAnswer2());
        ps.setString(5, q.getAnswer3());
        ps.setString(6, q.getAnswer4());
        ps.setInt(7, q.getCorrectAnswer());
        ps.setString(8, q.getImagePath());
        ps.setString(9, q.getTopic());
        ps.setString(10, q.getDifficulty());
    }

    private void setBaseId(Connection conn, int id, int baseId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE Questions SET base_id = ? WHERE id = ?")) {
            ps.setInt(1, baseId);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    private List<Question> query(String sql, int param) {
        List<Question> list = new ArrayList<>();
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (param >= 0) ps.setInt(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            System.err.println("[QuestionDAO] query failed: " + e.getMessage());
        }
        return list;
    }

    /** Builds a Question from the current row of a result set. */
    private Question mapRow(ResultSet rs) throws SQLException {
        Question q = new Question();
        q.setId(rs.getInt("id"));
        q.setCourseId(rs.getInt("course_id"));
        q.setQuestionText(rs.getString("question_text"));
        q.setAnswer1(rs.getString("answer_1"));
        q.setAnswer2(rs.getString("answer_2"));
        q.setAnswer3(rs.getString("answer_3"));
        q.setAnswer4(rs.getString("answer_4"));
        q.setCorrectAnswer(rs.getInt("correct_answer"));
        q.setImagePath(rs.getString("image_path"));
        q.setTopic(rs.getString("topic"));
        q.setDifficulty(rs.getString("difficulty"));
        q.setBaseId(rs.getInt("base_id"));
        q.setVersion(rs.getInt("version"));
        q.setCurrent(rs.getBoolean("is_current"));
        return q;
    }

    private void rollback(Connection conn) {
        if (conn != null) try { conn.rollback(); } catch (SQLException ignored) { }
    }

    private void close(Connection conn) {
        if (conn != null) try { conn.setAutoCommit(true); conn.close(); } catch (SQLException ignored) { }
    }
}
