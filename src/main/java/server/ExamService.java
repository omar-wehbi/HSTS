package server;

import common.entities.Exam;
import common.entities.ExamStatus;
import common.entities.Role;
import common.entities.User;
import common.network.AutoExamRequest;
import common.network.ExamRejectionRequest;
import common.network.Message;
import common.network.Message.Command;
import server.db.ExamDAO;
import server.db.QuestionSource;

import java.io.Serializable;

/**
 * Authorization and validation service for exam building and approval.
 *
 * <p>Implements:</p>
 * <ul>
 *     <li>Scenario 3 — manual and automatic exam building;</li>
 *     <li>Scenario 3 — versioned exam editing;</li>
 *     <li>Scenario 4 — submission, approval and rejection;</li>
 *     <li>Scenario 4 — storing a written rejection reason.</li>
 * </ul>
 *
 * <p>Teachers create and edit exams. Coordinators approve or reject
 * exams. The principal has read-only access.</p>
 */
public class ExamService {

    private final ExamDAO examDAO;
    private final QuestionSource questionSource;
    private final AutoExamGenerator autoExamGenerator;

    public ExamService(ExamDAO examDAO,
                       QuestionSource questionSource,
                       AutoExamGenerator autoExamGenerator) {

        this.examDAO = examDAO;
        this.questionSource = questionSource;
        this.autoExamGenerator = autoExamGenerator;
    }

    // ===== reads ==========================================================

    /**
     * Returns all current exams.
     *
     * <p>Available to teachers, coordinators and the principal.</p>
     */
    public Message getAll(User caller) {
        Authorization.requireRole(
                caller,
                Role.TEACHER,
                Role.COORDINATOR,
                Role.PRINCIPAL
        );

        return success((Serializable) examDAO.getAllCurrent());
    }

    /**
     * Returns one exam by its exact database ID.
     *
     * <p>Payload: Integer exam ID.</p>
     */
    public Message getById(User caller, Object payload) {
        Authorization.requireRole(
                caller,
                Role.TEACHER,
                Role.COORDINATOR,
                Role.PRINCIPAL
        );

        if (!(payload instanceof Integer)) {
            return error("GET_EXAM requires an exam ID (Integer).");
        }

        int examId = (Integer) payload;

        if (examId <= 0) {
            return error("A valid exam ID is required.");
        }

        Exam exam = examDAO.getById(examId);

        if (exam == null) {
            return error("Exam not found.");
        }

        if (caller.getRole() == Role.TEACHER
                && exam.getTeacherId() != caller.getId()) {

            throw new AuthorizationException(
                    "You may only view exams that you authored."
            );
        }

        return success(exam);
    }

    /**
     * Returns current exams authored by the logged-in teacher.
     */
    public Message getMine(User caller) {
        Authorization.requireRole(caller, Role.TEACHER);

        return success((Serializable)
                examDAO.getCurrentByTeacher(caller.getId()));
    }

    /**
     * Returns current exams waiting for coordinator approval.
     */
    public Message getPending(User caller) {
        Authorization.requireRole(caller, Role.COORDINATOR);

        return success((Serializable)
                examDAO.getPendingApproval());
    }

    // ===== teacher actions ===============================================

    /**
     * Creates a manually built exam.
     *
     * <p>Payload: Exam.</p>
     */
    public Message create(User caller, Object payload) {
        Authorization.requireRole(caller, Role.TEACHER);

        if (!(payload instanceof Exam)) {
            return error("CREATE_EXAM requires an Exam payload.");
        }

        Exam exam = (Exam) payload;

        prepareNewExam(exam, caller.getId());

        String invalid = ExamValidator.validateExam(exam);

        if (invalid != null) {
            return error(invalid);
        }

        Exam saved = examDAO.create(exam);

        return saved != null
                ? success(toWireExam(saved))
                : error("Exam creation failed.");
    }

    /**
     * Creates a new version of an existing exam.
     *
     * <p>Payload: Exam containing the current exam ID.</p>
     */
    public Message update(User caller, Object payload) {
        Authorization.requireRole(caller, Role.TEACHER);

        if (!(payload instanceof Exam)) {
            return error("UPDATE_EXAM requires an Exam payload.");
        }

        Exam requested = (Exam) payload;

        if (requested.getId() <= 0) {
            return error(
                    "UPDATE_EXAM requires the current exam ID."
            );
        }

        Exam current = examDAO.getById(requested.getId());

        if (current == null) {
            return error("Exam not found.");
        }

        if (current.getTeacherId() != caller.getId()) {
            throw new AuthorizationException(
                    "You may only edit exams that you authored."
            );
        }

        if (!current.isCurrent()) {
            return error(
                    "Only the current exam version can be edited."
            );
        }

        if (!current.isEditable()) {
            return error(
                    "Only a draft or rejected exam can be edited."
            );
        }

        prepareUpdatedExam(requested, current, caller.getId());

        String invalid = ExamValidator.validateExam(requested);

        if (invalid != null) {
            return error(invalid);
        }

        Exam updated = examDAO.createNewVersion(
                current.getId(),
                requested
        );

        return updated != null
                ? success(toWireExam(updated))
                : error("Exam update failed.");
    }

    /**
     * Automatically generates and stores an exam.
     *
     * <p>Payload: AutoExamRequest.</p>
     */
    public Message generateAuto(User caller, Object payload) {
        Authorization.requireRole(caller, Role.TEACHER);

        if (!(payload instanceof AutoExamRequest)) {
            return error(
                    "GENERATE_EXAM_AUTO requires an AutoExamRequest payload."
            );
        }

        AutoExamRequest request = (AutoExamRequest) payload;

        request.setTeacherId(caller.getId());

        String invalid =
                ExamValidator.validateAutoRequest(request);

        if (invalid != null) {
            return error(invalid);
        }

        try {
            Exam generated = autoExamGenerator.generate(
                    request,
                    questionSource.getByCourse(request.getCourseId())
            );

            prepareNewExam(generated, caller.getId());

            Exam saved = examDAO.create(generated);

            return saved != null
                    ? success(toWireExam(saved))
                    : error("Automatic exam creation failed.");

        } catch (IllegalArgumentException exception) {
            return error(exception.getMessage());
        }
    }

    /**
     * Submits an exam for coordinator approval.
     *
     * <p>Payload: Integer exam ID.</p>
     */
    public Message submitForApproval(User caller, Object payload) {
        Authorization.requireRole(caller, Role.TEACHER);

        if (!(payload instanceof Integer)) {
            return error(
                    "SUBMIT_EXAM_FOR_APPROVAL requires an exam ID (Integer)."
            );
        }

        int examId = (Integer) payload;

        Exam exam = examDAO.getById(examId);

        if (exam == null) {
            return error("Exam not found.");
        }

        if (exam.getTeacherId() != caller.getId()) {
            throw new AuthorizationException(
                    "You may only submit exams that you authored."
            );
        }

        if (!exam.isCurrent()) {
            return error(
                    "Only the current exam version can be submitted."
            );
        }

        String invalid =
                ExamValidator.validateSubmission(exam);

        if (invalid != null) {
            return error(invalid);
        }

        Exam submitted =
                examDAO.submitForApproval(examId);

        return submitted != null
                ? success(submitted)
                : error("Submitting the exam failed.");
    }

    /**
     * Deletes a draft or rejected exam.
     *
     * <p>Payload: Integer exam ID.</p>
     */
    public Message delete(User caller, Object payload) {
        Authorization.requireRole(caller, Role.TEACHER);

        if (!(payload instanceof Integer)) {
            return error("DELETE_EXAM requires an exam ID (Integer).");
        }

        int examId = (Integer) payload;

        Exam exam = examDAO.getById(examId);

        if (exam == null) {
            return error("Exam not found.");
        }

        if (exam.getTeacherId() != caller.getId()) {
            throw new AuthorizationException(
                    "You may only delete exams that you authored."
            );
        }

        if (!exam.isEditable()) {
            return error(
                    "Only draft or rejected exams can be deleted."
            );
        }

        boolean deleted = examDAO.delete(examId);

        return deleted
                ? success(examId)
                : error("Exam deletion failed.");
    }

    // ===== coordinator actions ===========================================

    /**
     * Approves an exam waiting for approval.
     *
     * <p>Payload: Integer exam ID.</p>
     */
    public Message approve(User caller, Object payload) {
        Authorization.requireRole(caller, Role.COORDINATOR);

        if (!(payload instanceof Integer)) {
            return error(
                    "APPROVE_EXAM requires an exam ID (Integer)."
            );
        }

        int examId = (Integer) payload;

        Exam exam = examDAO.getById(examId);

        String invalid =
                ExamValidator.validateApproval(exam);

        if (invalid != null) {
            return error(invalid);
        }

        if (!exam.isCurrent()) {
            return error(
                    "Only the current exam version can be approved."
            );
        }

        Exam approved = examDAO.approve(
                examId,
                caller.getId()
        );

        return approved != null
                ? success(approved)
                : error("Exam approval failed.");
    }

    /**
     * Rejects an exam with a mandatory written reason.
     *
     * <p>Payload: ExamRejectionRequest.</p>
     */
    public Message reject(User caller, Object payload) {
        Authorization.requireRole(caller, Role.COORDINATOR);

        if (!(payload instanceof ExamRejectionRequest)) {
            return error(
                    "REJECT_EXAM requires an ExamRejectionRequest payload."
            );
        }

        ExamRejectionRequest request =
                (ExamRejectionRequest) payload;

        request.setCoordinatorId(caller.getId());

        Exam exam =
                examDAO.getById(request.getExamId());

        String invalid =
                ExamValidator.validateRejection(exam, request);

        if (invalid != null) {
            return error(invalid);
        }

        if (!exam.isCurrent()) {
            return error(
                    "Only the current exam version can be rejected."
            );
        }

        Exam rejected = examDAO.reject(
                request.getExamId(),
                caller.getId(),
                request.getReason().trim()
        );

        return rejected != null
                ? success(rejected)
                : error("Exam rejection failed.");
    }

    // ===== helpers ========================================================

    /**
     * Sets fields controlled by the server for a new exam.
     */
    private static void prepareNewExam(Exam exam,
                                       int teacherId) {

        exam.setId(0);
        exam.setBaseId(0);
        exam.setTeacherId(teacherId);
        exam.setVersion(1);
        exam.setCurrent(true);
        exam.setStatus(ExamStatus.DRAFT);
        exam.setCoordinatorId(null);
        exam.setRejectionReason(null);
    }

    /**
     * Sets fields controlled by the server for a new exam version.
     */
    private static void prepareUpdatedExam(Exam requested,
                                           Exam current,
                                           int teacherId) {

        requested.setTeacherId(teacherId);
        requested.setCourseId(current.getCourseId());
        requested.setBaseId(current.getBaseId());
        requested.setVersion(current.getVersion() + 1);
        requested.setCurrent(true);
        requested.setStatus(ExamStatus.DRAFT);
        requested.setCoordinatorId(null);
        requested.setRejectionReason(null);
    }

    private static Message success(Serializable payload) {
        return new Message(Command.SUCCESS, payload);
    }

    private static Message error(String reason) {
        return new Message(Command.ERROR, reason);
    }

    /** Plain copy for OCSF serialization (avoids Hibernate-managed entity state). */
    private static Exam toWireExam(Exam source) {
        Exam exam = new Exam();
        exam.setId(source.getId());
        exam.setBaseId(source.getBaseId());
        exam.setVersion(source.getVersion());
        exam.setCurrent(source.isCurrent());
        exam.setCourseId(source.getCourseId());
        exam.setTeacherId(source.getTeacherId());
        exam.setTitle(source.getTitle());
        exam.setDurationMinutes(source.getDurationMinutes());
        exam.setStudentInstructions(source.getStudentInstructions());
        exam.setTeacherNotes(source.getTeacherNotes());
        exam.setStatus(source.getStatus());
        exam.setRejectionReason(source.getRejectionReason());
        exam.setCoordinatorId(source.getCoordinatorId());
        exam.setCourseName(source.getCourseName());
        exam.setQuestions(new java.util.ArrayList<>(source.getQuestions()));
        return exam;
    }
}