package client.ui.exam;

import common.entities.Exam;
import common.entities.ExamQuestion;
import common.entities.ExamStatus;
import common.network.AutoExamRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure-Java tests for exam form helpers (Person 5, TDD — no JavaFX).
 */
class ExamFormValidatorTest {

    @Test
    void totalPointsSumsRows() {
        assertThat(ExamFormValidator.totalPoints(List.of(
                new ExamQuestion(1, 40, 1),
                new ExamQuestion(2, 60, 2)
        ))).isEqualTo(100);
        assertThat(ExamFormValidator.totalPoints(null)).isEqualTo(0);
        assertThat(ExamFormValidator.totalPoints(List.of())).isEqualTo(0);
    }

    @Test
    void validateManualExamRejectsEmptyTitleAndBadDuration() {
        Exam exam = validExam();
        exam.setTitle("  ");
        assertThat(ExamFormValidator.validateManualExam(exam)).contains("title");

        exam = validExam();
        exam.setDurationMinutes(0);
        assertThat(ExamFormValidator.validateManualExam(exam)).contains("Duration");
    }

    @Test
    void validateManualExamRejectsWrongPointTotal() {
        Exam exam = validExam();
        exam.getQuestions().get(0).setPoints(10);
        assertThat(ExamFormValidator.validateManualExam(exam))
                .contains("Total points must equal 100");
    }

    @Test
    void validateManualExamRejectsDuplicateQuestionIds() {
        Exam exam = validExam();
        exam.getQuestions().get(1).setQuestionId(10);
        assertThat(ExamFormValidator.validateManualExam(exam))
                .contains("same question");
    }

    @Test
    void validateManualExamAcceptsValidHundredPointExam() {
        assertThat(ExamFormValidator.validateManualExam(validExam())).isNull();
    }

    @Test
    void validateAutoRequestRequiresHundredPoints() {
        AutoExamRequest bad = new AutoExamRequestBuilder()
                .courseId(1).teacherId(1).title("Quiz").durationMinutes(60)
                .addRequirement("Sorting", "EASY", 2, 20)
                .build();
        assertThat(ExamFormValidator.validateAutoRequest(bad)).contains("100");

        AutoExamRequest ok = new AutoExamRequestBuilder()
                .courseId(1).teacherId(1).title("Quiz").durationMinutes(60)
                .addRequirement("Sorting", "EASY", 2, 25)
                .addRequirement("Complexity", "MEDIUM", 2, 25)
                .build();
        assertThat(ExamFormValidator.validateAutoRequest(ok)).isNull();
        assertThat(ok.getTotalPoints()).isEqualTo(100);
    }

    @Test
    void validateRejectionReason() {
        assertThat(ExamFormValidator.validateRejectionReason("  ")).contains("required");
        assertThat(ExamFormValidator.validateRejectionReason("Too hard")).isNull();
    }

    private static Exam validExam() {
        Exam exam = new Exam();
        exam.setCourseId(1);
        exam.setTeacherId(1);
        exam.setTitle("Midterm");
        exam.setDurationMinutes(90);
        exam.setStatus(ExamStatus.DRAFT);
        exam.setQuestions(List.of(
                new ExamQuestion(10, 50, 1),
                new ExamQuestion(11, 50, 2)
        ));
        return exam;
    }
}
