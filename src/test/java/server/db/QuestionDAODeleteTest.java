package server.db;

import common.entities.Question;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static server.db.QuestionBankTestFixture.COURSE_ALGORITHMS;

/**
 * Scenario 2.4 — deleting a question removes its whole version family
 * (Person 2, Phase 2). Locks down {@link QuestionDAO#delete}.
 */
class QuestionDAODeleteTest extends QuestionDaoTestBase {

    @Test
    void deleteRemovesEveryVersionOfTheFamily() {
        Question v1 = dao.add(QuestionBankTestFixture.sample(COURSE_ALGORITHMS, "v1"));
        Question edit = QuestionBankTestFixture.sample(COURSE_ALGORITHMS, "v2");
        edit.setBaseId(v1.getBaseId());
        dao.update(edit);

        boolean deleted = dao.delete(v1.getBaseId());

        assertThat(deleted).isTrue();
        assertThat(dao.getHistory(v1.getBaseId()))
                .as("no version of the family may survive")
                .isEmpty();
        assertThat(dao.getAllCurrent()).isEmpty();
    }

    @Test
    void deleteOfUnknownFamilyReturnsFalse() {
        assertThat(dao.delete(424242)).isFalse();
    }

    @Test
    void deleteTouchesOnlyTheTargetFamily() {
        Question doomed = dao.add(QuestionBankTestFixture.sample(COURSE_ALGORITHMS, "doomed"));
        Question survivor = dao.add(QuestionBankTestFixture.sample(COURSE_ALGORITHMS, "survivor"));

        dao.delete(doomed.getBaseId());

        assertThat(dao.getAllCurrent())
                .extracting(Question::getId)
                .containsExactly(survivor.getId());
        assertThat(dao.getHistory(survivor.getBaseId())).hasSize(1);
    }
}
