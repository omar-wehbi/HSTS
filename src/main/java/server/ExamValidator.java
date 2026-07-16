package server;

import common.entities.Exam;
import common.entities.ExamQuestion;
import common.entities.ExamStatus;
import common.network.AutoExamRequest;
import common.network.AutoExamRequirement;
import common.network.ExamRejectionRequest;

import java.util.HashSet;
import java.util.Set;

/**
 * Server-side validation rules for exam building and approval.
 *
 * <p>This class validates scenarios 3 and 4:
 * manual exam creation, automatic generation, exam submission,
 * approval and rejection.</p>
 *
 * <p>The validator is stateless. Every method returns a readable
 * error message, or {@code null} when the supplied data is valid.</p>
 */
public final class ExamValidator {

    private static final int REQUIRED_TOTAL_POINTS = 100;
    private static final int MAX_TITLE_LENGTH = 255;
    private static final int MAX_INSTRUCTIONS_LENGTH = 4000;
    private static final int MAX_TEACHER_NOTES_LENGTH = 4000;
    private static final int MAX_REJECTION_REASON_LENGTH = 2000;

    private ExamValidator() {
    }

    /**
     * Validates an exam that is being created or updated manually.
     *
     * @param exam exam payload
     * @return error message, or {@code null} when valid
     */
    public static String validateExam(Exam exam) {
        if (exam == null) {
            return "An exam payload is required.";
        }

        if (exam.getCourseId() <= 0) {
            return "The exam must belong to a course.";
        }

        if (exam.getTeacherId() <= 0) {
            return "The exam must belong to a teacher.";
        }

        if (isBlank(exam.getTitle())) {
            return "Exam title is required.";
        }

        if (exam.getTitle().length() > MAX_TITLE_LENGTH) {
            return "Exam title is too long (max "
                    + MAX_TITLE_LENGTH + " characters).";
        }

        if (exam.getDurationMinutes() <= 0) {
            return "Exam duration must be greater than zero.";
        }

        if (exam.getStudentInstructions() != null
                && exam.getStudentInstructions().length()
                > MAX_INSTRUCTIONS_LENGTH) {
            return "Student instructions are too long (max "
                    + MAX_INSTRUCTIONS_LENGTH + " characters).";
        }

        if (exam.getTeacherNotes() != null
                && exam.getTeacherNotes().length()
                > MAX_TEACHER_NOTES_LENGTH) {
            return "Teacher notes are too long (max "
                    + MAX_TEACHER_NOTES_LENGTH + " characters).";
        }

        if (exam.getStatus() == null) {
            return "Exam status is required.";
        }

        if (exam.getQuestions() == null || exam.getQuestions().isEmpty()) {
            return "The exam must contain at least one question.";
        }

        String questionError = validateQuestions(exam);
        if (questionError != null) {
            return questionError;
        }

        if (exam.getTotalPoints() != REQUIRED_TOTAL_POINTS) {
            return "The total exam points must equal "
                    + REQUIRED_TOTAL_POINTS + ".";
        }

        return null;
    }

    /**
     * Validates the questions connected to an exam.
     */
    private static String validateQuestions(Exam exam) {
        Set<Integer> questionIds = new HashSet<>();
        Set<Integer> positions = new HashSet<>();

        for (ExamQuestion examQuestion : exam.getQuestions()) {
            if (examQuestion == null) {
                return "Exam questions cannot contain null values.";
            }

            if (examQuestion.getQuestionId() <= 0) {
                return "Every exam question must reference a valid question.";
            }

            if (examQuestion.getPoints() <= 0) {
                return "Every exam question must have positive points.";
            }

            if (examQuestion.getPosition() <= 0) {
                return "Every exam question must have a positive position.";
            }

            if (!questionIds.add(examQuestion.getQuestionId())) {
                return "The same question cannot appear twice in one exam.";
            }

            if (!positions.add(examQuestion.getPosition())) {
                return "Two exam questions cannot have the same position.";
            }
        }

        return null;
    }

    /**
     * Validates a request for automatic exam generation.
     *
     * @param request automatic generation request
     * @return error message, or {@code null} when valid
     */
    public static String validateAutoRequest(AutoExamRequest request) {
        if (request == null) {
            return "An automatic exam request is required.";
        }

        if (request.getCourseId() <= 0) {
            return "The exam must belong to a course.";
        }

        if (request.getTeacherId() <= 0) {
            return "The exam must belong to a teacher.";
        }

        if (isBlank(request.getTitle())) {
            return "Exam title is required.";
        }

        if (request.getTitle().length() > MAX_TITLE_LENGTH) {
            return "Exam title is too long (max "
                    + MAX_TITLE_LENGTH + " characters).";
        }

        if (request.getDurationMinutes() <= 0) {
            return "Exam duration must be greater than zero.";
        }

        if (request.getStudentInstructions() != null
                && request.getStudentInstructions().length()
                > MAX_INSTRUCTIONS_LENGTH) {
            return "Student instructions are too long (max "
                    + MAX_INSTRUCTIONS_LENGTH + " characters).";
        }

        if (request.getTeacherNotes() != null
                && request.getTeacherNotes().length()
                > MAX_TEACHER_NOTES_LENGTH) {
            return "Teacher notes are too long (max "
                    + MAX_TEACHER_NOTES_LENGTH + " characters).";
        }

        if (request.getRequirements() == null
                || request.getRequirements().isEmpty()) {
            return "At least one automatic-generation requirement is required.";
        }

        Set<String> uniqueRequirements = new HashSet<>();

        for (AutoExamRequirement requirement : request.getRequirements()) {
            if (requirement == null) {
                return "Automatic-generation requirements cannot contain null values.";
            }

            if (isBlank(requirement.getTopic())) {
                return "Every automatic-generation requirement must include a topic.";
            }

            if (isBlank(requirement.getDifficulty())) {
                return "Every automatic-generation requirement must include a difficulty.";
            }

            String difficulty = requirement.getDifficulty().trim().toUpperCase();

            if (!difficulty.matches("EASY|MEDIUM|HARD")) {
                return "Difficulty must be EASY, MEDIUM or HARD.";
            }

            if (requirement.getQuestionCount() <= 0) {
                return "Question count must be greater than zero.";
            }

            if (requirement.getPointsPerQuestion() <= 0) {
                return "Points per question must be greater than zero.";
            }

            String key = requirement.getTopic().trim().toLowerCase()
                    + "|" + difficulty;

            if (!uniqueRequirements.add(key)) {
                return "Duplicate topic and difficulty requirement: "
                        + requirement.getTopic() + " / " + difficulty + ".";
            }
        }

        if (request.getTotalQuestionCount() <= 0) {
            return "The exam must request at least one question.";
        }

        if (request.getTotalPoints() != REQUIRED_TOTAL_POINTS) {
            return "The total automatic exam points must equal "
                    + REQUIRED_TOTAL_POINTS + ".";
        }

        return null;
    }

    /**
     * Validates whether an exam can be submitted for coordinator approval.
     */
    public static String validateSubmission(Exam exam) {
        String invalid = validateExam(exam);

        if (invalid != null) {
            return invalid;
        }

        if (exam.getStatus() != ExamStatus.DRAFT
                && exam.getStatus() != ExamStatus.REJECTED) {
            return "Only a draft or rejected exam can be submitted for approval.";
        }

        return null;
    }

    /**
     * Validates whether an exam can be approved.
     */
    public static String validateApproval(Exam exam) {
        if (exam == null) {
            return "The exam was not found.";
        }

        if (exam.getStatus() != ExamStatus.PENDING_APPROVAL) {
            return "Only an exam waiting for approval can be approved.";
        }

        return null;
    }

    /**
     * Validates a coordinator's exam rejection request.
     */
    public static String validateRejection(Exam exam,
                                           ExamRejectionRequest request) {
        if (exam == null) {
            return "The exam was not found.";
        }

        if (request == null) {
            return "An exam rejection request is required.";
        }

        if (request.getExamId() <= 0) {
            return "A valid exam ID is required.";
        }

        if (request.getExamId() != exam.getId()) {
            return "The rejection request does not match the selected exam.";
        }

        if (request.getCoordinatorId() <= 0) {
            return "A valid coordinator ID is required.";
        }

        if (!request.hasReason()) {
            return "A rejection reason is required.";
        }

        if (request.getReason().length() > MAX_REJECTION_REASON_LENGTH) {
            return "Rejection reason is too long (max "
                    + MAX_REJECTION_REASON_LENGTH + " characters).";
        }

        if (exam.getStatus() != ExamStatus.PENDING_APPROVAL) {
            return "Only an exam waiting for approval can be rejected.";
        }

        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}