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
 * <p>Note: question commands carry no authorization guard yet; when Phase 5
 * adds role guards this test gains a LOGIN step.
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

    @Test
    void filteredPoolTravelsTheFullWirePath() throws Exception {
        TestClient c = new TestClient();
        c.openConnection();
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
        Message r = c.call(new Message(Command.GET_QUESTIONS_FILTERED, "not-a-filter"));
        assertEquals(Command.ERROR, r.getCommand());
        c.closeConnection();
    }
}
