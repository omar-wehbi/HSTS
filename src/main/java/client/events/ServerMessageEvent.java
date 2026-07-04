package client.events;

import common.network.Message;

/**
 * Event published on the {@link ClientEventBus} for every {@link Message} that
 * arrives from the server (Presentation tier, Person 1 infrastructure).
 *
 * <p>Subscribers receive it on the JavaFX Application Thread (the network
 * adapter posts it from inside {@code Platform.runLater}), so UI code in
 * subscriber methods is thread-safe by construction.
 */
public class ServerMessageEvent {

    private final Message message;

    public ServerMessageEvent(Message message) {
        this.message = message;
    }

    public Message getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "ServerMessageEvent{" + message + '}';
    }
}
