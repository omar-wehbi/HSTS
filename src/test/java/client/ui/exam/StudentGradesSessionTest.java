package client.ui.exam;

import common.entities.GradeStatus;
import common.network.CheckedAnswer;
import common.network.CheckedExamResult;
import common.network.Message;
import common.network.Message.Command;
import common.network.StudentResultSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StudentGradesSessionTest {

    private StudentGradesSession session;

    @BeforeEach
    void setUp() {
        session = new StudentGradesSession();
    }

    @Test
    void requestResultsUsesCommand() {
        Message m = session.requestResults();
        assertThat(m.getCommand()).isEqualTo(Command.GET_STUDENT_RESULTS);
    }

    @Test
    void resultsListPopulated() {
        session.requestResults();
        StudentResultSummary s = new StudentResultSummary(
                1, 2, 3, "Quiz", 88, GradeStatus.APPROVED, LocalDateTime.now());
        session.onServerMessage(new Message(Command.SUCCESS, List.of(s)));
        assertThat(session.getResults()).hasSize(1);
    }

    @Test
    void checkedExamRequiresGradeId() {
        assertThat(session.requestCheckedExam(0)).isNull();
        Message m = session.requestCheckedExam(5);
        assertThat(m.getCommand()).isEqualTo(Command.GET_CHECKED_EXAM);
        assertThat(m.getPayload()).isEqualTo(5);
    }

    @Test
    void checkedExamStored() {
        session.requestCheckedExam(5);
        CheckedExamResult checked = new CheckedExamResult(
                5, 2, 3, "Quiz", 88, GradeStatus.APPROVED, null, "Nice work",
                List.of(new CheckedAnswer(1, "Q?", "A", "B", "C", "D", 2, 2, 10, true)));
        session.onServerMessage(new Message(Command.SUCCESS, checked));
        assertThat(session.getCheckedExam()).isSameAs(checked);
    }
}
