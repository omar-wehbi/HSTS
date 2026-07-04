package server;

import common.entities.Question;
import common.network.Message;
import common.network.Message.Command;
import ocsf.client.AbstractClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import server.db.QuestionBankTestFixture;
import server.db.QuestionDAO;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * End-to-end test for {@code GET_QUESTION_IMAGE} (Person 2, Phase 4):
 * illustration bytes survive the OCSF round-trip, and questions without an
 * image answer with a null payload rather than an error.
 * Since Phase 5 every bank command requires a logged-in caller, so each test
 * logs in first (seed account {@code teacher}/{@code 1234}).
 */
class QuestionImageIntegrationTest {

    private static final int PORT = 5604;
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 42, 43, 44};
    private static HSTSServer server;
    private static int illustratedId;
    private static int plainId;

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
        QuestionBankTestFixture.ensureCourses();
        QuestionBankTestFixture.wipeQuestions();
        QuestionDAO dao = new QuestionDAO();

        Question q = QuestionBankTestFixture.sample(QuestionBankTestFixture.COURSE_ALGORITHMS, "with image");
        q.setImagePath("diagram.png");
        q.setImageData(PNG);
        illustratedId = dao.add(q).getId();

        plainId = dao.add(QuestionBankTestFixture.sample(
                QuestionBankTestFixture.COURSE_ALGORITHMS, "no image")).getId();
    }

    /** Minimal OCSF client that captures server responses (Person 1's pattern). */
    private static class TestClient extends AbstractClient {
        final BlockingQueue<Message> responses = new LinkedBlockingQueue<>();
        TestClient() { super("localhost", PORT); }
        @Override protected void handleMessageFromServer(Object msg) {
            if (msg instanceof Message) responses.add((Message) msg);
        }
        Message call(Message request) throws Exception {
            sendToServer(request);
            Message r = responses.poll(3, TimeUnit.SECONDS);
            assertNotNull(r, "no response for " + request.getCommand());
            return r;
        }
    }

    /** Phase 5 security: bank commands need a session — log the teacher in. */
    private static void login(TestClient c) throws Exception {
        Message r = c.call(new Message(Command.LOGIN,
                new common.network.Credentials("teacher", "1234")));
        assertEquals(Command.SUCCESS, r.getCommand(), "seed teacher must be able to log in");
    }

    @Test
    void imageBytesSurviveTheWire() throws Exception {
        TestClient c = new TestClient();
        c.openConnection();
        login(c);
        Message r = c.call(new Message(Command.GET_QUESTION_IMAGE, illustratedId));
        assertEquals(Command.SUCCESS, r.getCommand());
        assertArrayEquals(PNG, (byte[]) r.getPayload());
        c.closeConnection();
    }

    @Test
    void questionWithoutImageAnswersNullNotError() throws Exception {
        TestClient c = new TestClient();
        c.openConnection();
        login(c);
        Message r = c.call(new Message(Command.GET_QUESTION_IMAGE, plainId));
        assertEquals(Command.SUCCESS, r.getCommand());
        assertNull(r.getPayload());
        c.closeConnection();
    }

    @Test
    void wrongPayloadTypeGetsCleanError() throws Exception {
        TestClient c = new TestClient();
        c.openConnection();
        login(c);
        Message r = c.call(new Message(Command.GET_QUESTION_IMAGE, "not-an-id"));
        assertEquals(Command.ERROR, r.getCommand());
        c.closeConnection();
    }
}
