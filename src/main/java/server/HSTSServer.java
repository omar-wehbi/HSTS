package server;

import common.entities.Question;
import common.entities.User;
import common.network.Credentials;
import common.network.Message;
import common.network.Message.Command;
import ocsf.server.AbstractServer;
import ocsf.server.ConnectionToClient;
import server.db.CourseDAO;
import server.db.QuestionDAO;
import server.db.UserDAO;

import java.io.Serializable;
import java.util.List;

/**
 * The HSTS Fat Server (Logic tier) — the secure gatekeeper.
 *
 * <p>Extends the OCSF {@link AbstractServer}. Every client request is a
 * {@link Message}; this server validates it, routes it to the right DAO, and
 * replies with a {@link Message} (SUCCESS or ERROR). Clients never touch the
 * database directly.
 */
public class HSTSServer extends AbstractServer {

    private final QuestionDAO    questionDAO = new QuestionDAO();
    private final CourseDAO      courseDAO   = new CourseDAO();
    private final UserDAO        userDAO     = new UserDAO();
    private final SessionManager sessions    = new SessionManager();

    public HSTSServer(int port) {
        super(port);
    }

    @Override
    protected void handleMessageFromClient(Object msg, ConnectionToClient client) {
        log("received from " + client + ": " + msg);

        if (!(msg instanceof Message)) {
            safeSend(client, new Message(Command.ERROR, "Unrecognized message type."));
            return;
        }

        Message request = (Message) msg;
        try {
            switch (request.getCommand()) {
                case LOGIN:
                    handleLogin(request, client);
                    break;
                case LOGOUT:
                    sessions.logout(client);
                    safeSend(client, new Message(Command.SUCCESS));
                    break;
                case GET_CURRENT_USER:
                    safeSend(client, new Message(Command.SUCCESS, sessions.getUser(client)));
                    break;
                case GET_COURSES:
                    safeSend(client, new Message(Command.SUCCESS, (Serializable) courseDAO.getAll()));
                    break;
                case GET_QUESTIONS:
                    sendBank(client);
                    break;
                case GET_QUESTIONS_BY_COURSE:
                    safeSend(client, new Message(Command.SUCCESS,
                            (Serializable) questionDAO.getByCourse((Integer) request.getPayload())));
                    break;
                case GET_QUESTION_HISTORY:
                    safeSend(client, new Message(Command.SUCCESS,
                            (Serializable) questionDAO.getHistory((Integer) request.getPayload())));
                    break;
                case ADD_QUESTION:
                    handleAdd(request, client);
                    break;
                case UPDATE_QUESTION:
                    handleUpdate(request, client);
                    break;
                case DELETE_QUESTION:
                    handleDelete(request, client);
                    break;
                default:
                    safeSend(client, new Message(Command.ERROR,
                            "Unsupported command: " + request.getCommand()));
            }
        } catch (Exception e) {
            log("handler threw: " + e.getMessage());
            safeSend(client, new Message(Command.ERROR, "Server error: " + e.getMessage()));
        }
    }

    // ===== handlers =======================================================

    private void handleLogin(Message request, ConnectionToClient client) {
        if (!(request.getPayload() instanceof Credentials)) {
            safeSend(client, new Message(Command.ERROR, "LOGIN requires credentials."));
            return;
        }
        Credentials cred = (Credentials) request.getPayload();
        User user = userDAO.authenticate(cred.getUsername(), cred.getPassword());
        if (user == null) {
            safeSend(client, new Message(Command.ERROR, "Invalid username or password."));
            return;
        }
        if (!sessions.login(client, user)) {
            safeSend(client, new Message(Command.ERROR, "This user is already logged in."));
            return;
        }
        log("login: " + user.getUsername() + " (" + user.getRole() + ")");
        safeSend(client, new Message(Command.SUCCESS, user));
    }

    private void handleAdd(Message request, ConnectionToClient client) {
        if (!(request.getPayload() instanceof Question)) {
            safeSend(client, new Message(Command.ERROR, "ADD_QUESTION requires a Question payload."));
            return;
        }
        Question saved = questionDAO.add((Question) request.getPayload());
        if (saved != null) sendBank(client);
        else safeSend(client, new Message(Command.ERROR, "Add failed."));
    }

    private void handleUpdate(Message request, ConnectionToClient client) {
        if (!(request.getPayload() instanceof Question)) {
            safeSend(client, new Message(Command.ERROR, "UPDATE_QUESTION requires a Question payload."));
            return;
        }
        Question updated = questionDAO.update((Question) request.getPayload());
        if (updated != null) sendBank(client);
        else safeSend(client, new Message(Command.ERROR, "Update failed."));
    }

    private void handleDelete(Message request, ConnectionToClient client) {
        if (!(request.getPayload() instanceof Integer)) {
            safeSend(client, new Message(Command.ERROR, "DELETE_QUESTION requires a baseId (Integer)."));
            return;
        }
        boolean ok = questionDAO.delete((Integer) request.getPayload());
        if (ok) sendBank(client);
        else safeSend(client, new Message(Command.ERROR, "Delete failed."));
    }

    /** Replies with the refreshed current question bank. */
    private void sendBank(ConnectionToClient client) {
        List<Question> bank = questionDAO.getAllCurrent();
        log("returning bank: " + bank.size() + " questions");
        safeSend(client, new Message(Command.SUCCESS, (Serializable) bank));
    }

    // ===== OCSF lifecycle hooks ===========================================

    @Override protected void serverStarted() { log("listening on port " + getPort()); }
    @Override protected void serverStopped() { log("stopped."); }
    @Override protected void clientConnected(ConnectionToClient client) { log("client connected: " + client); }
    @Override protected synchronized void clientDisconnected(ConnectionToClient client) {
        sessions.logout(client);   // free the username so the user can log in again
        log("client disconnected: " + client);
    }
    @Override protected synchronized void clientException(ConnectionToClient client, Throwable e) {
        log("client exception (" + client + "): " + e.getMessage());
    }

    // ===== helpers ========================================================

    private void safeSend(ConnectionToClient client, Message response) {
        try { client.sendToClient(response); }
        catch (Exception e) { log("failed to send to " + client + ": " + e.getMessage()); }
    }

    private void log(String text) { System.out.println("[HSTSServer] " + text); }
}
