package server.db;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PrincipalReadOnlyDAOTest extends ExecutionDaoTestBase {

    private final PrincipalReadOnlyDAO dao = new PrincipalReadOnlyDAO();

    @Test
    void loadReturnsFiveBuckets() {
        QuestionBankTestFixture.ensureCourses();
        ExecutionTestFixture.ensureApprovedExam();
        var data = dao.load();
        assertThat(data.getQuestions()).isNotNull();
        assertThat(data.getExams()).isNotEmpty();
        assertThat(data.getReleases()).isNotNull();
        assertThat(data.getSessions()).isNotNull();
        assertThat(data.getResults()).isNotNull();
    }
}
