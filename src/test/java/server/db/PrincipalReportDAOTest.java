package server.db;

import common.entities.Exam;
import common.network.PrincipalData;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PrincipalReportDAOTest extends ExecutionDaoTestBase {

    private final PrincipalReportDAO dao = new PrincipalReportDAO();

    @Test
    void catalogListsRolesAndCourses() {
        QuestionBankTestFixture.ensureCourses();
        PrincipalData data = dao.getCatalog();
        assertThat(data.getTeachers()).isNotEmpty();
        assertThat(data.getCourses()).isNotEmpty();
        assertThat(data.getStudents()).isNotEmpty();
        assertThat(data.getGradedAttempts()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void visibleGradeRecordsIncludeApprovedOnly() {
        Exam exam = ExecutionTestFixture.ensureApprovedExam();
        var release = ExecutionTestFixture.createOpenRelease(exam, "6001");
        int maya = ExecutionTestFixture.userId("maya");
        var session = ExecutionTestFixture.startSession(release, maya, 15);
        new ExamSessionDAO().submit(session.getId(),
                java.util.List.of(new common.entities.StudentAnswer(
                        exam.getQuestions().get(0).getQuestionId(), 2)),
                java.time.LocalDateTime.now(),
                common.entities.ExamSessionStatus.SUBMITTED);
        var grade = ExecutionTestFixture.autoGrade(session, 88);
        new GradeDAO().approve(grade.getId(), ExecutionTestFixture.userId("teacher"),
                java.time.LocalDateTime.now());

        assertThat(dao.getVisibleGradeRecords())
                .anyMatch(r -> r.getStudentId() == maya && r.getScore() == 88);
    }
}
