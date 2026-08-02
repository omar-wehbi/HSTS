package server;

import common.entities.Exam;
import common.entities.Question;
import common.network.AutoExamRequest;
import common.network.AutoExamRequirement;
import common.network.Credentials;
import common.network.Message;
import common.network.Message.Command;
import ocsf.client.AbstractClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import server.db.QuestionBankTestFixture;
import server.db.QuestionDAO;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static server.db.QuestionBankTestFixture.COURSE_ALGORITHMS;

/**
 * End-to-end smoke test for {@code GENERATE_EXAM_AUTO} against a running server.
 */
class AutoExamIntegrationTest {

    private static final int PORT = 5605;
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

    /** DAO suites wipe the bank; reseed what auto-generate needs. */
    @BeforeEach
    void seedAutoExamPool() {
        QuestionBankTestFixture.ensureCourses();
        QuestionBankTestFixture.wipeQuestions();
        QuestionDAO dao = new QuestionDAO();
        for (int i = 1; i <= 2; i++) {
            dao.add(tagged("complexity easy " + i, "Complexity", "EASY"));
            dao.add(tagged("sorting medium " + i, "Sorting", "MEDIUM"));
        }
    }

    private static Question tagged(String text, String topic, String difficulty) {
        Question q = QuestionBankTestFixture.sample(COURSE_ALGORITHMS, text);
        q.setTopic(topic);
        q.setDifficulty(difficulty);
        return q;
    }

    private static class TestClient extends AbstractClient {
        final BlockingQueue<Message> responses = new LinkedBlockingQueue<>();

        TestClient() {
            super("localhost", PORT);
        }

        @Override
        protected void handleMessageFromServer(Object msg) {
            if (msg instanceof Message m) {
                responses.add(m);
            }
        }

        Message call(Message request) throws Exception {
            sendToServer(request);
            Message r = responses.poll(5, TimeUnit.SECONDS);
            assertNotNull(r, "no response for " + request.getCommand());
            return r;
        }
    }

    @Test
    void generateAutoExamReturnsSavedExam() throws Exception {
        TestClient client = new TestClient();
        client.openConnection();
        try {
            Message login = client.call(new Message(Command.LOGIN, new Credentials("teacher", "1234")));
            assertEquals(Command.SUCCESS, login.getCommand());

            AutoExamRequest request = new AutoExamRequest(
                    1,
                    0,
                    "Integration Auto Exam",
                    60,
                    null,
                    null,
                    List.of(
                            new AutoExamRequirement("Complexity", "EASY", 2, 25),
                            new AutoExamRequirement("Sorting", "MEDIUM", 2, 25)
                    )
            );

            Message response = client.call(new Message(Command.GENERATE_EXAM_AUTO, request));
            assertEquals(Command.SUCCESS, response.getCommand(), String.valueOf(response.getPayload()));
            assertInstanceOf(Exam.class, response.getPayload());
        } finally {
            client.closeConnection();
        }
    }

    @Test
    void concurrentBankLoadDoesNotBreakAutoGenerate() throws Exception {
        TestClient client = new TestClient();
        client.openConnection();
        try {
            assertEquals(Command.SUCCESS,
                    client.call(new Message(Command.LOGIN, new Credentials("teacher", "1234"))).getCommand());

            client.sendToServer(new Message(Command.GET_QUESTIONS_FILTERED,
                    new common.network.QuestionFilter(1, null, null)));

            AutoExamRequest request = new AutoExamRequest(
                    1, 0, "Concurrent Auto Exam", 60, null, null,
                    List.of(
                            new AutoExamRequirement("Complexity", "EASY", 2, 25),
                            new AutoExamRequirement("Sorting", "MEDIUM", 2, 25)
                    ));
            client.sendToServer(new Message(Command.GENERATE_EXAM_AUTO, request));

            Message first = client.responses.poll(5, TimeUnit.SECONDS);
            Message second = client.responses.poll(5, TimeUnit.SECONDS);
            assertNotNull(first);
            assertNotNull(second);

            boolean sawExam = false;
            for (Message msg : List.of(first, second)) {
                if (msg.getCommand() == Command.SUCCESS && msg.getPayload() instanceof Exam) {
                    sawExam = true;
                }
            }
            assertEquals(true, sawExam, "expected a SUCCESS Exam response");
        } finally {
            client.closeConnection();
        }
    }
}
