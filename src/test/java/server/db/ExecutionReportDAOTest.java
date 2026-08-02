package server.db;

import common.entities.Exam;
import common.entities.ExamRelease;
import common.entities.ExamSession;
import common.entities.ExamSessionStatus;
import common.entities.Grade;
import common.entities.GradeStatus;
import common.entities.StudentAnswer;
import common.network.ExamExecutionSummary;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionReportDAOTest extends ExecutionDaoTestBase {

    private final ExecutionReportDAO dao = new ExecutionReportDAO();
    private final ExamSessionDAO sessions = new ExamSessionDAO();
    private final GradeDAO grades = new GradeDAO();

    @Test
    void summaryCountsSessions() {
        Exam exam = ExecutionTestFixture.ensureApprovedExam();
        ExamRelease release = ExecutionTestFixture.createOpenRelease(exam, "5001");
        int maya = ExecutionTestFixture.userId("maya");
        ExamSession session = ExecutionTestFixture.startSession(release, maya, 20);
        int qid = exam.getQuestions().get(0).getQuestionId();
        sessions.submit(session.getId(), List.of(new StudentAnswer(qid, 2)),
                LocalDateTime.now(), ExamSessionStatus.SUBMITTED);

        ExamExecutionSummary summary = dao.summary(release.getId());
        assertThat(summary).isNotNull();
        assertThat(summary.getStartedCount()).isEqualTo(1);
        assertThat(summary.getSubmittedCount()).isEqualTo(1);
    }

    @Test
    void refreshStatisticsWritesBins() {
        Exam exam = ExecutionTestFixture.ensureApprovedExam();
        ExamRelease release = ExecutionTestFixture.createOpenRelease(exam, "5002");
        int maya = ExecutionTestFixture.userId("maya");
        ExamSession session = ExecutionTestFixture.startSession(release, maya, 20);
        int qid = exam.getQuestions().get(0).getQuestionId();
        sessions.submit(session.getId(), List.of(new StudentAnswer(qid, 2)),
                LocalDateTime.now(), ExamSessionStatus.SUBMITTED);
        Grade grade = ExecutionTestFixture.autoGrade(session, 100);
        grades.approve(grade.getId(), ExecutionTestFixture.userId("teacher"), LocalDateTime.now());

        dao.refreshStatistics(release.getId());
        // second call exercises ON DUPLICATE KEY UPDATE
        dao.refreshStatistics(release.getId());
        assertThat(grades.getVisibleByRelease(release.getId())).hasSize(1);
        assertThat(grade.getStatus()).isEqualTo(GradeStatus.AUTO_GRADED); // local object unchanged
    }
}
