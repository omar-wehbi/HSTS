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
import server.db.ExamDAO;
import server.db.ExamReleaseDAO;
import server.db.ExamSessionDAO;
import server.db.GradeDAO;
import server.db.PrincipalReportDAO;
import server.db.StudyBotDAO;
import server.bot.ExternalStudyBotApiAdapter;

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
    private final ExamDAO examDAO = new ExamDAO();
    private final ExamReleaseDAO examReleaseDAO = new ExamReleaseDAO();
    private final ExamSessionDAO examSessionDAO = new ExamSessionDAO();
    private final GradeDAO gradeDAO = new GradeDAO();
    private final PrincipalReportDAO principalReportDAO = new PrincipalReportDAO();
    private final StudyBotDAO studyBotDAO = new StudyBotDAO();
    private final AutoExamGenerator autoExamGenerator = new AutoExamGenerator();
    /** Facade holding all question-bank rules (auth + validation), unit-tested with mocks. */
    private final QuestionService questions   = new QuestionService(questionDAO, courseDAO);

    private final ExamService exams =
            new ExamService(examDAO, questionDAO, autoExamGenerator);

    private final ExamReleaseService releases =
            new ExamReleaseService(examDAO, examReleaseDAO);

    private final ExamExecutionService execution =
            new ExamExecutionService(examReleaseDAO, examDAO, examSessionDAO, questionDAO, userDAO);

    private final GradingService grading =
            new GradingService(gradeDAO, examSessionDAO, examDAO, questionDAO);

    private final ResultsService results =
            new ResultsService(gradeDAO, examSessionDAO, examDAO, questionDAO);

    private final PrincipalReportService principalReports =
            new PrincipalReportService(principalReportDAO);

    private final StudyBotService studyBot =
            new StudyBotService(studyBotDAO, userDAO, examSessionDAO, new ExternalStudyBotApiAdapter());

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
                // ----- Exam building and approval -----
                case CREATE_EXAM:
                    safeSend(client, exams.create(caller, request.getPayload()));
                    break;

                case UPDATE_EXAM:
                    safeSend(client, exams.update(caller, request.getPayload()));
                    break;

                case GET_EXAMS:
                    safeSend(client, exams.getAll(caller));
                    break;

                case GET_EXAM:
                    safeSend(client, exams.getById(caller, request.getPayload()));
                    break;

                case GET_MY_EXAMS:
                    safeSend(client, exams.getMine(caller));
                    break;

                case GET_PENDING_EXAMS:
                    safeSend(client, exams.getPending(caller));
                    break;

                case GENERATE_EXAM_AUTO:
                    safeSend(client, exams.generateAuto(caller, request.getPayload()));
                    break;

                case DELETE_EXAM:
                    safeSend(client, exams.delete(caller, request.getPayload()));
                    break;

                case SUBMIT_EXAM_FOR_APPROVAL:
                    safeSend(client,
                            exams.submitForApproval(caller, request.getPayload()));
                    break;

                case APPROVE_EXAM:
                    safeSend(client, exams.approve(caller, request.getPayload()));
                    break;

                case REJECT_EXAM:
                    safeSend(client, exams.reject(caller, request.getPayload()));
                    break;

                case RELEASE_EXAM:
                    safeSend(client, releases.release(caller, request.getPayload()));
                    break;

                case GET_RELEASED_EXAMS:
                    safeSend(client, releases.getReleased(caller));
                    break;

                case START_EXAM_SESSION:
                    safeSend(client, execution.start(caller, request.getPayload()));
                    break;

                case SAVE_ANSWERS:
                    safeSend(client, execution.save(caller, request.getPayload()));
                    break;

                case SUBMIT_ANSWERS:
                    safeSend(client, execution.submit(caller, request.getPayload()));
                    break;

                case GET_EXAM_SESSION:
                    safeSend(client, execution.getSession(caller, request.getPayload()));
                    break;

                case EXTEND_EXAM_TIME:
                    safeSend(client, execution.extend(caller, request.getPayload()));
                    break;

                case GET_EXECUTION_SUMMARY:
                    safeSend(client, execution.summary(caller, request.getPayload()));
                    break;

                case GET_RELEASE_SESSIONS:
                    safeSend(client, execution.sessionsForRelease(caller, request.getPayload()));
                    break;

                case GET_RELEASE_GRADES:
                    safeSend(client, grading.listByRelease(caller, request.getPayload()));
                    break;

                case GRADE_EXAM_AUTO:
                    safeSend(client, grading.autoGrade(caller, request.getPayload()));
                    break;

                case APPROVE_GRADE:
                    safeSend(client, grading.approve(caller, request.getPayload()));
                    break;

                case OVERRIDE_GRADE:
                    safeSend(client, grading.override(caller, request.getPayload()));
                    break;

                case GET_STUDENT_RESULTS:
                    safeSend(client, results.getStudentResults(caller));
                    break;

                case GET_CHECKED_EXAM:
                    safeSend(client, results.getCheckedExam(caller, request.getPayload()));
                    break;

                case GET_EXAM_STATISTICS:
                    safeSend(client, results.getTeacherExamResults(caller, request.getPayload()));
                    break;

                case GET_PRINCIPAL_DATA:
                    safeSend(client, principalReports.getPrincipalData(caller));
                    break;

                case GET_PRINCIPAL_READ_ONLY:
                    safeSend(client, principalReports.getReadOnlyData(caller));
                    break;

                case GET_REPORT:
                    safeSend(client, principalReports.getReport(caller, request.getPayload()));
                    break;

                case GET_EXAM_COMPARISON_REPORT:
                    safeSend(client, principalReports.getExamComparisonReport(caller, request.getPayload()));
                    break;

                case CREATE_STUDY_BOT:
                    safeSend(client, studyBot.create(caller, request.getPayload()));
                    break;
                case GET_STUDY_BOT:
                    safeSend(client, studyBot.getBot(caller, request.getPayload()));
                    break;
                case SET_STUDY_BOT_AVAILABILITY:
                    safeSend(client, studyBot.setAvailability(caller, request.getPayload()));
                    break;
                case ADD_STUDY_BOT_SOURCE:
                    safeSend(client, studyBot.addSource(caller, request.getPayload()));
                    break;
                case ADD_STUDY_BOT_DOCUMENT_SOURCE:
                    safeSend(client, studyBot.addDocumentSource(caller, request.getPayload()));
                    break;
                case UPDATE_STUDY_BOT_SOURCE:
                    safeSend(client, studyBot.updateSource(caller, request.getPayload()));
                    break;
                case DELETE_STUDY_BOT_SOURCE:
                    safeSend(client, studyBot.deleteSource(caller, request.getPayload()));
                    break;
                case GET_STUDY_BOT_SOURCES:
                    safeSend(client, studyBot.getSources(caller, request.getPayload()));
                    break;
                case ASK_STUDY_BOT:
                    safeSend(client, studyBot.ask(caller, request.getPayload()));
                    break;
                case GET_MY_STUDY_BOT_HISTORY:
                    safeSend(client, studyBot.personalHistory(caller));
                    break;
                case GET_STUDY_BOT_USAGE:
                    safeSend(client, studyBot.aggregateHistory(caller, request.getPayload()));
                    break;
                default:
                    safeSend(client, new Message(Command.ERROR,
                            "Unsupported command: " + request.getCommand()));
            }
        } catch (AuthorizationException e) {
            // A handler called Authorization.requireRole(...) and the caller wasn't allowed.
            safeSend(client, new Message(Command.ERROR, e.getMessage()));
        } catch (Throwable e) {
            // Catch Error too (e.g. NoClassDefFoundError after a hot rebuild) so the
            // OCSF worker thread does not die and drop the client connection.
            log("handler threw: " + e);
            e.printStackTrace();
            String detail = e.getMessage();
            if (detail == null || detail.isBlank()) {
                detail = e.getClass().getSimpleName();
            }
            safeSend(client, new Message(Command.ERROR, "Server error: " + detail));
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
        try {
            client.sendToClient(response);
        } catch (Exception e) {
            log("failed to send to " + client + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void log(String text) { System.out.println("[HSTSServer] " + text); }
}
