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
 * Session lifecycle tests (Person 1): LOGOUT, GET_CURRENT_USER, and the
 * guarantees that matter in demos — after a logout (or a disconnect) the same
 * user can log in again.
 * Integration test: requires MySQL {@code hsts_a3_db} with the seed users.
 */
class SessionLifecycleTest {

    private static final int PORT = 5602;
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

    private static class TestClient extends AbstractClient {
        final BlockingQueue<Message> responses = new LinkedBlockingQueue<>();
        TestClient() { super("localhost", PORT); }
        @Override protected void handleMessageFromServer(Object msg) {
            if (msg instanceof Message) responses.add((Message) msg);
        }
        Message call(Message request) throws Exception {
            sendToServer(request);
            Message r = responses.poll(5, TimeUnit.SECONDS);
            assertNotNull(r, "no response for " + request.getCommand());
            return r;
        }
    }

    @Test
    void currentUserReflectsLoginAndLogout() throws Exception {
        TestClient c = new TestClient();
        c.openConnection();

        // Before login: no current user.
        Message before = c.call(new Message(Command.GET_CURRENT_USER));
        assertEquals(Command.SUCCESS, before.getCommand());
        assertNull(before.getPayload(), "no user should be attached before login");

        // After login: current user is me, with my role.
        c.call(new Message(Command.LOGIN, new Credentials("principal", "1234")));
        Message during = c.call(new Message(Command.GET_CURRENT_USER));
        assertInstanceOf(User.class, during.getPayload());
        assertEquals("principal", ((User) during.getPayload()).getUsername());

        // After logout: no current user again.
        Message logout = c.call(new Message(Command.LOGOUT));
        assertEquals(Command.SUCCESS, logout.getCommand());
        Message after = c.call(new Message(Command.GET_CURRENT_USER));
        assertNull(after.getPayload(), "logout must clear the session");

        c.closeConnection();
    }

    @Test
    void userCanLoginAgainAfterLogout() throws Exception {
        TestClient first = new TestClient();
        first.openConnection();
        assertEquals(Command.SUCCESS,
                first.call(new Message(Command.LOGIN, new Credentials("noa", "1234"))).getCommand());
        assertEquals(Command.SUCCESS,
                first.call(new Message(Command.LOGOUT)).getCommand());

        // Same user, new connection — must be allowed now.
        TestClient second = new TestClient();
        second.openConnection();
        assertEquals(Command.SUCCESS,
                second.call(new Message(Command.LOGIN, new Credentials("noa", "1234"))).getCommand(),
                "after logout the username must be free again");

        first.closeConnection();
        second.closeConnection();
    }

    @Test
    void userCanLoginAgainAfterDisconnect() throws Exception {
        TestClient first = new TestClient();
        first.openConnection();
        assertEquals(Command.SUCCESS,
                first.call(new Message(Command.LOGIN, new Credentials("maya", "1234"))).getCommand());

        // Simulate the window being closed: disconnect WITHOUT logging out.
        first.closeConnection();
        Thread.sleep(300);   // let the server's clientDisconnected hook run

        TestClient second = new TestClient();
        second.openConnection();
        assertEquals(Command.SUCCESS,
                second.call(new Message(Command.LOGIN, new Credentials("maya", "1234"))).getCommand(),
                "a disconnect must free the username (clientDisconnected -> logout)");

        second.closeConnection();
    }

    @Test
    void logoutWithoutLoginIsHarmless() throws Exception {
        TestClient c = new TestClient();
        c.openConnection();
        assertEquals(Command.SUCCESS, c.call(new Message(Command.LOGOUT)).getCommand(),
                "logging out when not logged in should not error");
        c.closeConnection();
    }
}
