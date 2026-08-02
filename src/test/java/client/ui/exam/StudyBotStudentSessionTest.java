package client.ui.exam;

import common.entities.Course;
import common.network.Message;
import common.network.Message.Command;
import common.network.StudyBotAnswer;
import common.network.StudyBotQuestionRequest;
import common.network.StudyBotView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StudyBotStudentSessionTest {

    private StudyBotStudentSession session;

    @BeforeEach
    void setUp() {
        session = new StudyBotStudentSession();
    }

    @Test
    void askRequiresQuestion() {
        assertThat(session.requestAsk(1, "  ")).isNull();
        Message m = session.requestAsk(1, "What is a tree?");
        assertThat(m.getCommand()).isEqualTo(Command.ASK_STUDY_BOT);
        StudyBotQuestionRequest req = (StudyBotQuestionRequest) m.getPayload();
        assertThat(req.getQuestion()).isEqualTo("What is a tree?");
    }

    @Test
    void coursesAndBotLoaded() {
        session.requestCourses();
        session.onServerMessage(new Message(Command.SUCCESS, List.of(new Course(1, "Algo"))));
        assertThat(session.getCourses()).hasSize(1);

        session.requestStudyBot(1);
        StudyBotView view = new StudyBotView(1, "Helper", true, true, 2,
                LocalDateTime.now(), 3);
        session.onServerMessage(new Message(Command.SUCCESS, view));
        assertThat(session.getBotView().getName()).isEqualTo("Helper");
    }

    @Test
    void historyLoaded() {
        session.requestHistory();
        StudyBotAnswer a = new StudyBotAnswer(1, 1, "Q?", "A", LocalDateTime.now());
        session.onServerMessage(new Message(Command.SUCCESS, List.of(a)));
        assertThat(session.getHistory()).hasSize(1);
    }

    @Test
    void errorLockoutSetsLastError() {
        session.requestAsk(1, "What is a tree?");
        session.onServerMessage(new Message(Command.ERROR,
                "Study bot is locked while an exam is in progress."));
        assertThat(session.getLastError()).contains("locked");
        assertThat(session.getStatusText()).isEqualTo("Server error.");
        assertThat(session.getLastAnswer()).isNull();
    }

    @Test
    void errorUnavailableSetsLastError() {
        session.requestStudyBot(1);
        session.onServerMessage(new Message(Command.ERROR, "Study bot is not available."));
        assertThat(session.getLastError()).contains("not available");
        assertThat(session.getBotView()).isNull();
    }

    @Test
    void askSuccessStoresAnswer() {
        session.requestAsk(1, "What is DFS?");
        StudyBotAnswer answer = new StudyBotAnswer(2, 1, "What is DFS?", "Depth-first search",
                LocalDateTime.now());
        session.onServerMessage(new Message(Command.SUCCESS, answer));
        assertThat(session.getLastAnswer()).isSameAs(answer);
        assertThat(session.getStatusText()).contains("Answer received");
    }

    @Test
    void emptyListsClearCoursesAndHistory() {
        session.requestCourses();
        session.onServerMessage(new Message(Command.SUCCESS, List.of()));
        assertThat(session.getCourses()).isEmpty();
        assertThat(session.getStatusText()).contains("0 course");

        session.requestHistory();
        session.onServerMessage(new Message(Command.SUCCESS, List.of()));
        assertThat(session.getHistory()).isEmpty();
        assertThat(session.getStatusText()).contains("0 history");
    }

    @Test
    void askAndStudyBotRejectNonPositiveCourseId() {
        assertThat(session.requestAsk(0, "Q?")).isNull();
        assertThat(session.getLastError()).contains("course ID");
        assertThat(session.requestStudyBot(-1)).isNull();
        assertThat(session.getLastError()).contains("course ID");
    }
}
