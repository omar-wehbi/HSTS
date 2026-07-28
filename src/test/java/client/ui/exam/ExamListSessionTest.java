package client.ui.exam;

import common.entities.Exam;
import common.entities.ExamStatus;
import common.network.Message;
import common.network.Message.Command;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExamListSessionTest {

    private ExamListSession session;

    @BeforeEach
    void setUp() {
        session = new ExamListSession();
    }

    @Test
    void requestMyExamsBuildsGetCommand() {
        Message m = session.requestMyExams();
        assertThat(m.getCommand()).isEqualTo(Command.GET_MY_EXAMS);
        assertThat(session.isAwaitingList()).isTrue();
    }

    @Test
    void successListPopulatesExams() {
        session.requestMyExams();
        Exam draft = exam(1, ExamStatus.DRAFT, null);
        Exam rejected = exam(2, ExamStatus.REJECTED, "Too short");
        session.onServerMessage(new Message(Command.SUCCESS, List.of(draft, rejected)));

        assertThat(session.getExams()).hasSize(2);
        assertThat(session.isAwaitingList()).isFalse();
        assertThat(session.getStatusText()).contains("2 exams");
    }

    @Test
    void rejectedExamDetailAvailableViaStatusLabel() {
        Exam rejected = exam(2, ExamStatus.REJECTED, "Missing trees");
        assertThat(ExamStatusLabel.detail(rejected)).contains("Missing trees");
    }

    @Test
    void submitRequiresSelectableDraft() {
        assertThat(session.requestSubmit()).isNull();
        assertThat(session.getLastError()).contains("Select");

        Exam pending = exam(3, ExamStatus.PENDING_APPROVAL, null);
        session.setSelected(pending);
        assertThat(session.requestSubmit()).isNull();
        assertThat(session.getLastError()).contains("draft or rejected");
    }

    @Test
    void submitSendsExamIdAndAppliesUpdatedExam() {
        Exam draft = exam(5, ExamStatus.DRAFT, null);
        session.onServerMessage(new Message(Command.SUCCESS, List.of(draft)));
        session.setSelected(draft);

        Message submit = session.requestSubmit();
        assertThat(submit.getCommand()).isEqualTo(Command.SUBMIT_EXAM_FOR_APPROVAL);
        assertThat(submit.getPayload()).isEqualTo(5);

        Exam pending = exam(5, ExamStatus.PENDING_APPROVAL, null);
        pending.setBaseId(5);
        session.onServerMessage(new Message(Command.SUCCESS, pending));
        assertThat(session.getSelected().getStatus()).isEqualTo(ExamStatus.PENDING_APPROVAL);
        assertThat(session.getStatusText()).contains("Submitted");
    }

    @Test
    void errorClearsAwaitingFlags() {
        session.requestMyExams();
        session.onServerMessage(new Message(Command.ERROR, "boom"));
        assertThat(session.isAwaitingList()).isFalse();
        assertThat(session.getLastError()).isEqualTo("boom");
    }

    private static Exam exam(int id, ExamStatus status, String reason) {
        Exam e = new Exam();
        e.setId(id);
        e.setBaseId(id);
        e.setTitle("Exam " + id);
        e.setStatus(status);
        e.setRejectionReason(reason);
        e.setCurrent(true);
        e.setDurationMinutes(60);
        e.setCourseId(1);
        e.setTeacherId(1);
        return e;
    }
}
