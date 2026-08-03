package server.db;

import common.entities.Question;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Deterministic test data for the question-bank DAO suite (Person 2, Phase 1).
 *
 * <p>Two jobs:
 * <ul>
 *   <li><b>Builders</b> — {@link #sample(int)} & friends produce a fully valid
 *       {@link Question} so tests only spell out the fields they care about.</li>
 *   <li><b>State control</b> — {@link #wipeQuestions()} + {@link #ensureCourses()}
 *       give every test an identical, empty bank over the same three courses,
 *       independent of what a previous test (or a demo run) left behind.</li>
 * </ul>
 *
 * <p>Runs against the database resolved by {@link DatabaseConfig} — same as the
 * DAOs under test, so there is no "test database" drift.
 */
public final class QuestionBankTestFixture {

    /** Course ids guaranteed to exist (match seed_test_scenarios.sql). */
    public static final int COURSE_ALGORITHMS = 1;
    public static final int COURSE_DATABASES  = 2;
    public static final int COURSE_NETWORKS   = 3;

    private QuestionBankTestFixture() { }

    // ===== builders =======================================================

    /** A fully valid question for {@code courseId} with distinct, recognisable fields. */
    public static Question sample(int courseId) {
        return sample(courseId, "What is the capital of testing?");
    }

    /** Same as {@link #sample(int)} but with caller-chosen question text. */
    public static Question sample(int courseId, String text) {
        return new Question(
                courseId,
                text,
                "Answer one", "Answer two", "Answer three", "Answer four",
                2,                       // correct answer
                null,                    // no illustration by default
                "Testing",               // topic
                "EASY");                 // difficulty
    }

    /** Inserts {@code n} distinct questions for one course; returns nothing — read them back via the DAO. */
    public static void insertN(QuestionDAO dao, int n, int courseId) {
        for (int i = 1; i <= n; i++) {
            dao.add(sample(courseId, "Question #" + i + " of course " + courseId));
        }
    }

    // ===== state control ==================================================

    /**
     * Deletes every question (all versions, all families) and resets the id
     * counter, so generated ids are stable across test runs.
     *
     * <p>Child rows that reference {@code Questions} must go first — a seeded
     * {@code ExamQuestions}/{@code StudentAnswers} row would otherwise block
     * {@code DELETE FROM Questions} with a foreign-key error.
     */
    public static void wipeQuestions() {
        TestDatabase.requireTestDatabase();
        execute("DELETE FROM StudentAnswers",
                "DELETE FROM ExamQuestions",
                "DELETE FROM Questions",
                "ALTER TABLE Questions AUTO_INCREMENT = 1");
    }

    /**
     * Guarantees the three seed courses exist (idempotent), because every
     * question row needs a valid {@code course_id} foreign key.
     */
    public static void ensureCourses() {
        execute("INSERT INTO Courses (id, name) VALUES "
                + "(1,'Algorithms'), (2,'Databases'), (3,'Computer Networks') "
                + "ON DUPLICATE KEY UPDATE name = VALUES(name)");
    }

    private static void execute(String... statements) {
        try (Connection conn = DatabaseConfig.getConnection();
             Statement st = conn.createStatement()) {
            for (String sql : statements) {
                st.executeUpdate(sql);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Fixture SQL failed: " + e.getMessage(), e);
        }
    }
}
