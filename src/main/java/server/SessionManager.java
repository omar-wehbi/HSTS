package server;

import common.entities.User;
import ocsf.server.ConnectionToClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks logged-in users on the server (Logic tier).
 *
 * <p>Two responsibilities:
 * <ul>
 *   <li>Remember which {@link User} is logged in on each client connection, so
 *       handlers can check the caller's role.</li>
 *   <li>Enforce the requirement that the <b>same user cannot be logged in twice</b>
 *       at the same time.</li>
 * </ul>
 * Thread-safe: OCSF calls in on per-client threads.
 */
public class SessionManager {

    /** The user logged in on each connection. */
    private final Map<ConnectionToClient, User> byConnection = new ConcurrentHashMap<>();
    /** Which connection currently holds each username (for the single-session rule). */
    private final Map<String, ConnectionToClient> byUsername = new ConcurrentHashMap<>();

    /**
     * Registers a successful login.
     *
     * @return {@code true} if login is allowed; {@code false} if that username is
     *         already logged in on another connection (reject the second login).
     */
    public synchronized boolean login(ConnectionToClient conn, User user) {
        if (byUsername.containsKey(user.getUsername())) {
            return false;   // already logged in elsewhere
        }
        byConnection.put(conn, user);
        byUsername.put(user.getUsername(), conn);
        return true;
    }

    /** Logs out whoever is on this connection (also called on disconnect). */
    public synchronized void logout(ConnectionToClient conn) {
        User u = byConnection.remove(conn);
        if (u != null) {
            byUsername.remove(u.getUsername());
        }
    }

    /** @return the user logged in on this connection, or {@code null} if none. */
    public User getUser(ConnectionToClient conn) {
        return byConnection.get(conn);
    }

    /** @return true if the given username currently has an active session. */
    public boolean isLoggedIn(String username) {
        return byUsername.containsKey(username);
    }
}
