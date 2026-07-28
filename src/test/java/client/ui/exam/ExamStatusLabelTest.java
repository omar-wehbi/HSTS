package client.ui.exam;

import common.entities.Exam;
import common.entities.ExamStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExamStatusLabelTest {

    @Test
    void badgeMapsEveryStatus() {
        assertThat(ExamStatusLabel.badge(ExamStatus.DRAFT)).isEqualTo("Draft");
        assertThat(ExamStatusLabel.badge(ExamStatus.PENDING_APPROVAL)).isEqualTo("Pending approval");
        assertThat(ExamStatusLabel.badge(ExamStatus.APPROVED)).isEqualTo("Approved");
        assertThat(ExamStatusLabel.badge(ExamStatus.REJECTED)).isEqualTo("Rejected");
        assertThat(ExamStatusLabel.badge(null)).isEqualTo("Unknown");
    }

    @Test
    void detailIncludesRejectionReason() {
        Exam rejected = new Exam();
        rejected.setStatus(ExamStatus.REJECTED);
        rejected.setRejectionReason("Missing coverage of trees");
        assertThat(ExamStatusLabel.detail(rejected))
                .isEqualTo("Rejected: Missing coverage of trees");
    }

    @Test
    void displayIdUsesBaseIdThenId() {
        Exam exam = new Exam();
        exam.setId(7);
        exam.setBaseId(0);
        assertThat(ExamStatusLabel.displayId(exam)).isEqualTo("#7");

        exam.setBaseId(12);
        assertThat(ExamStatusLabel.displayId(exam)).isEqualTo("#12");
    }
}
