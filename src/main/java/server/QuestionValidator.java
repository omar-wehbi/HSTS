package server;

import common.entities.Question;

/**
 * Server-side content rules for scenario 2 (Person 2, Phase 5): a question is
 * text + <b>four</b> answers + a correct answer in 1..4 + an optional
 * illustration, and belongs to exactly one course.
 *
 * <p><b>Pattern: Strategy (validation)</b> — one stateless rule set returning
 * an error message (or {@code null} when valid), reused verbatim by the ADD and
 * UPDATE paths in {@link QuestionService}, and available to any future module
 * that composes questions (e.g. the study bot referencing bank content).
 * The client performs the same checks for fast feedback, but the server is the
 * gatekeeper — a hand-crafted client cannot bypass these rules.
 *
 * <p>Length limits mirror the schema (V1/V5) so bad input fails here with a
 * readable message instead of as a MySQL truncation/ENUM error.
 */
public final class QuestionValidator {

    /** Illustrations above this size are refused (NFR 18 — keep the wire sane). */
    public static final int MAX_IMAGE_BYTES = 2 * 1024 * 1024;

    private static final int MAX_TOPIC_LENGTH = 255;     // topic VARCHAR(255)
    private static final int MAX_IMAGE_NAME_LENGTH = 512; // image_path VARCHAR(512)

    private QuestionValidator() { }

    /**
     * @return a human-readable error for the client, or {@code null} if the
     *         question satisfies every scenario-2 content rule.
     */
    public static String validate(Question q) {
        if (q == null) return "A question payload is required.";
        if (q.getCourseId() <= 0) return "The question must belong to a course.";
        if (isBlank(q.getQuestionText())) return "Question text is required.";
        if (isBlank(q.getAnswer1()) || isBlank(q.getAnswer2())
                || isBlank(q.getAnswer3()) || isBlank(q.getAnswer4())) {
            return "All 4 answers are required.";
        }
        if (q.getCorrectAnswer() < 1 || q.getCorrectAnswer() > 4) {
            return "The correct answer must be between 1 and 4.";
        }
        if (q.getDifficulty() != null && !q.getDifficulty().matches("EASY|MEDIUM|HARD")) {
            return "Difficulty must be EASY, MEDIUM or HARD.";
        }
        if (q.getTopic() != null && q.getTopic().length() > MAX_TOPIC_LENGTH) {
            return "Topic is too long (max " + MAX_TOPIC_LENGTH + " characters).";
        }
        if (q.getImagePath() != null && q.getImagePath().length() > MAX_IMAGE_NAME_LENGTH) {
            return "Illustration file name is too long (max " + MAX_IMAGE_NAME_LENGTH + " characters).";
        }
        if (q.getImageData() != null && q.getImageData().length > MAX_IMAGE_BYTES) {
            return "Illustration is too large (max " + (MAX_IMAGE_BYTES / 1024 / 1024) + " MB).";
        }
        if (q.getImageData() != null && isBlank(q.getImagePath())) {
            return "Illustration file name is missing.";
        }
        return null;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
