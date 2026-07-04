package server;

import common.entities.User;
import common.network.Credentials;
import common.network.Message;
import common.network.Message.Command;
import ocsf.server.AbstractServer;
import ocsf.server.ConnectionToClient;
import server.db.CourseDAO;
import server.db.QuestionDAO;
import server.db.UserDAO;


/**
 * The HSTS Fat Server (Logic tier) — the secure gatekeeper.
 *
 * <p>Extends the OCSF {@link AbstractServer}. Every client request is a
 * {@link Message}; this server validates it, routes it to the right DAO, and
 * replies with a {@link Message} (SUCCESS or ERROR). Clients never touch the
 * database directly.
 */
public class HSTSServer extends AbstractServer {

    private final QuestionDAO     questionDAO = new QuestionDAO();
    private final CourseDAO       courseDAO   = new CourseDAO();
    private final UserDAO         userDAO     = new UserDAO();
    private final SessionManager  sessions    = new SessionManager();
    /** Facade holding all question-bank rules (auth + validation), unit-tested with mocks. */
    private final QuestionService questions   = new QuestionService(questionDAO, courseDAO);

    public HSTSServer(int port) {
        super(port);
        // Fail fast: bring the ORM data tier up at construction, not at the first
        // login. A misconfigured database is discovered at server start, and the
        // first user never pays the SessionFactory warm-up cost.
        server.db.HibernateUtil.getSessionFactory();
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
            // Who is asking? Null until LOGIN succeeds — QuestionService rejects
            // anonymous callers for every bank command (Phase 5 security).
            User caller = sessions.getUser(client);

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
                    safeSend(client, questions.getCourses(caller));
                    break;
                case GET_QUESTIONS:
                    safeSend(client, questions.getBank(caller));
                    break;
                case GET_QUESTIONS_BY_COURSE:
                    safeSend(client, questions.getByCourse(caller, request.getPayload()));
                    break;
                case GET_QUESTIONS_FILTERED:
                    safeSend(client, questions.getFiltered(caller, request.getPayload()));
                    break;
                case GET_QUESTION_HISTORY:
                    safeSend(client, questions.getHistory(caller, request.getPayload()));
                    break;
                case GET_QUESTION_IMAGE:
                    safeSend(client, questions.getImage(caller, request.getPayload()));
                    break;
                case ADD_QUESTION:
                    safeSend(client, questions.add(caller, request.getPayload()));
                    break;
                case UPDATE_QUESTION:
                    safeSend(client, questions.update(caller, request.getPayload()));
                    break;
                case DELETE_QUESTION:
                    safeSend(client, questions.delete(caller, request.getPayload()));
                    break;
                default:
                    safeSend(client, new Message(Command.ERROR,
                            "Unsupported command: " + request.getCommand()));
            }
        } catch (AuthorizationException e) {
            // A handler called Authorization.requireRole(...) and the caller wasn't allowed.
            safeSend(client, new Message(Command.ERROR, e.getMessage()));
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
