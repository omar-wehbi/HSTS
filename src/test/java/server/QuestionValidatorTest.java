package server;

import common.entities.Question;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link QuestionValidator} (Person 2, Phase 5) — the server-side
 * content rules for scenario 2 (question = text + 4 answers + correct answer
 * + optional illustration, belongs to one course). Pure unit: no DB, no mocks.
 */
class QuestionValidatorTest {

    private static Question valid() {
        Question q = new Question(1, "What is 2+2?",
                "3", "4", "5", "22", 2, null, "Arithmetic", "EASY");
        return q;
    }

    // ===== happy path =====================================================

    @Test
    void aFullyValidQuestionPasses() {
        assertThat(QuestionValidator.validate(valid())).isNull();
    }

    @Test
    void topicDifficultyAndImageAreOptional() {
        Question q = valid();
        q.setTopic(null);
        q.setDifficulty(null);
        q.setImagePath(null);
        q.setImageData(null);
        assertThat(QuestionValidator.validate(q)).isNull();
    }

    // ===== structural rules ==============================================

    @Test
    void nullQuestionIsRejected() {
        assertThat(QuestionValidator.validate(null)).contains("required");
    }

    @Test
    void missingCourseIsRejected() {
        Question q = valid();
        q.setCourseId(0);
        assertThat(QuestionValidator.validate(q)).containsIgnoringCase("course");
    }

    @Test
    void blankQuestionTextIsRejected() {
        Question q = valid();
        q.setQuestionText("   ");
        assertThat(QuestionValidator.validate(q)).containsIgnoringCase("text");
    }

    @Test
    void everyAnswerMustBeFilled() {
        for (int i = 1; i <= 4; i++) {
            Question q = valid();
            switch (i) {
                case 1 -> q.setAnswer1("");
                case 2 -> q.setAnswer2(null);
                case 3 -> q.setAnswer3("  ");
                case 4 -> q.setAnswer4("");
            }
            assertThat(QuestionValidator.validate(q))
                    .as("answer %d blank must be rejected", i)
                    .containsIgnoringCase("answer");
        }
    }

    @Test
    void correctAnswerMustBeBetween1And4() {
        Question q = valid();
        q.setCorrectAnswer(0);
        assertThat(QuestionValidator.validate(q)).containsIgnoringCase("correct");
        q.setCorrectAnswer(5);
        assertThat(QuestionValidator.validate(q)).containsIgnoringCase("correct");
    }

    @Test
    void difficultyMustMatchTheSchemaEnum() {
        Question q = valid();
        q.setDifficulty("IMPOSSIBLE");
        assertThat(QuestionValidator.validate(q)).containsIgnoringCase("difficulty");
    }

    // ===== column limits (fail before MySQL truncates/errors) ============

    @Test
    void overlongTopicIsRejected() {
        Question q = valid();
        q.setTopic("t".repeat(256));
        assertThat(QuestionValidator.validate(q)).containsIgnoringCase("topic");
    }

    @Test
    void overlongImageNameIsRejected() {
        Question q = valid();
        q.setImagePath("f".repeat(513));
        assertThat(QuestionValidator.validate(q)).containsIgnoringCase("file name");
    }

    // ===== illustration rules =============================================

    @Test
    void oversizedIllustrationIsRejected() {
        Question q = valid();
        q.setImagePath("big.png");
        q.setImageData(new byte[QuestionValidator.MAX_IMAGE_BYTES + 1]);
        assertThat(QuestionValidator.validate(q)).containsIgnoringCase("large");
    }

    @Test
    void illustrationAtTheLimitIsAccepted() {
        Question q = valid();
        q.setImagePath("ok.png");
        q.setImageData(new byte[QuestionValidator.MAX_IMAGE_BYTES]);
        assertThat(QuestionValidator.validate(q)).isNull();
    }

    @Test
    void imageBytesWithoutAFileNameAreRejected() {
        Question q = valid();
        q.setImagePath(null);
        q.setImageData(new byte[]{1, 2, 3});
        assertThat(QuestionValidator.validate(q)).containsIgnoringCase("file name");
    }
}
