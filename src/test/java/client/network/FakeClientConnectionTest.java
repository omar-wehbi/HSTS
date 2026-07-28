package client.network;

import client.events.ClientEventBus;
import client.events.ServerMessageEvent;
import common.network.Message;
import common.network.Message.Command;
import org.greenrobot.eventbus.Subscribe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link FakeClientConnection} (Person 5, TDD).
 */
class FakeClientConnectionTest {

    /** Public: EventBus invokes @Subscribe via reflection. */
    public static class Probe {
        final List<ServerMessageEvent> received = new ArrayList<>();

        @Subscribe
        public void onServerMessage(ServerMessageEvent event) {
            received.add(event);
        }
    }

    private FakeClientConnection fake;
    private Probe probe;

    @BeforeEach
    void setUp() {
        fake = new FakeClientConnection();
        probe = new Probe();
        ClientEventBus.register(probe);
    }

    @AfterEach
    void tearDown() {
        ClientEventBus.unregister(probe);
    }

    @Test
    void connectOpensAndDisconnectCloses() {
        assertThat(fake.isConnectionOpen()).isFalse();
        fake.connect();
        assertThat(fake.isConnectionOpen()).isTrue();
        fake.disconnect();
        assertThat(fake.isConnectionOpen()).isFalse();
    }

    @Test
    void sendWhileClosedThrows() {
        assertThatThrownBy(() -> fake.send(new Message(Command.GET_COURSES)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("not connected");
    }

    @Test
    void sendRecordsMessageAndDeliversQueuedReplyViaEventBus() throws Exception {
        fake.connect();
        fake.enqueueReply(new Message(Command.SUCCESS, "ok"));

        fake.send(new Message(Command.GET_MY_EXAMS));

        assertThat(fake.getSentMessages()).hasSize(1);
        assertThat(fake.lastSent().getCommand()).isEqualTo(Command.GET_MY_EXAMS);
        assertThat(probe.received).hasSize(1);
        assertThat(probe.received.get(0).getMessage().getCommand()).isEqualTo(Command.SUCCESS);
        assertThat(probe.received.get(0).getMessage().getPayload()).isEqualTo("ok");
    }

    @Test
    void commandSpecificReplyPreferredOverDefaultQueue() throws Exception {
        fake.connect();
        fake.enqueueReply(new Message(Command.SUCCESS, "default"));
        fake.enqueueReply(Command.CREATE_EXAM, new Message(Command.SUCCESS, "created"));

        fake.send(new Message(Command.CREATE_EXAM));

        assertThat(probe.received).hasSize(1);
        assertThat(probe.received.get(0).getMessage().getPayload()).isEqualTo("created");
    }

    @Test
    void autoReplyOffOnlyRecordsUntilDeliver() throws Exception {
        fake.connect();
        fake.setAutoReply(false);
        fake.enqueueReply(new Message(Command.ERROR, "ignored until deliver"));

        fake.send(new Message(Command.LOGIN));
        assertThat(probe.received).isEmpty();

        fake.deliver(new Message(Command.ERROR, "now"));
        assertThat(probe.received).hasSize(1);
        assertThat(probe.received.get(0).getMessage().getPayload()).isEqualTo("now");
    }

    @Test
    void legacyHandlerAlsoReceivesDeliveredReply() throws Exception {
        fake.connect();
        AtomicReference<Message> viaHandler = new AtomicReference<>();
        fake.setServerMessageHandler(viaHandler::set);
        fake.enqueueReply(new Message(Command.SUCCESS, 42));

        fake.send(new Message(Command.GET_CURRENT_USER));

        assertThat(viaHandler.get()).isNotNull();
        assertThat(viaHandler.get().getPayload()).isEqualTo(42);
    }

    @Test
    void hostAndPortExposed() {
        FakeClientConnection custom = new FakeClientConnection("demo", 9999);
        assertThat(custom.getHost()).isEqualTo("demo");
        assertThat(custom.getPort()).isEqualTo(9999);
    }
}
