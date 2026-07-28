package client.ui.exam;

import common.entities.Exam;
import common.entities.ExamQuestion;
import common.network.AutoExamRequest;
import common.network.AutoExamRequirement;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Client-side validation for exam builder forms (Person 5).
 *
 * <p>Mirrors the essential rules of {@code server.ExamValidator} so the UI can
 * block invalid sends before round-tripping to the server. Returns a readable
 * error message, or {@code null} when valid.
 */
public final class ExamFormValidator {

    public static final int REQUIRED_TOTAL_POINTS = 100;

    private ExamFormValidator() {
    }

    /**
     * Validates a draft exam about to be created or updated.
     *
     * @param exam exam built from the form (teacherId / courseId should be set)
     * @return error text, or null if OK
     */
    public static String validateManualExam(Exam exam) {
        if (exam == null) {
            return "An exam is required.";
        }
        if (exam.getCourseId() <= 0) {
            return "Please choose a course.";
        }
        if (isBlank(exam.getTitle())) {
            return "Exam title is required.";
        }
        if (exam.getDurationMinutes() <= 0) {
            return "Duration must be greater than zero.";
        }
        List<ExamQuestion> questions = exam.getQuestions();
        if (questions == null || questions.isEmpty()) {
            return "Add at least one question.";
        }

        Set<Integer> questionIds = new HashSet<>();
        Set<Integer> positions = new HashSet<>();
        for (ExamQuestion eq : questions) {
            if (eq == null) {
                return "Exam questions cannot contain empty rows.";
            }
            if (eq.getQuestionId() <= 0) {
                return "Every row must reference a question.";
            }
            if (eq.getPoints() <= 0) {
                return "Every question must have positive points.";
            }
            if (eq.getPosition() <= 0) {
                return "Every question must have a positive position.";
            }
            if (!questionIds.add(eq.getQuestionId())) {
                return "The same question cannot appear twice.";
            }
            if (!positions.add(eq.getPosition())) {
                return "Two questions cannot share the same position.";
            }
        }

        if (totalPoints(questions) != REQUIRED_TOTAL_POINTS) {
            return "Total points must equal " + REQUIRED_TOTAL_POINTS
                    + " (currently " + totalPoints(questions) + ").";
        }
        return null;
    }

    /** Validates an automatic-generation form before {@code GENERATE_EXAM_AUTO}. */
    public static String validateAutoRequest(AutoExamRequest request) {
        if (request == null) {
            return "An automatic exam request is required.";
        }
        if (request.getCourseId() <= 0) {
            return "Please choose a course.";
        }
        if (isBlank(request.getTitle())) {
            return "Exam title is required.";
        }
        if (request.getDurationMinutes() <= 0) {
            return "Duration must be greater than zero.";
        }
        if (request.getRequirements() == null || request.getRequirements().isEmpty()) {
            return "Add at least one topic / difficulty requirement.";
        }

        Set<String> unique = new HashSet<>();
        for (AutoExamRequirement req : request.getRequirements()) {
            if (req == null) {
                return "Requirements cannot contain empty rows.";
            }
            if (isBlank(req.getTopic())) {
                return "Every requirement needs a topic.";
            }
            if (isBlank(req.getDifficulty())) {
                return "Every requirement needs a difficulty.";
            }
            String difficulty = req.getDifficulty().trim().toUpperCase();
            if (!difficulty.matches("EASY|MEDIUM|HARD")) {
                return "Difficulty must be EASY, MEDIUM or HARD.";
            }
            if (req.getQuestionCount() <= 0) {
                return "Question count must be greater than zero.";
            }
            if (req.getPointsPerQuestion() <= 0) {
                return "Points per question must be greater than zero.";
            }
            String key = req.getTopic().trim().toLowerCase() + "|" + difficulty;
            if (!unique.add(key)) {
                return "Duplicate topic and difficulty: " + req.getTopic() + " / " + difficulty + ".";
            }
        }

        if (request.getTotalPoints() != REQUIRED_TOTAL_POINTS) {
            return "Total automatic points must equal " + REQUIRED_TOTAL_POINTS
                    + " (currently " + request.getTotalPoints() + ").";
        }
        return null;
    }

    /** Coordinator rejection: non-empty reason required. */
    public static String validateRejectionReason(String reason) {
        if (isBlank(reason)) {
            return "A rejection reason is required.";
        }
        if (reason.length() > 2000) {
            return "Rejection reason is too long (max 2000 characters).";
        }
        return null;
    }

    public static int totalPoints(List<ExamQuestion> questions) {
        if (questions == null) return 0;
        int total = 0;
        for (ExamQuestion eq : questions) {
            if (eq != null) total += eq.getPoints();
        }
        return total;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
