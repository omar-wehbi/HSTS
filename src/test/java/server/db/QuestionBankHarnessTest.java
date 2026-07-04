package server.db;

import common.entities.Question;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static server.db.QuestionBankTestFixture.COURSE_ALGORITHMS;
import static server.db.QuestionBankTestFixture.COURSE_DATABASES;

/**
 * Smoke test for the Phase 1 harness itself: proves the wipe/reseed lifecycle
 * and the fixture builders behave before the real DAO suites (Phase 2) rely on
 * them. Needs MySQL — see {@link QuestionDaoTestBase} for the prerequisites.
 */
class QuestionBankHarnessTest extends QuestionDaoTestBase {

    @Test
    void bankStartsEmptyForEveryTest() {
        assertThat(dao.getAllCurrent()).isEmpty();
    }

    @Test
    void sampleQuestionIsValidAndInsertable() {
        Question saved = dao.add(QuestionBankTestFixture.sample(COURSE_ALGORITHMS));
        assertThat(saved).isNotNull();
        assertThat(saved.getId()).isPositive();
        assertThat(dao.getAllCurrent()).hasSize(1);
    }

    @Test
    void insertNCreatesDistinctQuestionsPerCourse() {
        QuestionBankTestFixture.insertN(dao, 3, COURSE_ALGORITHMS);
        QuestionBankTestFixture.insertN(dao, 2, COURSE_DATABASES);

        List<Question> algo = dao.getByCourse(COURSE_ALGORITHMS);
        assertThat(algo).hasSize(3);
        assertThat(dao.getByCourse(COURSE_DATABASES)).hasSize(2);
        assertThat(algo).extracting(Question::getQuestionText).doesNotHaveDuplicates();
    }
}
