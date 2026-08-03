package server.db;

import common.entities.Exam;
import common.entities.ExamRelease;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExamSnapshotDAOTest extends ExecutionDaoTestBase {

    private final ExamSnapshotDAO dao = new ExamSnapshotDAO();

    @Test
    void createForReleaseAndReadBack() {
        Exam exam = ExecutionTestFixture.ensureApprovedExam();
        ExamRelease release = ExecutionTestFixture.createOpenRelease(exam, "4001");

        var rows = dao.getByRelease(release.getId());
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getQuestionId()).isEqualTo(exam.getQuestions().get(0).getQuestionId());
        assertThat(rows.get(0).getPoints()).isEqualTo(100);
        assertThat(rows.get(0).getText()).contains("Coverage fixture");
    }

    @Test
    void createForReleaseReturnsFalseWhenForeignKeyDoesNotExist() {
        Exam exam = ExecutionTestFixture.ensureApprovedExam();
        assertThat(dao.createForRelease(999_999, exam, new QuestionDAO())).isFalse();
    }
}
