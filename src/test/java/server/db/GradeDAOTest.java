package server.db;

import common.entities.Exam;
import common.entities.ExamRelease;
import common.entities.ExamSession;
import common.entities.ExamSessionStatus;
import common.entities.Grade;
import common.entities.GradeStatus;
import common.entities.StudentAnswer;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GradeDAOTest extends ExecutionDaoTestBase {

    private final GradeDAO dao = new GradeDAO();
    private final ExamSessionDAO sessions = new ExamSessionDAO();

    private ExamSession submittedSession(String code) {
        Exam exam = ExecutionTestFixture.ensureApprovedExam();
        ExamRelease release = ExecutionTestFixture.createOpenRelease(exam, code);
        int maya = ExecutionTestFixture.userId("maya");
        ExamSession session = ExecutionTestFixture.startSession(release, maya, 20);
        int qid = exam.getQuestions().get(0).getQuestionId();
        return sessions.submit(session.getId(),
                List.of(new StudentAnswer(qid, 2)),
                LocalDateTime.now(),
                ExamSessionStatus.SUBMITTED);
    }

    @Test
    void createGetByIdAndSession() {
        ExamSession session = submittedSession("3001");
        Grade grade = ExecutionTestFixture.autoGrade(session, 80);

        assertThat(dao.getById(grade.getId()).getAutoScore()).isEqualTo(80);
        assertThat(dao.getBySessionId(session.getId()).getId()).isEqualTo(grade.getId());
        assertThat(dao.getByExamId(session.getExamId())).hasSize(1);
        assertThat(dao.getByRelease(session.getReleaseId())).hasSize(1);
    }

    @Test
    void approveOnlyFromAutoGraded() {
        ExamSession session = submittedSession("3002");
        Grade grade = ExecutionTestFixture.autoGrade(session, 70);
        int teacherId = ExecutionTestFixture.userId("teacher");

        Grade approved = dao.approve(grade.getId(), teacherId, LocalDateTime.now(), "ok");
        assertThat(approved.getStatus()).isEqualTo(GradeStatus.APPROVED);
        assertThat(approved.getFinalScore()).isEqualTo(70);

        assertThat(dao.approve(approved.getId(), teacherId, LocalDateTime.now())).isNull();
        assertThat(dao.getVisibleByStudent(session.getStudentId())).hasSize(1);
        assertThat(dao.getVisibleByRelease(session.getReleaseId())).hasSize(1);
    }

    @Test
    void overrideSetsScoreAndJustification() {
        ExamSession session = submittedSession("3003");
        Grade grade = ExecutionTestFixture.autoGrade(session, 50);
        int teacherId = ExecutionTestFixture.userId("teacher");

        Grade overridden = dao.override(grade.getId(), 90, "partial credit", teacherId, LocalDateTime.now());
        assertThat(overridden.getStatus()).isEqualTo(GradeStatus.OVERRIDDEN);
        assertThat(overridden.getFinalScore()).isEqualTo(90);
        assertThat(overridden.getOverrideJustification()).isEqualTo("partial credit");
        assertThat(dao.getVisibleByStudent(session.getStudentId())).hasSize(1);
    }

    @Test
    void createReturnsNullWhenForeignKeyDoesNotExist() {
        Exam exam = ExecutionTestFixture.ensureApprovedExam();
        int studentId = ExecutionTestFixture.userId("maya");
        assertThat(dao.create(new Grade(999_999, exam.getId(), studentId, 50, LocalDateTime.now())))
                .isNull();
    }
}
