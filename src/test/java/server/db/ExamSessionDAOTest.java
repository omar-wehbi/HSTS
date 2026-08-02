package server.db;

import common.entities.Exam;
import common.entities.ExamRelease;
import common.entities.ExamSession;
import common.entities.ExamSessionStatus;
import common.entities.StudentAnswer;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExamSessionDAOTest extends ExecutionDaoTestBase {

    private final ExamSessionDAO dao = new ExamSessionDAO();

    @Test
    void createAndGetByIdLoadsAnswers() {
        Exam exam = ExecutionTestFixture.ensureApprovedExam();
        ExamRelease release = ExecutionTestFixture.createOpenRelease(exam, "1001");
        int maya = ExecutionTestFixture.userId("maya");
        ExamSession session = ExecutionTestFixture.startSession(release, maya, 30);

        dao.saveAnswers(session.getId(), List.of(new StudentAnswer(exam.getQuestions().get(0).getQuestionId(), 2)));

        ExamSession loaded = dao.getById(session.getId());
        assertThat(loaded).isNotNull();
        assertThat(loaded.getStatus()).isEqualTo(ExamSessionStatus.IN_PROGRESS);
        assertThat(loaded.getAnswers()).hasSize(1);
    }

    @Test
    void getByReleaseAndStudentFindsLatest() {
        Exam exam = ExecutionTestFixture.ensureApprovedExam();
        ExamRelease release = ExecutionTestFixture.createOpenRelease(exam, "1002");
        int maya = ExecutionTestFixture.userId("maya");
        ExamSession session = ExecutionTestFixture.startSession(release, maya, 20);

        ExamSession found = dao.getByReleaseAndStudent(release.getId(), maya);
        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(session.getId());
        assertThat(dao.getByRelease(release.getId())).hasSize(1);
    }

    @Test
    void saveAnswersRejectedWhenNotInProgress() {
        Exam exam = ExecutionTestFixture.ensureApprovedExam();
        ExamRelease release = ExecutionTestFixture.createOpenRelease(exam, "1003");
        int maya = ExecutionTestFixture.userId("maya");
        ExamSession session = ExecutionTestFixture.startSession(release, maya, 15);
        int qid = exam.getQuestions().get(0).getQuestionId();

        dao.submit(session.getId(), List.of(new StudentAnswer(qid, 1)), LocalDateTime.now(), ExamSessionStatus.SUBMITTED);
        assertThat(dao.saveAnswers(session.getId(), List.of(new StudentAnswer(qid, 2)))).isNull();
    }

    @Test
    void submitClosesAttempt() {
        Exam exam = ExecutionTestFixture.ensureApprovedExam();
        ExamRelease release = ExecutionTestFixture.createOpenRelease(exam, "1004");
        int maya = ExecutionTestFixture.userId("maya");
        ExamSession session = ExecutionTestFixture.startSession(release, maya, 15);
        int qid = exam.getQuestions().get(0).getQuestionId();

        ExamSession submitted = dao.submit(session.getId(),
                List.of(new StudentAnswer(qid, 2)),
                LocalDateTime.now(),
                ExamSessionStatus.SUBMITTED);
        assertThat(submitted).isNotNull();
        assertThat(submitted.getStatus()).isEqualTo(ExamSessionStatus.SUBMITTED);
        assertThat(submitted.getSubmittedAt()).isNotNull();
    }

    @Test
    void expireOverdueSessionMarksTimedOut() {
        Exam exam = ExecutionTestFixture.ensureApprovedExam();
        ExamRelease release = ExecutionTestFixture.createOpenRelease(exam, "1005");
        int maya = ExecutionTestFixture.userId("maya");
        LocalDateTime started = LocalDateTime.now().minusMinutes(40);
        ExamSession session = new ExamSession(release.getId(), exam.getId(), maya, started, started.plusMinutes(5));
        session = dao.create(session);
        assertThat(session).isNotNull();

        ExamSession expired = dao.expireOverdueSession(session.getId(), LocalDateTime.now());
        assertThat(expired.getStatus()).isEqualTo(ExamSessionStatus.TIMED_OUT);
        assertThat(dao.expireOverdueSessions(LocalDateTime.now())).isGreaterThanOrEqualTo(0);
    }

    @Test
    void extendActiveSessionsAddsMinutes() {
        Exam exam = ExecutionTestFixture.ensureApprovedExam();
        ExamRelease release = ExecutionTestFixture.createOpenRelease(exam, "1006");
        int maya = ExecutionTestFixture.userId("maya");
        ExamSession session = ExecutionTestFixture.startSession(release, maya, 10);
        LocalDateTime before = session.getDeadline();

        int n = dao.extendActiveSessions(release.getId(), 15, LocalDateTime.now());
        assertThat(n).isEqualTo(1);
        ExamSession updated = dao.getById(session.getId());
        assertThat(updated.getDeadline()).isAfter(before.minusSeconds(1));
        assertThat(updated.getDeadline()).isEqualTo(updated.getDeadline()); // loaded
        assertThat(updated.getExtensionMinutes()).isEqualTo(15);
        assertThat(java.time.Duration.between(before, updated.getDeadline()).toMinutes()).isEqualTo(15);
    }

    @Test
    void hasActiveSessionFlags() {
        Exam exam = ExecutionTestFixture.ensureApprovedExam();
        ExamRelease release = ExecutionTestFixture.createOpenRelease(exam, "1007");
        int maya = ExecutionTestFixture.userId("maya");
        ExecutionTestFixture.startSession(release, maya, 20);
        LocalDateTime now = LocalDateTime.now();

        assertThat(dao.hasActiveSession(maya, now)).isTrue();
        assertThat(dao.hasActiveSessionForCourse(maya, ExecutionTestFixture.COURSE_ALGORITHMS, now)).isTrue();
        assertThat(dao.hasActiveSession(ExecutionTestFixture.userId("noa"), now)).isFalse();
    }
}
