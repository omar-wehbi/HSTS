package server.db;

import common.entities.Exam;
import common.entities.ExamRelease;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ExamReleaseDAOTest extends ExecutionDaoTestBase {

    private final ExamReleaseDAO dao = new ExamReleaseDAO();

    @Test
    void createGetByIdAndByTeacher() {
        Exam exam = ExecutionTestFixture.ensureApprovedExam();
        ExamRelease release = ExecutionTestFixture.createOpenRelease(exam, "2001");

        ExamRelease loaded = dao.getById(release.getId());
        assertThat(loaded).isNotNull();
        assertThat(loaded.getExecutionCode()).isEqualTo("2001");
        assertThat(dao.getByTeacher(exam.getTeacherId())).extracting(ExamRelease::getId)
                .contains(release.getId());
        assertThat(dao.getAll()).isNotEmpty();
    }

    @Test
    void openByExecutionCodeRespectsWindow() {
        Exam exam = ExecutionTestFixture.ensureApprovedExam();
        ExamRelease release = ExecutionTestFixture.createOpenRelease(exam, "2002");
        LocalDateTime now = LocalDateTime.now();

        assertThat(dao.getOpenByExecutionCode("2002", now).getId()).isEqualTo(release.getId());
        assertThat(dao.getByExecutionCode("2002").getId()).isEqualTo(release.getId());
        assertThat(dao.executionCodeExists("2002")).isTrue();
    }

    @Test
    void executionCodeConflictsWhenWindowsOverlap() {
        Exam exam = ExecutionTestFixture.ensureApprovedExam();
        LocalDateTime now = LocalDateTime.now();
        dao.create(new ExamRelease(exam.getId(), exam.getTeacherId(), "2003",
                now.minusHours(1), now.plusHours(1)));

        assertThat(dao.executionCodeConflicts("2003", now, now.plusMinutes(30))).isTrue();
        assertThat(dao.executionCodeConflicts("2003", now.plusHours(2), now.plusHours(3))).isFalse();
        assertThat(dao.executionCodeConflicts("9999", now, now.plusMinutes(30))).isFalse();
    }

    @Test
    void deleteRemovesRelease() {
        Exam exam = ExecutionTestFixture.ensureApprovedExam();
        ExamRelease release = ExecutionTestFixture.createOpenRelease(exam, "2004");
        // Snapshot rows reference release — wipe those first for this isolated delete check
        ExecutionTestFixture.execute("DELETE FROM ReleasedExamQuestions WHERE release_id=" + release.getId());
        dao.delete(release.getId());
        assertThat(dao.getById(release.getId())).isNull();
    }

    @Test
    void createReturnsNullWhenForeignKeyDoesNotExist() {
        LocalDateTime now = LocalDateTime.now();
        int teacherId = ExecutionTestFixture.userId("teacher");
        assertThat(dao.create(new ExamRelease(999_999, teacherId, "2099",
                now.minusHours(1), now.plusHours(1)))).isNull();
    }
}
