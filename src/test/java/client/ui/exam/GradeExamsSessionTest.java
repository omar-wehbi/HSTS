package client.ui.exam;

import common.entities.ExamRelease;
import common.entities.Grade;
import common.entities.GradeStatus;
import common.network.ApproveGradeRequest;
import common.network.Message;
import common.network.Message.Command;
import common.network.OverrideGradeRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GradeExamsSessionTest {

    private GradeExamsSession session;

    @BeforeEach
    void setUp() {
        session = new GradeExamsSession();
    }

    @Test
    void gradeAutoRequiresSessionId() {
        assertThat(session.requestGradeAuto(0)).isNull();
        assertThat(session.getLastError()).contains("Select a session");
        Message m = session.requestGradeAuto(42);
        assertThat(m.getCommand()).isEqualTo(Command.GRADE_EXAM_AUTO);
        assertThat(m.getPayload()).isEqualTo(42);
    }

    @Test
    void sessionsLoadedFromServer() {
        Message req = session.requestSessions(9);
        assertThat(req.getCommand()).isEqualTo(Command.GET_RELEASE_SESSIONS);
        assertThat(req.getPayload()).isEqualTo(9);

        common.entities.ExamSession s = new common.entities.ExamSession();
        s.setId(42);
        s.setReleaseId(9);
        s.setStudentId(4);
        session.onServerMessage(new Message(Command.SUCCESS, List.of(s)));
        assertThat(session.getSessions()).hasSize(1);
        assertThat(session.getSessions().get(0).getId()).isEqualTo(42);
    }

    @Test
    void gradesLoadedFromServer() {
        Message req = session.requestGrades(9);
        assertThat(req.getCommand()).isEqualTo(Command.GET_RELEASE_GRADES);
        Grade grade = grade(1, 42, 80);
        session.onServerMessage(new Message(Command.SUCCESS, List.of(grade)));
        assertThat(session.getGrades()).hasSize(1);
        assertThat(session.getGrades().get(0).getAutoScore()).isEqualTo(80);
    }

    @Test
    void autoGradeStoredInList() {
        session.requestGradeAuto(42);
        Grade grade = grade(1, 42, 80);
        session.onServerMessage(new Message(Command.SUCCESS, grade));

        assertThat(session.getGrades()).hasSize(1);
        assertThat(session.getSelectedGrade()).isSameAs(grade);
    }

    @Test
    void approveUsesCommentWhenProvided() {
        Message withComment = session.requestApprove(5, "Well done");
        assertThat(withComment.getPayload()).isInstanceOf(ApproveGradeRequest.class);

        Message bare = session.requestApprove(5, "  ");
        assertThat(bare.getPayload()).isEqualTo(5);
    }

    @Test
    void overrideValidatesScoreAndJustification() {
        assertThat(session.requestOverride(1, 101, "reason")).isNull();
        assertThat(session.requestOverride(1, 50, "  ")).isNull();

        Message m = session.requestOverride(1, 75, "Partial credit");
        assertThat(m.getCommand()).isEqualTo(Command.OVERRIDE_GRADE);
        OverrideGradeRequest req = (OverrideGradeRequest) m.getPayload();
        assertThat(req.getNewScore()).isEqualTo(75);
    }

    @Test
    void releasesLoadedFromServer() {
        session.requestReleasedExams();
        ExamRelease r = new ExamRelease(1, 2, "0042",
                LocalDateTime.now(), LocalDateTime.now().plusHours(1));
        r.setId(9);
        session.onServerMessage(new Message(Command.SUCCESS, List.of(r)));
        assertThat(session.getReleases()).hasSize(1);
    }

    private static Grade grade(int id, int sessionId, int score) {
        Grade g = new Grade(sessionId, 1, 3, score, LocalDateTime.now());
        g.setId(id);
        g.setStatus(GradeStatus.AUTO_GRADED);
        return g;
    }
}
