package server;

import common.entities.Question;
import common.network.Message;
import common.network.Message.Command;
import common.network.QuestionFilter;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * End-to-end test for {@code GET_QUESTIONS_FILTERED} (Person 2, Phase 3):
 * real {@link HSTSServer}, real OCSF client, real MySQL — the same wire path
 * Person 3's exam builder will use for its question pools.
 *
 * <p>Since Phase 5 every bank command requires a logged-in caller, so each
 * test logs in first (seed account {@code teacher}/{@code 1234}).
 */
class QuestionFilterIntegrationTest {

    private static final int PORT = 5603;
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
    void seedPool() {
        QuestionBankTestFixture.ensureCourses();
        QuestionBankTestFixture.wipeQuestions();
        QuestionDAO dao = new QuestionDAO();
        dao.add(tagged("algebra easy", "Algebra", "EASY"));
        dao.add(tagged("algebra hard", "Algebra", "HARD"));
        dao.add(tagged("geometry easy", "Geometry", "EASY"));
    }

    private static Question tagged(String text, String topic, String difficulty) {
        Question q = QuestionBankTestFixture.sample(QuestionBankTestFixture.COURSE_ALGORITHMS, text);
        q.setTopic(topic);
        q.setDifficulty(difficulty);
        return q;
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
    void filteredPoolTravelsTheFullWirePath() throws Exception {
        TestClient c = new TestClient();
        c.openConnection();
        login(c);
        Message r = c.call(new Message(Command.GET_QUESTIONS_FILTERED,
                new QuestionFilter(QuestionBankTestFixture.COURSE_ALGORITHMS, "Algebra", "EASY")));
        assertEquals(Command.SUCCESS, r.getCommand());

        @SuppressWarnings("unchecked")
        List<Question> pool = (List<Question>) r.getPayload();
        assertEquals(1, pool.size());
        assertEquals("algebra easy", pool.get(0).getQuestionText());
        c.closeConnection();
    }

    @Test
    void wrongPayloadTypeGetsCleanError() throws Exception {
        TestClient c = new TestClient();
        c.openConnection();
        login(c);
        Message r = c.call(new Message(Command.GET_QUESTIONS_FILTERED, "not-a-filter"));
        assertEquals(Command.ERROR, r.getCommand());
        c.closeConnection();
    }

    @Test
    void mutationRepliesAreSurgicalNotFullBank() throws Exception {
        // Phase 8 (NFR 18): ADD answers with the saved Question, DELETE with the
        // removed baseId — never the whole bank.
        TestClient c = new TestClient();
        c.openConnection();
        login(c);

        Message added = c.call(new Message(Command.ADD_QUESTION,
                QuestionBankTestFixture.sample(QuestionBankTestFixture.COURSE_ALGORITHMS, "surgical")));
        assertEquals(Command.SUCCESS, added.getCommand());
        Question saved = (Question) added.getPayload();
        assertEquals("surgical", saved.getQuestionText());

        Message deleted = c.call(new Message(Command.DELETE_QUESTION, saved.getBaseId()));
        assertEquals(Command.SUCCESS, deleted.getCommand());
        assertEquals(saved.getBaseId(), deleted.getPayload());
        c.closeConnection();
    }

    @Test
    void withoutLoginEveryBankCommandIsRejected() throws Exception {
        // Phase 5 (R-063): an anonymous socket can neither read nor mutate.
        TestClient c = new TestClient();
        c.openConnection();
        assertEquals(Command.ERROR, c.call(new Message(Command.GET_QUESTIONS)).getCommand());
        assertEquals(Command.ERROR, c.call(new Message(Command.GET_QUESTIONS_FILTERED,
                new QuestionFilter(1, null, null))).getCommand());
        assertEquals(Command.ERROR, c.call(new Message(Command.DELETE_QUESTION, 1)).getCommand());
        c.closeConnection();
    }
}
