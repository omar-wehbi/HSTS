package client.ui.exam;

import common.entities.ExamSession;
import common.entities.ExamSessionStatus;
import common.entities.StudentAnswer;
import common.network.ExamForm;
import common.network.ExamFormQuestion;
import common.network.Message;
import common.network.Message.Command;
import common.network.SaveAnswersRequest;
import common.network.StartExamRequest;
import common.network.SubmitAnswersRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TakeExamSessionTest {

    private TakeExamSession session;

    @BeforeEach
    void setUp() {
        session = new TakeExamSession();
    }

    @Test
    void startRequiresCodeAndId() {
        assertThat(session.requestStart("", "123")).isNull();
        assertThat(session.requestStart("0042", "")).isNull();
    }

    @Test
    void startBuildsRequestAndLoadsForm() {
        Message m = session.requestStart("0042", "987654321");
        assertThat(m.getCommand()).isEqualTo(Command.START_EXAM_SESSION);
        StartExamRequest req = (StartExamRequest) m.getPayload();
        assertThat(req.getExecutionCode()).isEqualTo("0042");

        ExamForm form = sampleForm();
        session.onServerMessage(new Message(Command.SUCCESS, form));
        assertThat(session.getExamForm()).isSameAs(form);
        assertThat(session.getQuestions()).hasSize(1);
    }

    @Test
    void saveAndSubmitBuildAnswerLists() {
        session.onServerMessage(new Message(Command.SUCCESS, sampleForm()));
        session.recordAnswer(7, 2);

        Message save = session.requestSave();
        assertThat(save.getCommand()).isEqualTo(Command.SAVE_ANSWERS);
        SaveAnswersRequest saveReq = (SaveAnswersRequest) save.getPayload();
        assertThat(saveReq.getAnswers()).extracting(StudentAnswer::getQuestionId).containsExactly(7);

        Message submit = session.requestSubmit();
        assertThat(submit.getCommand()).isEqualTo(Command.SUBMIT_ANSWERS);
        SubmitAnswersRequest submitReq = (SubmitAnswersRequest) submit.getPayload();
        assertThat(submitReq.getAnswers()).hasSize(1);
    }

    @Test
    void submitBlocksFurtherEdits() {
        session.onServerMessage(new Message(Command.SUCCESS, sampleForm()));
        ExamSession submitted = sampleForm().getSession();
        submitted.setStatus(ExamSessionStatus.SUBMITTED);
        session.onServerMessage(new Message(Command.SUCCESS, submitted));

        assertThat(session.isSubmitted()).isTrue();
        assertThat(session.requestSave()).isNull();
    }

    @Test
    void remainingSecondsUsesDeadline() {
        session.onServerMessage(new Message(Command.SUCCESS, sampleForm()));
        LocalDateTime now = LocalDateTime.of(2026, 7, 28, 10, 0);
        assertThat(session.remainingSeconds(now)).isEqualTo(1800);
    }

    @Test
    void errorMessageClearsAwaitingAndSetsLastError() {
        session.requestStart("0042", "987654321");
        assertThat(session.isAwaitingStart()).isTrue();

        session.onServerMessage(new Message(Command.ERROR, "Not enrolled in this course."));
        assertThat(session.isAwaitingStart()).isFalse();
        assertThat(session.getLastError()).isEqualTo("Not enrolled in this course.");
        assertThat(session.getStatusText()).isEqualTo("Server error.");
        assertThat(session.getExamForm()).isNull();
    }

    @Test
    void timedOutSessionMarksSubmitted() {
        session.onServerMessage(new Message(Command.SUCCESS, sampleForm()));
        ExamSession timedOut = sampleForm().getSession();
        timedOut.setStatus(ExamSessionStatus.TIMED_OUT);

        session.onServerMessage(new Message(Command.SUCCESS, timedOut));
        assertThat(session.isSubmitted()).isTrue();
        assertThat(session.getStatusText()).contains("submitted");
        assertThat(session.requestSave()).isNull();
        assertThat(session.requestSubmit()).isNull();
    }

    @Test
    void saveAndSubmitWithoutFormReturnNull() {
        assertThat(session.requestSave()).isNull();
        assertThat(session.getLastError()).contains("No active exam session");
        assertThat(session.requestSubmit()).isNull();
        assertThat(session.getLastError()).contains("No active exam session");
    }

    @Test
    void remainingSecondsEdges() {
        assertThat(session.remainingSeconds(LocalDateTime.now())).isZero();

        session.onServerMessage(new Message(Command.SUCCESS, sampleForm()));
        LocalDateTime afterDeadline = LocalDateTime.of(2026, 7, 28, 11, 0);
        assertThat(session.remainingSeconds(afterDeadline)).isZero();

        ExamSession submitted = sampleForm().getSession();
        submitted.setStatus(ExamSessionStatus.SUBMITTED);
        session.onServerMessage(new Message(Command.SUCCESS, submitted));
        assertThat(session.remainingSeconds(LocalDateTime.of(2026, 7, 28, 10, 0))).isZero();
    }

    @Test
    void recordAnswerIgnoresWhenNotEditableOrOutOfRange() {
        session.recordAnswer(7, 2);
        assertThat(session.getAnswers()).isEmpty();

        session.onServerMessage(new Message(Command.SUCCESS, sampleForm()));
        session.recordAnswer(7, 0);
        session.recordAnswer(7, 5);
        assertThat(session.getAnswers()).isEmpty();

        session.recordAnswer(7, 3);
        assertThat(session.getAnswers()).containsEntry(7, 3);

        ExamSession submitted = sampleForm().getSession();
        submitted.setStatus(ExamSessionStatus.SUBMITTED);
        session.onServerMessage(new Message(Command.SUCCESS, submitted));
        session.recordAnswer(7, 1);
        assertThat(session.getAnswers()).containsEntry(7, 3);
    }

    @Test
    void nullServerMessageIsIgnored() {
        session.onServerMessage(null);
        assertThat(session.getLastError()).isNull();
        assertThat(session.getExamForm()).isNull();
    }

    private static ExamForm sampleForm() {
        ExamSession s = new ExamSession(1, 2, 3,
                LocalDateTime.of(2026, 7, 28, 9, 30),
                LocalDateTime.of(2026, 7, 28, 10, 30));
        s.setId(99);
        ExamFormQuestion q = new ExamFormQuestion(7, 10, 1, "Q?", "A", "B", "C", "D", null);
        return new ExamForm(s, "Quiz", "Good luck", List.of(q));
    }
}
