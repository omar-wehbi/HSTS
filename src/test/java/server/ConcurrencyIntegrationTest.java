package server;

import common.entities.User;
import common.network.Credentials;
import common.network.Message;
import common.network.Message.Command;
import ocsf.client.AbstractClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Concurrency stress tests (Person 1, non-functional requirement:
 * "multiple users connected at once — stress-test with 3+ clients").
 *
 * <p>All clients are released by a {@link CountDownLatch} at the same instant so
 * the logins genuinely race each other on the server.
 * Integration test: requires MySQL {@code hsts_a3_db} with the seed users.
 */
class ConcurrencyIntegrationTest {

    private static final int PORT = 5601;
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

    /** Minimal OCSF client that queues server responses. */
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
    void fiveUsersCanLoginSimultaneously() throws Exception {
        String[] usernames = {"teacher", "coord", "principal", "maya", "noa"};
        List<TestClient> clients = new ArrayList<>();
        for (int i = 0; i < usernames.length; i++) {
            TestClient c = new TestClient();
            c.openConnection();
            clients.add(c);
        }

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(usernames.length);
        AtomicInteger successes = new AtomicInteger();
        List<String> problems = new ArrayList<>();

        for (int i = 0; i < usernames.length; i++) {
            final String username = usernames[i];
            final TestClient client = clients.get(i);
            new Thread(() -> {
                try {
                    start.await();                       // everyone fires together
                    Message r = client.call(new Message(Command.LOGIN,
                            new Credentials(username, "1234")));
                    if (r.getCommand() == Command.SUCCESS
                            && r.getPayload() instanceof User
                            && username.equals(((User) r.getPayload()).getUsername())) {
                        successes.incrementAndGet();     // got MY OWN identity back
                    } else {
                        synchronized (problems) {
                            problems.add(username + " -> " + r);
                        }
                    }
                } catch (Exception e) {
                    synchronized (problems) {
                        problems.add(username + " threw " + e);
                    }
                } finally {
                    done.countDown();
                }
            }, "login-" + username).start();
        }

        start.countDown();                               // GO — all at once
        assertTrue(done.await(10, TimeUnit.SECONDS), "logins timed out");
        assertEquals(usernames.length, successes.get(),
                "every user should log in with their own identity; problems: " + problems);

        for (TestClient c : clients) c.closeConnection();
    }

    @Test
    void racingLoginsOfSameUserAllowExactlyOne() throws Exception {
        final int racers = 4;
        List<TestClient> clients = new ArrayList<>();
        for (int i = 0; i < racers; i++) {
            TestClient c = new TestClient();
            c.openConnection();
            clients.add(c);
        }

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(racers);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger rejections = new AtomicInteger();

        for (TestClient client : clients) {
            new Thread(() -> {
                try {
                    start.await();
                    Message r = client.call(new Message(Command.LOGIN,
                            new Credentials("maya", "1234")));
                    if (r.getCommand() == Command.SUCCESS) successes.incrementAndGet();
                    else rejections.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            }).start();
        }

        start.countDown();                               // GO — same user, all at once
        assertTrue(done.await(10, TimeUnit.SECONDS), "logins timed out");
        assertEquals(1, successes.get(), "exactly ONE login of the same user may succeed");
        assertEquals(racers - 1, rejections.get(), "all other racers must be rejected");

        for (TestClient c : clients) c.closeConnection();
    }
}
