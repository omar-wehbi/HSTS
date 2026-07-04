package client.events;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.EventBusException;

/**
 * The client's single event bus (Pub/Sub, Person 1 infrastructure).
 *
 * <p>Wraps GreenRobot {@link EventBus} behind one small class so the rest of the
 * client depends on our API, not on the library (same idea as the
 * {@code IClientConnection} adapter). Usage:
 * <ul>
 *   <li><b>Publish:</b> {@code ClientEventBus.post(new ServerMessageEvent(msg))}
 *       — done centrally by {@code HSTSClient}; screens normally never post.</li>
 *   <li><b>Subscribe:</b> declare
 *       {@code @Subscribe public void onServerMessage(ServerMessageEvent e)}
 *       in a screen — {@code ScreenManager} registers/unregisters screens
 *       automatically on navigation.</li>
 * </ul>
 */
public final class ClientEventBus {

    private static final EventBus BUS = EventBus.builder()
            .logNoSubscriberMessages(false)     // quiet when no screen listens
            .sendNoSubscriberEvent(false)
            .build();

    private ClientEventBus() { }

    /** Publishes an event to all currently registered subscribers. */
    public static void post(Object event) {
        BUS.post(event);
    }

    /**
     * Registers a subscriber. Safe to call for objects with no {@code @Subscribe}
     * methods (they are simply ignored) — this lets {@code ScreenManager} register
     * every screen without knowing which ones listen.
     */
    public static void register(Object subscriber) {
        try {
            BUS.register(subscriber);
        } catch (EventBusException ignored) {
            // Subscriber declares no @Subscribe methods — nothing to register.
        }
    }

    /** Unregisters a subscriber (harmless if it was never registered). */
    public static void unregister(Object subscriber) {
        if (BUS.isRegistered(subscriber)) {
            BUS.unregister(subscriber);
        }
    }
}
