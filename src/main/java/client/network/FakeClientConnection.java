package client.network;

import client.events.ClientEventBus;
import client.events.ServerMessageEvent;
import common.network.Message;
import common.network.Message.Command;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.function.Consumer;

/**
 * Test / demo double for {@link IClientConnection} (Person 5).
 *
 * <p>Records every outbound {@link Message} and delivers scripted replies.
 * Replies are published on {@link ClientEventBus} as {@link ServerMessageEvent}
 * (same path as {@link HSTSClient}), so screens under test keep using
 * {@code @Subscribe} without a live server.
 *
 * <p>Reply matching: if a queue is registered for the sent {@link Command},
 * that queue's next message is delivered; otherwise the FIFO default queue
 * is used. If neither has a reply, nothing is posted (caller can assert on
 * the recorded send alone).
 */
public class FakeClientConnection implements IClientConnection {

    private final String host;
    private final int port;
    private boolean open;

    private final List<Message> sent = new ArrayList<>();
    private final Queue<Message> defaultReplies = new LinkedList<>();
    private final Map<Command, Queue<Message>> repliesByCommand = new EnumMap<>(Command.class);

    /** Legacy direct handler (kept for parity with {@link IClientConnection}). */
    private Consumer<Message> serverMessageHandler;

    /** When true (default), {@link #send} posts replies immediately. */
    private boolean autoReply = true;

    public FakeClientConnection() {
        this("fake-host", 5555);
    }

    public FakeClientConnection(String host, int port) {
        this.host = host;
        this.port = port;
    }

    // ===== scripting ======================================================

    /** Enqueues a reply used when no command-specific queue matches. */
    public FakeClientConnection enqueueReply(Message reply) {
        defaultReplies.add(reply);
        return this;
    }

    /** Enqueues a reply for a specific outbound command. */
    public FakeClientConnection enqueueReply(Command forCommand, Message reply) {
        repliesByCommand
                .computeIfAbsent(forCommand, c -> new LinkedList<>())
                .add(reply);
        return this;
    }

    public FakeClientConnection clearReplies() {
        defaultReplies.clear();
        repliesByCommand.clear();
        return this;
    }

    /** When false, {@link #send} only records; call {@link #deliverNext(Message)} yourself. */
    public void setAutoReply(boolean autoReply) {
        this.autoReply = autoReply;
    }

    /** Manually deliver a reply (posts EventBus + legacy handler). */
    public void deliver(Message reply) {
        if (reply == null) return;
        ClientEventBus.post(new ServerMessageEvent(reply));
        if (serverMessageHandler != null) {
            serverMessageHandler.accept(reply);
        }
    }

    /** @return unmodifiable view of messages passed to {@link #send}. */
    public List<Message> getSentMessages() {
        return List.copyOf(sent);
    }

    public void clearSent() {
        sent.clear();
    }

    /** @return the last sent message, or null if none. */
    public Message lastSent() {
        return sent.isEmpty() ? null : sent.get(sent.size() - 1);
    }

    // ===== IClientConnection ==============================================

    @Override
    public void connect() {
        open = true;
    }

    @Override
    public void send(Message msg) throws IOException {
        if (!open) {
            throw new IOException("FakeClientConnection is not connected.");
        }
        if (msg == null) {
            throw new IOException("Cannot send a null message.");
        }
        sent.add(msg);
        if (!autoReply) return;

        Message reply = dequeueReply(msg.getCommand());
        if (reply != null) {
            deliver(reply);
        }
    }

    private Message dequeueReply(Command command) {
        Queue<Message> specific = repliesByCommand.get(command);
        if (specific != null && !specific.isEmpty()) {
            return specific.poll();
        }
        return defaultReplies.poll();
    }

    @Override
    public void disconnect() {
        open = false;
    }

    @Override
    public boolean isConnectionOpen() {
        return open;
    }

    @Override
    public String getHost() {
        return host;
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public void setServerMessageHandler(Consumer<Message> handler) {
        this.serverMessageHandler = handler;
    }
}
