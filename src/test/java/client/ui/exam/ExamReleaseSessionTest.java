package client.ui.exam;

import common.entities.Exam;
import common.entities.ExamStatus;
import common.network.ExamExecutionSummary;
import common.network.ExamReleaseRequest;
import common.network.ExtendExamTimeRequest;
import common.network.Message;
import common.network.Message.Command;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExamReleaseSessionTest {

    private ExamReleaseSession session;

    @BeforeEach
    void setUp() {
        session = new ExamReleaseSession();
    }

    @Test
    void requestApprovedExamsUsesGetMyExams() {
        Message m = session.requestApprovedExams();
        assertThat(m.getCommand()).isEqualTo(Command.GET_MY_EXAMS);
        assertThat(session.isAwaitingApproved()).isTrue();
    }

    @Test
    void successListKeepsOnlyApprovedExams() {
        session.requestApprovedExams();
        Exam approved = exam(1, ExamStatus.APPROVED);
        Exam draft = exam(2, ExamStatus.DRAFT);
        session.onServerMessage(new Message(Command.SUCCESS, List.of(approved, draft)));

        assertThat(session.getApprovedExams()).hasSize(1);
        assertThat(session.getApprovedExams().get(0).getStatus()).isEqualTo(ExamStatus.APPROVED);
    }

    @Test
    void releaseValidatesFourDigitCodeAndComputesCloseFromDuration() {
        LocalDateTime open = LocalDateTime.of(2026, 7, 28, 9, 0);

        assertThat(session.requestRelease(1, "42", open, 120)).isNull();
        assertThat(session.getLastError()).contains("4 letters or digits");

        assertThat(session.requestRelease(1, "0042", open, 0)).isNull();
        assertThat(session.getLastError()).contains("duration");

        assertThat(session.requestRelease(1, "0042", null, 120)).isNull();
        assertThat(session.getLastError()).contains("Open date");

        Message ok = session.requestRelease(1, "0042", open, 120);
        assertThat(ok.getCommand()).isEqualTo(Command.RELEASE_EXAM);
        ExamReleaseRequest req = (ExamReleaseRequest) ok.getPayload();
        assertThat(req.getExecutionCode()).isEqualTo("0042");
        assertThat(req.getCloseTime()).isEqualTo(open.plusMinutes(120));
    }

    @Test
    void computeCloseAddsDuration() {
        LocalDateTime open = LocalDateTime.of(2026, 7, 28, 9, 0);
        assertThat(ExamReleaseSession.computeClose(open, 45))
                .isEqualTo(LocalDateTime.of(2026, 7, 28, 9, 45));
        assertThat(ExamReleaseSession.computeClose(null, 45)).isNull();
        assertThat(ExamReleaseSession.computeClose(open, 0)).isNull();
    }

    @Test
    void extendRequiresValidMinutes() {
        assertThat(session.requestExtend(0, 30)).isNull();
        assertThat(session.requestExtend(5, 0)).isNull();
        assertThat(session.requestExtend(5, 200)).isNull();

        Message m = session.requestExtend(5, 45);
        assertThat(m.getCommand()).isEqualTo(Command.EXTEND_EXAM_TIME);
        assertThat(((ExtendExamTimeRequest) m.getPayload()).getExtraMinutes()).isEqualTo(45);
    }

    @Test
    void executionSummaryStoredOnSuccess() {
        session.requestExecutionSummary(3);
        ExamExecutionSummary summary = new ExamExecutionSummary(
                3, 10, "Midterm", LocalDateTime.now(), LocalDateTime.now().plusHours(2),
                90, 5, 4, 1);
        session.onServerMessage(new Message(Command.SUCCESS, summary));

        assertThat(session.getExecutionSummary()).isSameAs(summary);
    }

    private static Exam exam(int id, ExamStatus status) {
        Exam e = new Exam();
        e.setId(id);
        e.setTitle("Exam " + id);
        e.setStatus(status);
        return e;
    }
}
