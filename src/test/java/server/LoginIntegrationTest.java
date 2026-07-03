package server;

import common.entities.User;
import common.network.Credentials;
import common.network.Message;
import common.network.Message.Command;
import ocsf.client.AbstractClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests for the login flow and the single-session rule (Person 1).
 *
 * <p>Starts a real {@link HSTSServer} on a test port and connects real OCSF
 * clients. Integration test: requires MySQL {@code hsts_a3_db} with seed users.
 */
class LoginIntegrationTest {

    private static final int PORT = 5599;
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

    /** Minimal OCSF client that captures server responses. */
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
    void validLoginReturnsUserAndRole() throws Exception {
        TestClient c = new TestClient();
        c.openConnection();
        Message r = c.call(new Message(Command.LOGIN, new Credentials("teacher", "1234")));
        assertEquals(Command.SUCCESS, r.getCommand());
        assertInstanceOf(User.class, r.getPayload());
        assertEquals("teacher", ((User) r.getPayload()).getUsername());
        c.closeConnection();
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        TestClient c = new TestClient();
        c.openConnection();
        Message r = c.call(new Message(Command.LOGIN, new Credentials("teacher", "nope")));
        assertEquals(Command.ERROR, r.getCommand());
        c.closeConnection();
    }

    @Test
    void sameUserCannotLoginTwice() throws Exception {
        TestClient first = new TestClient();
        first.openConnection();
        Message r1 = first.call(new Message(Command.LOGIN, new Credentials("coord", "1234")));
        assertEquals(Command.SUCCESS, r1.getCommand());

        TestClient second = new TestClient();
        second.openConnection();
        Message r2 = second.call(new Message(Command.LOGIN, new Credentials("coord", "1234")));
        assertEquals(Command.ERROR, r2.getCommand(), "second login of same user must be rejected");

        first.closeConnection();
        second.closeConnection();
    }
}
