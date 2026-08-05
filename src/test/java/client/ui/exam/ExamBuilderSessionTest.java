package client.ui.exam;

import common.entities.Course;
import common.entities.Exam;
import common.entities.ExamQuestion;
import common.entities.ExamStatus;
import common.entities.Question;
import common.network.AutoExamRequest;
import common.network.Message;
import common.network.Message.Command;
import common.network.QuestionFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExamBuilderSessionTest {

    private ExamBuilderSession session;

    @BeforeEach
    void setUp() {
        session = new ExamBuilderSession();
        session.setTeacherId(1);
        session.setCourseId(1);
        session.setTitle("Midterm");
        session.setDurationMinutes(90);
    }

    @Test
    void requestSaveBlockedWhenPointsNotHundred() {
        Question q = question(10);
        session.addQuestion(q, 40);
        assertThat(session.requestSave()).isNull();
        assertThat(session.getLastError()).contains("100");
    }

    @Test
    void requestSaveSendsCreateExamWhenValid() {
        session.addQuestion(question(10), 50);
        session.addQuestion(question(11), 50);

        Message m = session.requestSave();
        assertThat(m).isNotNull();
        assertThat(m.getCommand()).isEqualTo(Command.CREATE_EXAM);
        Exam payload = (Exam) m.getPayload();
        assertThat(payload.getTitle()).isEqualTo("Midterm");
        assertThat(payload.getTotalPoints()).isEqualTo(100);
    }

    @Test
    void requestSaveSendsUpdateWhenEditing() {
        Exam existing = new Exam();
        existing.setId(5);
        existing.setBaseId(5);
        existing.setCourseId(1);
        existing.setTeacherId(1);
        existing.setTitle("Old");
        existing.setDurationMinutes(60);
        existing.setStatus(ExamStatus.DRAFT);
        existing.setQuestions(List.of(
                new ExamQuestion(10, 50, 1),
                new ExamQuestion(11, 50, 2)));
        session.loadExam(existing);
        session.setTitle("New title");

        Message m = session.requestSave();
        assertThat(m.getCommand()).isEqualTo(Command.UPDATE_EXAM);
        assertThat(((Exam) m.getPayload()).getBaseId()).isEqualTo(5);
    }

    @Test
    void requestSaveClearsStaleBankWaits() {
        session.requestCourses();
        session.requestBank();
        session.addQuestion(question(10), 50);
        session.addQuestion(question(11), 50);

        Message m = session.requestSave();

        assertThat(m).isNotNull();
        assertThat(session.isAwaitingSave()).isTrue();
        assertThat(session.isAwaitingCourses()).isFalse();
        assertThat(session.isAwaitingBank()).isFalse();
    }

    @Test
    void successCreateLoadsSavedExam() {
        session.addQuestion(question(10), 50);
        session.addQuestion(question(11), 50);
        session.requestSave();

        Exam saved = new Exam();
        saved.setId(9);
        saved.setBaseId(9);
        saved.setCourseId(1);
        saved.setTeacherId(1);
        saved.setTitle("Midterm");
        saved.setDurationMinutes(90);
        saved.setStatus(ExamStatus.DRAFT);
        saved.setQuestions(List.of(
                new ExamQuestion(10, 50, 1),
                new ExamQuestion(11, 50, 2)));

        session.onServerMessage(new Message(Command.SUCCESS, saved));
        assertThat(session.getLastSaved()).isSameAs(saved);
        assertThat(session.isEditing()).isTrue();
        assertThat(session.getStatusText()).contains("saved");
    }

    @Test
    void autoGenerateErrorSurfacesMessage() {
        AutoExamRequest req = new AutoExamRequestBuilder()
                .courseId(1).teacherId(1).title("Auto").durationMinutes(60)
                .addRequirement("Sorting", "EASY", 2, 25)
                .addRequirement("Complexity", "MEDIUM", 2, 25)
                .build();
        session.requestAutoGenerate(req);
        session.onServerMessage(new Message(Command.ERROR,
                "Not enough questions matching the criteria."));
        assertThat(session.getLastError()).contains("Not enough questions");
        assertThat(session.getStatusText()).contains("Auto-generate failed");
    }

    @Test
    void staleBankErrorDoesNotCancelAutoGenerate() {
        session.requestBank();
        AutoExamRequest req = new AutoExamRequestBuilder()
                .courseId(1).teacherId(1).title("Auto").durationMinutes(60)
                .addRequirement("Sorting", "EASY", 2, 25)
                .addRequirement("Complexity", "MEDIUM", 2, 25)
                .build();
        session.requestAutoGenerate(req);
        session.onServerMessage(new Message(Command.ERROR, "bank timeout"));
        assertThat(session.isAwaitingAuto()).isFalse();
        assertThat(session.getStatusText()).contains("Auto-generate failed");
    }

    @Test
    void connectionLossClearsPendingAutoGenerate() {
        session.requestAutoGenerate(new AutoExamRequestBuilder()
                .courseId(1).teacherId(1).title("Auto").durationMinutes(60)
                .addRequirement("Sorting", "EASY", 4, 25)
                .build());
        session.onConnectionLost("Connection reset");
        assertThat(session.isAwaitingAuto()).isFalse();
        assertThat(session.getStatusText()).contains("Connection lost");
    }

    @Test
    void coursesAndBankPopulateFromSuccess() {
        session.requestCourses();
        session.onServerMessage(new Message(Command.SUCCESS,
                List.of(new Course(1, "Algorithms"), new Course(2, "DB"))));
        assertThat(session.getCourses()).hasSize(2);
        assertThat(session.getCourseId()).isEqualTo(1);

        Message bankReq = session.requestBank();
        assertThat(bankReq.getCommand()).isEqualTo(Command.GET_QUESTIONS_FILTERED);
        assertThat(((QuestionFilter) bankReq.getPayload()).getCourseId()).isEqualTo(1);

        session.onServerMessage(new Message(Command.SUCCESS, List.of(question(10))));
        assertThat(session.getBank()).hasSize(1);
    }

    @Test
    void duplicateQuestionRejected() {
        session.addQuestion(question(10), 50);
        session.addQuestion(question(10), 50);
        assertThat(session.getSelectedQuestions()).hasSize(1);
        assertThat(session.getLastError()).contains("already");
    }

    @Test
    void setPointsAtUpdatesSelectedQuestion() {
        session.addQuestion(question(10), 25);
        session.addQuestion(question(11), 25);
        session.setPointsAt(0, 40);
        assertThat(session.getSelectedQuestions().get(0).getPoints()).isEqualTo(40);
        assertThat(session.getSelectedQuestions().get(1).getPoints()).isEqualTo(25);
        assertThat(session.getPointsTotal()).isEqualTo(65);
    }

    private static Question question(int id) {
        Question q = new Question(1, "Q" + id, "a", "b", "c", "d", 1,
                null, "Sorting", "EASY");
        q.setId(id);
        q.setBaseId(id);
        return q;
    }
}
