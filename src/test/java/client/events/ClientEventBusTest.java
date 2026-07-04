package client.events;

import common.network.Message;
import common.network.Message.Command;
import org.greenrobot.eventbus.Subscribe;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the client Pub/Sub infrastructure (Person 1).
 * Pure logic — no JavaFX, no network, no database.
 */
class ClientEventBusTest {

    /** A subscriber that records every event it receives (public: EventBus uses reflection). */
    public static class Probe {
        final List<ServerMessageEvent> received = new ArrayList<>();
        @Subscribe
        public void onServerMessage(ServerMessageEvent e) {
            received.add(e);
        }
    }

    /** An object with no @Subscribe methods at all. */
    public static class NotASubscriber { }

    @Test
    void subscriberReceivesPostedEvent() {
        Probe probe = new Probe();
        ClientEventBus.register(probe);
        try {
            Message msg = new Message(Command.SUCCESS, "hello");
            ClientEventBus.post(new ServerMessageEvent(msg));

            assertEquals(1, probe.received.size());
            assertSame(msg, probe.received.get(0).getMessage());
        } finally {
            ClientEventBus.unregister(probe);
        }
    }

    @Test
    void unregisteredSubscriberStopsReceiving() {
        Probe probe = new Probe();
        ClientEventBus.register(probe);
        ClientEventBus.unregister(probe);

        ClientEventBus.post(new ServerMessageEvent(new Message(Command.SUCCESS)));
        assertTrue(probe.received.isEmpty(), "no events after unregister");
    }

    @Test
    void multipleSubscribersAllReceiveTheEvent() {
        Probe a = new Probe();
        Probe b = new Probe();
        ClientEventBus.register(a);
        ClientEventBus.register(b);
        try {
            ClientEventBus.post(new ServerMessageEvent(new Message(Command.ERROR, "x")));
            assertEquals(1, a.received.size());
            assertEquals(1, b.received.size());
        } finally {
            ClientEventBus.unregister(a);
            ClientEventBus.unregister(b);
        }
    }

    @Test
    void registeringAnObjectWithoutSubscribeMethodsIsHarmless() {
        // ScreenManager registers EVERY screen; ones without @Subscribe methods
        // must be silently ignored, not crash navigation.
        NotASubscriber none = new NotASubscriber();
        assertDoesNotThrow(() -> ClientEventBus.register(none));
        assertDoesNotThrow(() -> ClientEventBus.unregister(none));
    }

    @Test
    void doubleUnregisterIsHarmless() {
        Probe probe = new Probe();
        ClientEventBus.register(probe);
        ClientEventBus.unregister(probe);
        assertDoesNotThrow(() -> ClientEventBus.unregister(probe));
    }
}
