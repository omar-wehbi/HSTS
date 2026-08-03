package server.db;

import common.entities.Exam;
import common.entities.ExamQuestion;
import common.entities.ExamRelease;
import common.entities.ExamSession;
import common.entities.ExamStatus;
import common.entities.Grade;
import common.entities.GradeStatus;
import common.entities.Question;
import common.entities.StudentAnswer;
import common.entities.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

/**
 * FK-safe wipe/seed helpers for Person 4 execution/grading/study-bot DAO tests.
 * Does not rewrite Person 2's {@link QuestionBankTestFixture}; composes beside it.
 */
public final class ExecutionTestFixture {

    public static final int COURSE_ALGORITHMS = QuestionBankTestFixture.COURSE_ALGORITHMS;

    private ExecutionTestFixture() { }

    /** Clears execution/bot rows that Person 4 DAOs own. Leaves Users/Courses intact. */
    public static void wipeExecutionData() {
        TestDatabase.requireTestDatabase();
        execute(
                "DELETE FROM StudyBotHistory",
                "DELETE FROM StudyBotSources",
                "DELETE FROM StudyBots",
                "DELETE FROM ExamExecutionStatistics",
                "DELETE FROM Grades",
                "DELETE FROM StudentAnswers",
                "DELETE FROM ExamSessions",
                "DELETE FROM ReleasedExamQuestions",
                "DELETE FROM ExamReleases"
        );
    }

    public static int userId(String username) {
        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id FROM Users WHERE username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException("Missing seed user: " + username);
                }
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lookup user failed: " + e.getMessage(), e);
        }
    }

    public static User teacher() {
        return new UserDAO().authenticate("teacher", "1234");
    }

    public static User studentMaya() {
        return new UserDAO().authenticate("maya", "1234");
    }

    /**
     * Ensures courses, at least one question, CourseTeachers link, and returns a
     * freshly created APPROVED exam with one 100-point question.
     */
    public static Exam ensureApprovedExam() {
        QuestionBankTestFixture.ensureCourses();
        int teacherId = userId("teacher");
        ensureCourseTeacher(COURSE_ALGORITHMS, teacherId);

        QuestionDAO questions = new QuestionDAO();
        Question q = QuestionBankTestFixture.sample(COURSE_ALGORITHMS, "Coverage fixture Q");
        q.setTopic("Complexity");
        q.setDifficulty("EASY");
        Question saved = questions.add(q);
        if (saved == null) {
            throw new IllegalStateException("Could not insert fixture question");
        }

        Exam exam = new Exam(
                COURSE_ALGORITHMS,
                teacherId,
                "Coverage Approved Exam",
                30,
                "Answer all.",
                "Teacher notes",
                List.of(new ExamQuestion(saved.getId(), 100, 1))
        );
        Exam created = new ExamDAO().create(exam);
        if (created == null) {
            throw new IllegalStateException("Could not create fixture exam");
        }
        markApproved(created.getId());
        created.setStatus(ExamStatus.APPROVED);
        return new ExamDAO().getById(created.getId());
    }

    public static ExamRelease createOpenRelease(Exam exam, String code) {
        LocalDateTime now = LocalDateTime.now();
        ExamRelease release = new ExamRelease(
                exam.getId(),
                exam.getTeacherId(),
                code,
                now.minusMinutes(5),
                now.plusHours(2)
        );
        ExamRelease saved = new ExamReleaseDAO().create(release);
        if (saved == null) {
            throw new IllegalStateException("Could not create fixture release");
        }
        boolean snap = new ExamSnapshotDAO().createForRelease(saved.getId(), exam, new QuestionDAO());
        if (!snap) {
            throw new IllegalStateException("Could not snapshot release questions");
        }
        return saved;
    }

    public static ExamSession startSession(ExamRelease release, int studentId, int durationMinutes) {
        LocalDateTime now = LocalDateTime.now();
        ExamSession session = new ExamSession(
                release.getId(),
                release.getExamId(),
                studentId,
                now,
                now.plusMinutes(durationMinutes)
        );
        ExamSession saved = new ExamSessionDAO().create(session);
        if (saved == null) {
            throw new IllegalStateException("Could not create fixture session");
        }
        return saved;
    }

    public static Grade autoGrade(ExamSession session, int score) {
        Grade grade = new Grade(
                session.getId(),
                session.getExamId(),
                session.getStudentId(),
                score,
                LocalDateTime.now()
        );
        Grade saved = new GradeDAO().create(grade);
        if (saved == null) {
            throw new IllegalStateException("Could not create fixture grade");
        }
        return saved;
    }

    public static void ensureCourseTeacher(int courseId, int teacherId) {
        execute("INSERT IGNORE INTO CourseTeachers (course_id, teacher_id) VALUES ("
                + courseId + "," + teacherId + ")");
    }

    private static void markApproved(int examId) {
        execute("UPDATE Exams SET status='APPROVED' WHERE id=" + examId);
    }

    static void execute(String... statements) {
        try (Connection conn = DatabaseConfig.getConnection();
             Statement st = conn.createStatement()) {
            for (String sql : statements) {
                try {
                    st.executeUpdate(sql);
                } catch (SQLException e) {
                    // Table may be absent on older DBs (e.g. statistics) — ignore missing-table only.
                    if (e.getMessage() != null && e.getMessage().toLowerCase().contains("doesn't exist")) {
                        continue;
                    }
                    throw e;
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Execution fixture SQL failed: " + e.getMessage(), e);
        }
    }
}
