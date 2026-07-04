package server.db;

import common.entities.Question;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static server.db.QuestionBankTestFixture.COURSE_ALGORITHMS;

/**
 * Scenario 2.2 — editing keeps the previous version in the bank
 * (Person 2, Phase 2). The acceptance spec's exact words: "השאלה בגרסה
 * הקודמת נשארת במאגר השאלות" — the old version STAYS.
 *
 * <p>Locks down {@link QuestionDAO#update}: version chain growth, single
 * current version per family, ordered history, and the all-or-nothing
 * transaction guarantee when the new version cannot be inserted.
 */
class QuestionDAOUpdateTest extends QuestionDaoTestBase {

    @Test
    void updateKeepsTheOldVersionInTheBank() {
        Question v1 = dao.add(QuestionBankTestFixture.sample(COURSE_ALGORITHMS, "Original text"));

        Question edit = QuestionBankTestFixture.sample(COURSE_ALGORITHMS, "Edited text");
        edit.setBaseId(v1.getBaseId());
        Question v2 = dao.update(edit);

        assertThat(v2).isNotNull();
        assertThat(v2.getVersion()).isEqualTo(2);
        assertThat(v2.isCurrent()).isTrue();
        assertThat(v2.getBaseId()).isEqualTo(v1.getBaseId());

        List<Question> history = dao.getHistory(v1.getBaseId());
        assertThat(history).as("v1 must still exist after the edit").hasSize(2);
        assertThat(history.get(0).getQuestionText()).isEqualTo("Original text");
        assertThat(history.get(0).isCurrent()).as("old version retired, not deleted").isFalse();
        assertThat(history.get(1).getQuestionText()).isEqualTo("Edited text");
    }

    @Test
    void repeatedEditsGrowTheChainWithExactlyOneCurrent() {
        Question v1 = dao.add(QuestionBankTestFixture.sample(COURSE_ALGORITHMS, "v1"));

        for (int v = 2; v <= 4; v++) {
            Question edit = QuestionBankTestFixture.sample(COURSE_ALGORITHMS, "v" + v);
            edit.setBaseId(v1.getBaseId());
            dao.update(edit);
        }

        List<Question> history = dao.getHistory(v1.getBaseId());
        assertThat(history).hasSize(4);
        assertThat(history).extracting(Question::getVersion).containsExactly(1, 2, 3, 4);
        assertThat(history).filteredOn(Question::isCurrent)
                .as("exactly one current version per family")
                .hasSize(1)
                .first().extracting(Question::getQuestionText).isEqualTo("v4");

        assertThat(dao.getAllCurrent())
                .as("the bank shows one row per family, the latest")
                .hasSize(1)
                .first().extracting(Question::getQuestionText).isEqualTo("v4");
    }

    @Test
    void historyIsOrderedOldestFirst() {
        Question v1 = dao.add(QuestionBankTestFixture.sample(COURSE_ALGORITHMS, "first"));
        Question edit = QuestionBankTestFixture.sample(COURSE_ALGORITHMS, "second");
        edit.setBaseId(v1.getBaseId());
        dao.update(edit);

        assertThat(dao.getHistory(v1.getBaseId()))
                .extracting(Question::getVersion)
                .isSorted();
    }

    @Test
    void failedUpdateRollsBackAndTheOldVersionStaysCurrent() {
        Question v1 = dao.add(QuestionBankTestFixture.sample(COURSE_ALGORITHMS, "safe"));

        // Sabotage step 3 of the transaction: the new row violates the course FK,
        // so the INSERT fails after step 1 already retired v1. Without the
        // rollback the family would be left with NO current version.
        Question broken = QuestionBankTestFixture.sample(9999, "broken");
        broken.setBaseId(v1.getBaseId());
        Question result = dao.update(broken);

        assertThat(result).isNull();
        List<Question> history = dao.getHistory(v1.getBaseId());
        assertThat(history).as("no new version may exist").hasSize(1);
        assertThat(history.get(0).isCurrent())
                .as("rollback must restore v1 as the current version")
                .isTrue();
        assertThat(dao.getAllCurrent()).hasSize(1);
    }
}
