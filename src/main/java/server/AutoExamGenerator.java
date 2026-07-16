package server;

import common.entities.Exam;
import common.entities.ExamQuestion;
import common.entities.Question;
import common.network.AutoExamRequest;
import common.network.AutoExamRequirement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds an exam automatically from a pool of available questions.
 *
 * <p>The generator selects questions according to topic and difficulty
 * requirements. It never selects the same question twice and fails
 * with a clear message when the available pool is too small.</p>
 */
public class AutoExamGenerator {

    /**
     * Generates a complete exam from an automatic generation request.
     *
     * @param request automatic exam requirements
     * @param availableQuestions questions available for the selected course
     * @return generated exam
     * @throws IllegalArgumentException when the request is invalid
     *                                  or the question pool is too small
     */
    public Exam generate(AutoExamRequest request,
                         List<Question> availableQuestions) {

        String validationError = ExamValidator.validateAutoRequest(request);

        if (validationError != null) {
            throw new IllegalArgumentException(validationError);
        }

        if (availableQuestions == null) {
            throw new IllegalArgumentException(
                    "The available question pool is required.");
        }

        List<Question> courseQuestions =
                filterCurrentCourseQuestions(
                        request.getCourseId(),
                        availableQuestions
                );

        List<ExamQuestion> selectedQuestions = new ArrayList<>();
        Set<Integer> selectedQuestionIds = new HashSet<>();

        int position = 1;

        for (AutoExamRequirement requirement : request.getRequirements()) {

            List<Question> matchingQuestions =
                    findMatchingQuestions(
                            courseQuestions,
                            requirement,
                            selectedQuestionIds
                    );

            if (matchingQuestions.size()
                    < requirement.getQuestionCount()) {

                throw new IllegalArgumentException(
                        buildInsufficientPoolMessage(
                                requirement,
                                matchingQuestions.size()
                        )
                );
            }

            /*
             * Shuffle so different valid exams may be generated
             * from the same question pool.
             */
            Collections.shuffle(matchingQuestions);

            for (int i = 0;
                 i < requirement.getQuestionCount();
                 i++) {

                Question selected = matchingQuestions.get(i);

                ExamQuestion examQuestion =
                        new ExamQuestion(
                                selected.getId(),
                                requirement.getPointsPerQuestion(),
                                position
                        );

                selectedQuestions.add(examQuestion);
                selectedQuestionIds.add(selected.getId());

                position++;
            }
        }

        Exam exam = new Exam(
                request.getCourseId(),
                request.getTeacherId(),
                request.getTitle(),
                request.getDurationMinutes(),
                request.getStudentInstructions(),
                request.getTeacherNotes(),
                selectedQuestions
        );

        String examValidationError =
                ExamValidator.validateExam(exam);

        if (examValidationError != null) {
            throw new IllegalArgumentException(
                    examValidationError);
        }

        return exam;
    }

    /**
     * Keeps only questions that belong to the selected course
     * and are the current version.
     */
    private List<Question> filterCurrentCourseQuestions(
            int courseId,
            List<Question> availableQuestions) {

        List<Question> result = new ArrayList<>();

        for (Question question : availableQuestions) {

            if (question == null) {
                continue;
            }

            if (question.getCourseId() != courseId) {
                continue;
            }

            if (!question.isCurrent()) {
                continue;
            }

            result.add(question);
        }

        return result;
    }

    /**
     * Finds questions matching one topic and difficulty requirement.
     */
    private List<Question> findMatchingQuestions(
            List<Question> courseQuestions,
            AutoExamRequirement requirement,
            Set<Integer> selectedQuestionIds) {

        List<Question> result = new ArrayList<>();

        String requiredTopic =
                normalize(requirement.getTopic());

        String requiredDifficulty =
                normalize(requirement.getDifficulty());

        for (Question question : courseQuestions) {

            if (selectedQuestionIds.contains(
                    question.getId())) {
                continue;
            }

            String questionTopic =
                    normalize(question.getTopic());

            String questionDifficulty =
                    normalize(question.getDifficulty());

            if (!requiredTopic.equals(questionTopic)) {
                continue;
            }

            if (!requiredDifficulty.equals(
                    questionDifficulty)) {
                continue;
            }

            result.add(question);
        }

        return result;
    }

    /**
     * Creates a readable failure message when the pool
     * does not contain enough matching questions.
     */
    private String buildInsufficientPoolMessage(
            AutoExamRequirement requirement,
            int availableCount) {

        return "Not enough questions for topic '"
                + requirement.getTopic()
                + "' and difficulty '"
                + requirement.getDifficulty()
                + "'. Requested "
                + requirement.getQuestionCount()
                + ", but only "
                + availableCount
                + " are available.";
    }

    private String normalize(String value) {

        if (value == null) {
            return "";
        }

        return value.trim().toUpperCase();
    }
}
