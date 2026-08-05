package server;

import common.entities.Question;
import common.entities.Role;
import common.entities.User;
import common.network.Message;
import common.network.Message.Command;
import common.network.QuestionFilter;
import server.db.CourseDAO;
import server.db.QuestionDAO;

import java.io.Serializable;

/**
 * The question bank's authorization + validation gate (Person 2, Phase 5).
 *
 * <p><b>Pattern: Facade (service extraction)</b> — {@link HSTSServer}'s switch
 * only resolves the caller and delegates here; every business rule for
 * scenario 2 lives in this one class, testable with Mockito (mock DAOs, plain
 * {@link User} caller) without opening a socket or a database.
 *
 * <p>Security model (R-001, R-063):
 * <ul>
 *   <li><b>every</b> command requires a logged-in caller — an anonymous socket
 *       can no longer even read the bank;</li>
 *   <li>reads are open to all roles (teachers browse, students will see exam
 *       forms, the principal is read-only per scenario 11);</li>
 *   <li>mutations (add / edit / delete) are for <b>teachers only</b>;</li>
 *   <li>mutating payloads must pass {@link QuestionValidator} before any DAO call.</li>
 * </ul>
 *
 * <p>Unauthorized callers trigger an {@link AuthorizationException}, which the
 * server's central catch turns into a clean {@code ERROR} reply (Person 1's
 * Guard pattern). Bad payloads return an {@code ERROR} {@link Message} directly.
 *
 * <p>Mutation replies are <b>surgical</b> (NFR 18 — no forced full refresh):
 * ADD/UPDATE answer with the affected {@link Question} only (illustration bytes
 * stripped — the client already has them), DELETE answers with the removed
 * {@code baseId}. The full bank travels only for an explicit
 * {@code GET_QUESTIONS} (initial load / manual refresh).
 */
public class QuestionService {

    private final QuestionDAO questionDAO;
    private final CourseDAO courseDAO;

    public QuestionService(QuestionDAO questionDAO, CourseDAO courseDAO) {
        this.questionDAO = questionDAO;
        this.courseDAO = courseDAO;
    }

    // ===== reads (any logged-in role) =====================================

    /** All courses — needed by the bank UI's course picker. */
    public Message getCourses(User caller) {
        requireLoggedIn(caller);
        return success((Serializable) courseDAO.getAll());
    }

    /** The current bank (latest version of every question). */
    public Message getBank(User caller) {
        requireLoggedIn(caller);
        return success((Serializable) questionDAO.getAllCurrent());
    }

    /** Current questions of one course; payload: Integer courseId. */
    public Message getByCourse(User caller, Object payload) {
        requireLoggedIn(caller);
        if (!(payload instanceof Integer)) {
            return error("GET_QUESTIONS_BY_COURSE requires a courseId (Integer).");
        }
        return success((Serializable) questionDAO.getByCourse((Integer) payload));
    }

    /** Topic/difficulty pool query; payload: {@link QuestionFilter}. */
    public Message getFiltered(User caller, Object payload) {
        requireLoggedIn(caller);
        if (!(payload instanceof QuestionFilter)) {
            return error("GET_QUESTIONS_FILTERED requires a QuestionFilter payload.");
        }
        QuestionFilter f = (QuestionFilter) payload;
        return success((Serializable)
                questionDAO.getByCourseFiltered(f.getCourseId(), f.getTopic(), f.getDifficulty()));
    }

    /** All versions of one family; payload: Integer baseId. */
    public Message getHistory(User caller, Object payload) {
        requireLoggedIn(caller);
        if (!(payload instanceof Integer)) {
            return error("GET_QUESTION_HISTORY requires a baseId (Integer).");
        }
        return success((Serializable) questionDAO.getHistory((Integer) payload));
    }

    /** Lazy illustration fetch; payload: Integer question id (NFR 18). */
    public Message getImage(User caller, Object payload) {
        requireLoggedIn(caller);
        if (!(payload instanceof Integer)) {
            return error("GET_QUESTION_IMAGE requires a question id (Integer).");
        }
        return success(questionDAO.getImage((Integer) payload));
    }

    // ===== mutations (teachers only) ======================================

    /** Scenario 2.1 — add a question; payload: {@link Question}. */
    public Message add(User caller, Object payload) {
        Authorization.requireRole(caller, Role.TEACHER);
        if (!(payload instanceof Question)) {
            return error("ADD_QUESTION requires a Question payload.");
        }
        Question q = (Question) payload;
        String invalid = QuestionValidator.validate(q);
        if (invalid != null) return error(invalid);
        if (!courseDAO.isTeacherAssigned(caller.getId(), q.getCourseId())) {
            throw new AuthorizationException(
                    "You may add questions only for courses you teach.");
        }

        Question saved = questionDAO.add(q);
        return (saved != null) ? success(withoutImageBytes(saved)) : error("Add failed.");
    }

    /** Scenario 2.2 — versioned edit (old version stays); payload: {@link Question}. */
    public Message update(User caller, Object payload) {
        Authorization.requireRole(caller, Role.TEACHER);
        if (!(payload instanceof Question)) {
            return error("UPDATE_QUESTION requires a Question payload.");
        }
        Question q = (Question) payload;
        String invalid = QuestionValidator.validate(q);
        if (invalid != null) return error(invalid);
        if (q.getBaseId() <= 0) {
            return error("UPDATE_QUESTION requires the question's version-family id (baseId).");
        }
        if (!courseDAO.isTeacherAssigned(caller.getId(), q.getCourseId())) {
            throw new AuthorizationException(
                    "You may edit questions only for courses you teach.");
        }

        Question updated = questionDAO.update(q);
        return (updated != null) ? success(withoutImageBytes(updated)) : error("Update failed.");
    }

    /** Scenario 2.4 — delete a whole version family; payload: Integer baseId. */
    public Message delete(User caller, Object payload) {
        Authorization.requireRole(caller, Role.TEACHER);
        if (!(payload instanceof Integer)) {
            return error("DELETE_QUESTION requires a baseId (Integer).");
        }
        boolean removed = questionDAO.delete((Integer) payload);
        return removed ? success((Integer) payload) : error("Delete failed — question not found.");
    }

    // ===== helpers ========================================================

    /** Logged-in check: any role passes, an anonymous caller does not. */
    private static void requireLoggedIn(User caller) {
        Authorization.requireRole(caller, Role.values());
    }

    /** Mutation replies never echo illustration bytes back (NFR 18). */
    private static Question withoutImageBytes(Question q) {
        q.setImageData(null);
        return q;
    }

    private static Message success(Serializable payload) {
        return new Message(Command.SUCCESS, payload);
    }

    private static Message error(String reason) {
        return new Message(Command.ERROR, reason);
    }
}
