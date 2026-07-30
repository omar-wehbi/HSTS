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
}
