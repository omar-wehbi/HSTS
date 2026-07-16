package server;

import common.entities.Exam;
import common.entities.ExamQuestion;
import common.entities.Question;
import common.network.AutoExamRequest;
import common.network.AutoExamRequirement;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Unit tests for {@link AutoExamGenerator}.
 *
 * <p>Pure unit tests: no database, no server and no Mockito.
 * The question pool is created in memory.</p>
 */
class AutoExamGeneratorTest {

    private final AutoExamGenerator generator = new AutoExamGenerator();

    private static Question question(int id,
                                     int courseId,
                                     String topic,
                                     String difficulty,
                                     boolean current) {

        Question question = new Question(
                courseId,
                "Question " + id,
                "A",
                "B",
                "C",
                "D",
                1,
                null,
                topic,
                difficulty
        );

        question.setId(id);
        question.setBaseId(id);
        question.setVersion(1);
        question.setCurrent(current);

        return question;
    }

    private static AutoExamRequest validRequest() {
        return new AutoExamRequest(
                1,
                10,
                "Automatic Algorithms Exam",
                90,
                "Answer all questions.",
                "Generated automatically.",
                List.of(
                        new AutoExamRequirement(
                                "Sorting",
                                "EASY",
                                2,
                                20
                        ),
                        new AutoExamRequirement(
                                "Complexity",
                                "MEDIUM",
                                2,
                                30
                        )
                )
        );
    }

    private static List<Question> validPool() {
        return List.of(
                question(101, 1, "Sorting", "EASY", true),
                question(102, 1, "Sorting", "EASY", true),
                question(103, 1, "Sorting", "EASY", true),

                question(201, 1, "Complexity", "MEDIUM", true),
                question(202, 1, "Complexity", "MEDIUM", true),
                question(203, 1, "Complexity", "MEDIUM", true),

                question(301, 1, "Sorting", "HARD", true),
                question(401, 2, "Sorting", "EASY", true),
                question(501, 1, "Sorting", "EASY", false)
        );
    }

    @Test
    void validRequestGeneratesExam() {
        Exam exam = generator.generate(
                validRequest(),
                validPool()
        );

        assertThat(exam).isNotNull();
        assertThat(exam.getTitle())
                .isEqualTo("Automatic Algorithms Exam");
        assertThat(exam.getCourseId()).isEqualTo(1);
        assertThat(exam.getTeacherId()).isEqualTo(10);
        assertThat(exam.getDurationMinutes()).isEqualTo(90);
    }

    @Test
    void generatedExamContainsRequestedNumberOfQuestions() {
        Exam exam = generator.generate(
                validRequest(),
                validPool()
        );

        assertThat(exam.getQuestions()).hasSize(4);
    }

    @Test
    void generatedExamTotalsExactly100Points() {
        Exam exam = generator.generate(
                validRequest(),
                validPool()
        );

        assertThat(exam.getTotalPoints()).isEqualTo(100);
    }

    @Test
    void selectedQuestionsBelongOnlyToRequestedCourse() {
        List<Question> pool = new ArrayList<>(validPool());

        pool.add(question(
                900,
                2,
                "Sorting",
                "EASY",
                true
        ));

        Exam exam = generator.generate(
                validRequest(),
                pool
        );

        Set<Integer> selectedIds = exam.getQuestions()
                .stream()
                .map(ExamQuestion::getQuestionId)
                .collect(Collectors.toSet());

        assertThat(selectedIds).doesNotContain(401, 900);
    }

    @Test
    void retiredQuestionVersionsAreIgnored() {
        Exam exam = generator.generate(
                validRequest(),
                validPool()
        );

        Set<Integer> selectedIds = exam.getQuestions()
                .stream()
                .map(ExamQuestion::getQuestionId)
                .collect(Collectors.toSet());

        assertThat(selectedIds).doesNotContain(501);
    }

    @Test
    void generatorRespectsTopicAndDifficulty() {
        Exam exam = generator.generate(
                validRequest(),
                validPool()
        );

        Set<Integer> selectedIds = exam.getQuestions()
                .stream()
                .map(ExamQuestion::getQuestionId)
                .collect(Collectors.toSet());

        Set<Integer> allowedIds = Set.of(
                101, 102, 103,
                201, 202, 203
        );

        assertThat(selectedIds)
                .allMatch(allowedIds::contains);

        assertThat(selectedIds)
                .doesNotContain(301);
    }

    @Test
    void sameQuestionIsNeverSelectedTwice() {
        Exam exam = generator.generate(
                validRequest(),
                validPool()
        );

        List<Integer> ids = exam.getQuestions()
                .stream()
                .map(ExamQuestion::getQuestionId)
                .toList();

        assertThat(ids).doesNotHaveDuplicates();
    }

    @Test
    void positionsStartAtOneAndAreSequential() {
        Exam exam = generator.generate(
                validRequest(),
                validPool()
        );

        List<Integer> positions = exam.getQuestions()
                .stream()
                .map(ExamQuestion::getPosition)
                .toList();

        assertThat(positions)
                .containsExactly(1, 2, 3, 4);
    }

    @Test
    void pointsFollowTheRequirement() {
        Exam exam = generator.generate(
                validRequest(),
                validPool()
        );

        List<Integer> points = exam.getQuestions()
                .stream()
                .map(ExamQuestion::getPoints)
                .toList();

        assertThat(points)
                .containsExactly(20, 20, 30, 30);
    }

    @Test
    void insufficientPoolThrowsReadableError() {
        List<Question> smallPool = List.of(
                question(101, 1, "Sorting", "EASY", true),
                question(201, 1, "Complexity", "MEDIUM", true),
                question(202, 1, "Complexity", "MEDIUM", true)
        );

        assertThatIllegalArgumentException()
                .isThrownBy(() ->
                        generator.generate(
                                validRequest(),
                                smallPool
                        )
                )
                .withMessageContaining("Not enough questions")
                .withMessageContaining("Sorting")
                .withMessageContaining("EASY");
    }

    @Test
    void nullQuestionPoolIsRejected() {
        assertThatIllegalArgumentException()
                .isThrownBy(() ->
                        generator.generate(
                                validRequest(),
                                null
                        )
                )
                .withMessageContaining("pool");
    }

    @Test
    void invalidRequestIsRejectedBeforeGeneration() {
        AutoExamRequest request = validRequest();
        request.setDurationMinutes(0);

        assertThatIllegalArgumentException()
                .isThrownBy(() ->
                        generator.generate(
                                request,
                                validPool()
                        )
                )
                .withMessageContaining("duration");
    }

    @Test
    void nullQuestionsInsidePoolAreIgnored() {
        List<Question> pool = new ArrayList<>(validPool());
        pool.add(null);

        Exam exam = generator.generate(
                validRequest(),
                pool
        );

        assertThat(exam.getQuestions()).hasSize(4);
    }

    @Test
    void topicAndDifficultyMatchingIgnoresCaseAndSpaces() {
        List<Question> pool = List.of(
                question(101, 1, " sorting ", "easy", true),
                question(102, 1, "SORTING", "EASY", true),
                question(201, 1, " complexity ", "medium", true),
                question(202, 1, "COMPLEXITY", "MEDIUM", true)
        );

        Exam exam = generator.generate(
                validRequest(),
                pool
        );

        assertThat(exam.getQuestions()).hasSize(4);
        assertThat(exam.getTotalPoints()).isEqualTo(100);
    }
}