package server.db;

import common.entities.Question;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static server.db.QuestionBankTestFixture.COURSE_ALGORITHMS;
import static server.db.QuestionBankTestFixture.COURSE_DATABASES;

/**
 * Scenario 2.3 — viewing the question bank (Person 2, Phase 2).
 * Locks down the read queries: retired versions never leak, course filtering
 * is exact, and empty results are empty lists (never null).
 */
class QuestionDAOReadTest extends QuestionDaoTestBase {

    @Test
    void bankShowsOnlyCurrentVersions() {
        Question v1 = dao.add(QuestionBankTestFixture.sample(COURSE_ALGORITHMS, "old"));
        Question edit = QuestionBankTestFixture.sample(COURSE_ALGORITHMS, "new");
        edit.setBaseId(v1.getBaseId());
        dao.update(edit);

        assertThat(dao.getAllCurrent())
                .hasSize(1)
                .first().extracting(Question::getQuestionText).isEqualTo("new");
    }

    @Test
    void courseQueryFiltersByCourseAndExcludesRetiredVersions() {
        QuestionBankTestFixture.insertN(dao, 2, COURSE_ALGORITHMS);
        Question dbQ = dao.add(QuestionBankTestFixture.sample(COURSE_DATABASES, "db v1"));
        Question edit = QuestionBankTestFixture.sample(COURSE_DATABASES, "db v2");
        edit.setBaseId(dbQ.getBaseId());
        dao.update(edit);

        assertThat(dao.getByCourse(COURSE_ALGORITHMS)).hasSize(2);
        assertThat(dao.getByCourse(COURSE_DATABASES))
                .hasSize(1)
                .first().extracting(Question::getQuestionText).isEqualTo("db v2");
    }

    @Test
    void emptyBankMeansEmptyListsNotNull() {
        assertThat(dao.getAllCurrent()).isNotNull().isEmpty();
        assertThat(dao.getByCourse(COURSE_ALGORITHMS)).isNotNull().isEmpty();
        assertThat(dao.getHistory(12345)).isNotNull().isEmpty();
    }
}
