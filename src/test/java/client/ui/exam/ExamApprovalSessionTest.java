package client.ui.exam;

import common.entities.Exam;
import common.entities.ExamStatus;
import common.network.ExamRejectionRequest;
import common.network.Message;
import common.network.Message.Command;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExamApprovalSessionTest {

    private ExamApprovalSession session;

    @BeforeEach
    void setUp() {
        session = new ExamApprovalSession();
    }

    @Test
    void loadsPendingList() {
        session.requestPending();
        Exam pending = exam(1, ExamStatus.PENDING_APPROVAL);
        session.onServerMessage(new Message(Command.SUCCESS, List.of(pending)));
        assertThat(session.getPending()).hasSize(1);
    }

    @Test
    void requestPendingSendsFilterPayload() {
        session.setFilterSubjectId(3);
        session.setFilterCourseId(7);
        Message m = session.requestPending();
        assertThat(m.getCommand()).isEqualTo(Command.GET_PENDING_EXAMS);
        common.network.PendingExamFilter f =
                (common.network.PendingExamFilter) m.getPayload();
        assertThat(f.getSubjectId()).isEqualTo(3);
        assertThat(f.getCourseId()).isEqualTo(7);
    }

    @Test
    void changingSubjectClearsCourseFilter() {
        session.setFilterSubjectId(1);
        session.setFilterCourseId(2);
        session.setFilterSubjectId(5);
        assertThat(session.getFilterSubjectId()).isEqualTo(5);
        assertThat(session.getFilterCourseId()).isNull();
    }

    @Test
    void approveSendsExamId() {
        Exam pending = exam(8, ExamStatus.PENDING_APPROVAL);
        session.onServerMessage(new Message(Command.SUCCESS, List.of(pending)));
        session.setSelected(pending);

        Message m = session.requestApprove();
        assertThat(m.getCommand()).isEqualTo(Command.APPROVE_EXAM);
        assertThat(m.getPayload()).isEqualTo(8);

        Exam approved = exam(8, ExamStatus.APPROVED);
        session.onServerMessage(new Message(Command.SUCCESS, approved));
        assertThat(session.getPending()).isEmpty();
        assertThat(session.getStatusText()).contains("approved");
    }

    @Test
    void rejectRequiresReason() {
        Exam pending = exam(8, ExamStatus.PENDING_APPROVAL);
        session.setSelected(pending);
        assertThat(session.requestReject("  ")).isNull();
        assertThat(session.getLastError()).contains("reason");

        Message m = session.requestReject("Needs more topics");
        assertThat(m.getCommand()).isEqualTo(Command.REJECT_EXAM);
        ExamRejectionRequest req = (ExamRejectionRequest) m.getPayload();
        assertThat(req.getExamId()).isEqualTo(8);
        assertThat(req.getReason()).isEqualTo("Needs more topics");
    }

    private static Exam exam(int id, ExamStatus status) {
        Exam e = new Exam();
        e.setId(id);
        e.setBaseId(id);
        e.setTitle("Exam " + id);
        e.setStatus(status);
        e.setCurrent(true);
        e.setDurationMinutes(60);
        e.setCourseId(1);
        e.setTeacherId(1);
        return e;
    }
}
