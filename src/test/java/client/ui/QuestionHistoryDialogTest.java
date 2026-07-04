package client.ui;

import common.entities.Question;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link QuestionHistoryDialog}'s pure label helpers (Person 2,
 * Phase 6). The helpers are deliberately static and JavaFX-free so the
 * dialog's *content* logic is verifiable here without booting an FX toolkit.
 *
 * <p><b>Why not TestFX for the dialog itself?</b> TestFX must launch a real FX
 * toolkit; on headless machines (WSL, CI) that needs the Monocle glass stub and
 * per-machine setup. Screen-level UI testing is Person 5's lane with
 * {@code FakeClientConnection}; here we keep everything testable *below* the
 * scene graph and verify the on-screen behaviour in the manual demo script.
 */
class QuestionHistoryDialogTest {

    private static Question q() {
        Question q = new Question(1, "Which structure is FIFO?",
                "Stack", "Queue", "Tree", "Graph", 2, null, "Data Structures", "EASY");
        q.setVersion(2);
        q.setCurrent(true);
        return q;
    }

    @Test
    void versionSummaryMarksCurrentAndRetired() {
        Question current = q();
        assertThat(QuestionHistoryDialog.versionSummary(current)).isEqualTo("v2 · current");

        Question retired = q();
        retired.setVersion(1);
        retired.setCurrent(false);
        assertThat(QuestionHistoryDialog.versionSummary(retired)).isEqualTo("v1 · retired");
    }

    @Test
    void answerLinesTickExactlyTheCorrectAnswer() {
        Question question = q();   // correct = 2
        assertThat(QuestionHistoryDialog.answerLine(question, 1)).doesNotContain("✔").contains("1. Stack");
        assertThat(QuestionHistoryDialog.answerLine(question, 2)).startsWith("✔").contains("2. Queue");
        assertThat(QuestionHistoryDialog.answerLine(question, 3)).doesNotContain("✔").contains("3. Tree");
        assertThat(QuestionHistoryDialog.answerLine(question, 4)).doesNotContain("✔").contains("4. Graph");
    }

    @Test
    void metaLineHandlesMissingParts() {
        Question full = q();
        assertThat(QuestionHistoryDialog.metaLine(full))
                .isEqualTo("Topic: Data Structures · Difficulty: EASY");

        Question topicOnly = q();
        topicOnly.setDifficulty(null);
        assertThat(QuestionHistoryDialog.metaLine(topicOnly)).isEqualTo("Topic: Data Structures");

        Question bare = q();
        bare.setTopic(null);
        bare.setDifficulty(null);
        assertThat(QuestionHistoryDialog.metaLine(bare)).isEmpty();
    }
}
