package server;

import common.entities.Exam;
import common.entities.ExamRelease;
import common.network.Credentials;
import common.network.Message;
import common.network.Message.Command;
import common.network.StartExamRequest;
import common.network.StudyBotQuestionRequest;
import ocsf.client.AbstractClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import server.db.ExecutionTestFixture;
import server.db.StudyBotDAO;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Study bot must reject asks while the student has an in-progress exam in that course. */
class StudyBotLockoutIntegrationTest {

    private static final int PORT = 5608;
    private static HSTSServer server;

    @BeforeAll
    static void startServer() throws Exception {
        server = new HSTSServer(PORT);
        server.listen();
    }

    @AfterAll
    static void stopServer() throws Exception {
        server.close();
    }

    @BeforeEach
    void seed() {
        ExecutionTestFixture.wipeExecutionData();
    }

    private static class TestClient extends AbstractClient {
        final BlockingQueue<Message> responses = new LinkedBlockingQueue<>();

        TestClient() { super("localhost", PORT); }

        @Override
        protected void handleMessageFromServer(Object msg) {
            if (msg instanceof Message m) responses.add(m);
        }

        Message call(Message request) throws Exception {
            sendToServer(request);
            Message r = responses.poll(8, TimeUnit.SECONDS);
            assertThat(r).as("no response for " + request.getCommand()).isNotNull();
            return r;
        }
    }

    @Test
    void askBlockedDuringActiveExamInSameCourse() throws Exception {
        int teacherId = ExecutionTestFixture.userId("teacher");
        int course = ExecutionTestFixture.COURSE_ALGORITHMS;
        ExecutionTestFixture.ensureCourseTeacher(course, teacherId);
        StudyBotDAO bots = new StudyBotDAO();
        bots.createBot(course, "Lockout Bot", false, teacherId);
        bots.setAvailability(course, true);
        bots.addSource(course, "Notes", "Useful notes", teacherId);

        Exam exam = ExecutionTestFixture.ensureApprovedExam();
        ExamRelease release = ExecutionTestFixture.createOpenRelease(exam, "8801");
        assertThat(release.getId()).isPositive();

        TestClient student = new TestClient();
        student.openConnection();
        try {
            assertThat(student.call(new Message(Command.LOGIN, new Credentials("maya", "1234")))
                    .getCommand()).isEqualTo(Command.SUCCESS);

            Message started = student.call(new Message(Command.START_EXAM_SESSION,
                    new StartExamRequest("8801", "207570227")));
            assertThat(started.getCommand()).isEqualTo(Command.SUCCESS);

            Message ask = student.call(new Message(Command.ASK_STUDY_BOT,
                    new StudyBotQuestionRequest(course, "What is Big-O?")));
            assertThat(ask.getCommand()).isEqualTo(Command.ERROR);
            assertThat(String.valueOf(ask.getPayload()))
                    .contains("unavailable while you are taking an exam");
        } finally {
            student.closeConnection();
        }
    }
}
