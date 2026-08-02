package client.ui.exam;

import common.entities.ExamRelease;
import common.entities.Grade;
import common.entities.GradeStatus;
import common.network.ApproveGradeRequest;
import common.network.ExamExecutionSummary;
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

    @Test
    void errorMessageSetsLastErrorAndClearsAwaiting() {
        session.requestSessions(9);
        assertThat(session.isAwaitingSessions()).isTrue();
        session.onServerMessage(new Message(Command.ERROR, "Not the exam author."));
        assertThat(session.isAwaitingSessions()).isFalse();
        assertThat(session.getLastError()).isEqualTo("Not the exam author.");
        assertThat(session.getStatusText()).isEqualTo("Server error.");
    }

    @Test
    void idGuardsRejectNonPositiveIds() {
        assertThat(session.requestSessions(0)).isNull();
        assertThat(session.getLastError()).contains("Select a release");

        assertThat(session.requestGrades(-1)).isNull();
        assertThat(session.getLastError()).contains("Select a release");

        assertThat(session.requestExecutionSummary(0)).isNull();
        assertThat(session.getLastError()).contains("Select a release");

        assertThat(session.requestApprove(0, "ok")).isNull();
        assertThat(session.getLastError()).contains("Select or grade");

        assertThat(session.requestOverride(0, 50, "reason")).isNull();
        assertThat(session.getLastError()).contains("Select or grade");
    }

    @Test
    void emptyListsClearPriorData() {
        session.requestSessions(9);
        common.entities.ExamSession s = new common.entities.ExamSession();
        s.setId(42);
        session.onServerMessage(new Message(Command.SUCCESS, List.of(s)));
        assertThat(session.getSessions()).hasSize(1);

        session.requestSessions(9);
        session.onServerMessage(new Message(Command.SUCCESS, List.of()));
        assertThat(session.getSessions()).isEmpty();
        assertThat(session.getStatusText()).contains("0 session");

        session.requestGrades(9);
        session.onServerMessage(new Message(Command.SUCCESS, List.of()));
        assertThat(session.getGrades()).isEmpty();

        session.requestReleasedExams();
        session.onServerMessage(new Message(Command.SUCCESS, List.of()));
        assertThat(session.getReleases()).isEmpty();
    }

    @Test
    void executionSummaryStoredOnSuccess() {
        Message req = session.requestExecutionSummary(9);
        assertThat(req.getCommand()).isEqualTo(Command.GET_EXECUTION_SUMMARY);

        ExamExecutionSummary summary = new ExamExecutionSummary(
                9, 2, "Quiz", LocalDateTime.now(), LocalDateTime.now().plusHours(1),
                60, 3, 2, 1);
        session.onServerMessage(new Message(Command.SUCCESS, summary));
        assertThat(session.getExecutionSummary()).isSameAs(summary);
        assertThat(session.getStatusText()).contains("Execution summary");
    }

    @Test
    void approveAndOverrideReplaceExistingGrade() {
        session.requestGrades(9);
        Grade first = grade(11, 42, 70);
        session.onServerMessage(new Message(Command.SUCCESS, List.of(first)));
        assertThat(session.getGrades()).hasSize(1);

        Grade approved = grade(11, 42, 70);
        approved.setStatus(GradeStatus.APPROVED);
        session.requestApprove(11, "ok");
        session.onServerMessage(new Message(Command.SUCCESS, approved));
        assertThat(session.getGrades()).hasSize(1);
        assertThat(session.getGrades().get(0).getStatus()).isEqualTo(GradeStatus.APPROVED);

        Grade overridden = grade(11, 42, 70);
        overridden.setStatus(GradeStatus.OVERRIDDEN);
        overridden.setFinalScore(90);
        session.requestOverride(11, 90, "curve");
        session.onServerMessage(new Message(Command.SUCCESS, overridden));
        assertThat(session.getGrades()).hasSize(1);
        assertThat(session.getGrades().get(0).getEffectiveScore()).isEqualTo(90);
    }

    private static Grade grade(int id, int sessionId, int score) {
        Grade g = new Grade(sessionId, 1, 3, score, LocalDateTime.now());
        g.setId(id);
        g.setStatus(GradeStatus.AUTO_GRADED);
        return g;
    }
}
