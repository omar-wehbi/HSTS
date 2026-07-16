package server;

import common.entities.Exam;
import common.entities.ExamQuestion;
import common.entities.ExamStatus;
import common.network.AutoExamRequest;
import common.network.AutoExamRequirement;
import common.network.ExamRejectionRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ExamValidator}.
 *
 * <p>Pure logic tests: no database, no network and no Mockito.</p>
 */
class ExamValidatorTest {

    private static Exam validExam() {
        Exam exam = new Exam(
                1,
                10,
                "Algorithms Midterm",
                90,
                "Answer all questions.",
                "For teachers only.",
                List.of(
                        new ExamQuestion(101, 40, 1),
                        new ExamQuestion(102, 30, 2),
                        new ExamQuestion(103, 30, 3)
                )
        );

        exam.setStatus(ExamStatus.DRAFT);
        return exam;
    }

    private static AutoExamRequest validAutoRequest() {
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

    // ===== manual exam validation ========================================

    @Test
    void validExamPasses() {
        assertThat(
                ExamValidator.validateExam(validExam())
        ).isNull();
    }

    @Test
    void nullExamIsRejected() {
        assertThat(
                ExamValidator.validateExam(null)
        ).containsIgnoringCase("required");
    }

    @Test
    void blankTitleIsRejected() {
        Exam exam = validExam();
        exam.setTitle("   ");

        assertThat(
                ExamValidator.validateExam(exam)
        ).containsIgnoringCase("title");
    }

    @Test
    void missingCourseIsRejected() {
        Exam exam = validExam();
        exam.setCourseId(0);

        assertThat(
                ExamValidator.validateExam(exam)
        ).containsIgnoringCase("course");
    }

    @Test
    void missingTeacherIsRejected() {
        Exam exam = validExam();
        exam.setTeacherId(0);

        assertThat(
                ExamValidator.validateExam(exam)
        ).containsIgnoringCase("teacher");
    }

    @Test
    void zeroDurationIsRejected() {
        Exam exam = validExam();
        exam.setDurationMinutes(0);

        assertThat(
                ExamValidator.validateExam(exam)
        ).containsIgnoringCase("duration");
    }

    @Test
    void examMustContainQuestions() {
        Exam exam = validExam();
        exam.clearQuestions();

        assertThat(
                ExamValidator.validateExam(exam)
        ).containsIgnoringCase("question");
    }

    @Test
    void duplicateQuestionIsRejected() {
        Exam exam = validExam();

        exam.setQuestions(List.of(
                new ExamQuestion(101, 50, 1),
                new ExamQuestion(101, 50, 2)
        ));

        assertThat(
                ExamValidator.validateExam(exam)
        ).containsIgnoringCase("same question");
    }

    @Test
    void duplicatePositionIsRejected() {
        Exam exam = validExam();

        exam.setQuestions(List.of(
                new ExamQuestion(101, 50, 1),
                new ExamQuestion(102, 50, 1)
        ));

        assertThat(
                ExamValidator.validateExam(exam)
        ).containsIgnoringCase("same position");
    }

    @Test
    void nonPositivePointsAreRejected() {
        Exam exam = validExam();

        exam.setQuestions(List.of(
                new ExamQuestion(101, 0, 1),
                new ExamQuestion(102, 100, 2)
        ));

        assertThat(
                ExamValidator.validateExam(exam)
        ).containsIgnoringCase("positive points");
    }

    @Test
    void totalBelow100IsRejected() {
        Exam exam = validExam();

        exam.setQuestions(List.of(
                new ExamQuestion(101, 40, 1),
                new ExamQuestion(102, 40, 2)
        ));

        assertThat(
                ExamValidator.validateExam(exam)
        ).contains("100");
    }

    @Test
    void totalAbove100IsRejected() {
        Exam exam = validExam();

        exam.setQuestions(List.of(
                new ExamQuestion(101, 60, 1),
                new ExamQuestion(102, 60, 2)
        ));

        assertThat(
                ExamValidator.validateExam(exam)
        ).contains("100");
    }

    // ===== automatic generation request validation =======================

    @Test
    void validAutoRequestPasses() {
        assertThat(
                ExamValidator.validateAutoRequest(validAutoRequest())
        ).isNull();
    }

    @Test
    void autoRequestMustHaveRequirements() {
        AutoExamRequest request = validAutoRequest();
        request.setRequirements(List.of());

        assertThat(
                ExamValidator.validateAutoRequest(request)
        ).containsIgnoringCase("requirement");
    }

    @Test
    void invalidDifficultyIsRejected() {
        AutoExamRequest request = validAutoRequest();

        request.setRequirements(List.of(
                new AutoExamRequirement(
                        "Sorting",
                        "IMPOSSIBLE",
                        5,
                        20
                )
        ));

        assertThat(
                ExamValidator.validateAutoRequest(request)
        ).containsIgnoringCase("difficulty");
    }

    @Test
    void automaticRequestMustTotal100Points() {
        AutoExamRequest request = validAutoRequest();

        request.setRequirements(List.of(
                new AutoExamRequirement(
                        "Sorting",
                        "EASY",
                        3,
                        20
                )
        ));

        assertThat(
                ExamValidator.validateAutoRequest(request)
        ).contains("100");
    }

    @Test
    void duplicateTopicDifficultyRequirementIsRejected() {
        AutoExamRequest request = validAutoRequest();

        request.setRequirements(List.of(
                new AutoExamRequirement(
                        "Sorting",
                        "EASY",
                        2,
                        25
                ),
                new AutoExamRequirement(
                        " sorting ",
                        "easy",
                        2,
                        25
                )
        ));

        assertThat(
                ExamValidator.validateAutoRequest(request)
        ).containsIgnoringCase("duplicate");
    }

    // ===== approval workflow validation ==================================

    @Test
    void draftExamCanBeSubmittedForApproval() {
        Exam exam = validExam();
        exam.setStatus(ExamStatus.DRAFT);

        assertThat(
                ExamValidator.validateSubmission(exam)
        ).isNull();
    }

    @Test
    void approvedExamCannotBeSubmittedAgain() {
        Exam exam = validExam();
        exam.setStatus(ExamStatus.APPROVED);

        assertThat(
                ExamValidator.validateSubmission(exam)
        ).containsIgnoringCase("draft");
    }

    @Test
    void pendingExamCanBeApproved() {
        Exam exam = validExam();
        exam.setStatus(ExamStatus.PENDING_APPROVAL);

        assertThat(
                ExamValidator.validateApproval(exam)
        ).isNull();
    }

    @Test
    void draftExamCannotBeApproved() {
        Exam exam = validExam();
        exam.setStatus(ExamStatus.DRAFT);

        assertThat(
                ExamValidator.validateApproval(exam)
        ).containsIgnoringCase("waiting");
    }

    @Test
    void rejectionRequiresWrittenReason() {
        Exam exam = validExam();
        exam.setId(5);
        exam.setStatus(ExamStatus.PENDING_APPROVAL);

        ExamRejectionRequest request =
                new ExamRejectionRequest(
                        5,
                        20,
                        "   "
                );

        assertThat(
                ExamValidator.validateRejection(exam, request)
        ).containsIgnoringCase("reason");
    }

    @Test
    void validRejectionPasses() {
        Exam exam = validExam();
        exam.setId(5);
        exam.setStatus(ExamStatus.PENDING_APPROVAL);

        ExamRejectionRequest request =
                new ExamRejectionRequest(
                        5,
                        20,
                        "Not enough hard questions."
                );

        assertThat(
                ExamValidator.validateRejection(exam, request)
        ).isNull();
    }
}
