package common.network;

import java.io.Serializable;

/**
 * Wire protocol envelope exchanged between client and server over OCSF.
 *
 * <p>Every exchange is a {@code Message}: a {@link Command} verb plus an optional
 * {@code payload}. Whatever is placed in the payload MUST be {@link Serializable}.
 */
public class Message implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Protocol verbs understood by both tiers. */
    public enum Command {
        // ----- Authentication: client -> server -----
        LOGIN,                  // payload: Credentials     -> SUCCESS: User  | ERROR: reason
        LOGOUT,                 // payload: null            -> SUCCESS: null
        GET_CURRENT_USER,       // payload: null            -> SUCCESS: User (or null if none)

        // ----- Question bank: client -> server -----
        GET_COURSES,            // payload: null            -> SUCCESS: List<Course>
        GET_QUESTIONS,          // payload: null            -> SUCCESS: List<Question> (current bank)
        GET_QUESTIONS_BY_COURSE,// payload: Integer courseId-> SUCCESS: List<Question>
        GET_QUESTIONS_FILTERED, // payload: QuestionFilter  -> SUCCESS: List<Question> (course + optional topic/difficulty)
        GET_QUESTION_HISTORY,   // payload: Integer baseId  -> SUCCESS: List<Question>
        GET_QUESTION_IMAGE,     // payload: Integer question id -> SUCCESS: byte[] (null if no illustration)
        ADD_QUESTION,           // payload: Question        -> SUCCESS: Question (saved, no image bytes)
        UPDATE_QUESTION,        // payload: Question        -> SUCCESS: Question (new current version)
        DELETE_QUESTION,        // payload: Integer baseId  -> SUCCESS: Integer (removed baseId)

        // ----- Exam building: client -> server -----
        CREATE_EXAM,             // payload: Exam -> SUCCESS: saved Exam
        UPDATE_EXAM,             // payload: Exam -> SUCCESS: new Exam version
        GET_EXAMS,               // payload: null -> SUCCESS: List<Exam>
        GET_EXAM,                // payload: Integer examId -> SUCCESS: Exam
        GET_MY_EXAMS,            // payload: null -> SUCCESS: List<Exam>
        GENERATE_EXAM_AUTO,       // payload: AutoExamRequest -> SUCCESS: generated Exam
        DELETE_EXAM,              // payload: Integer examId -> SUCCESS: Integer (deleted examId)

        // ----- Exam approval: client -> server -----
        GET_PENDING_EXAMS,          // payload: null -> SUCCESS: List<Exam>
        SUBMIT_EXAM_FOR_APPROVAL,   // payload: Integer examId -> SUCCESS: Exam
        APPROVE_EXAM,               // payload: Integer examId -> SUCCESS: Exam
        REJECT_EXAM,                // payload: ExamRejectionRequest -> SUCCESS: Exam

        // ----- Exam release: client -> server -----
        RELEASE_EXAM,               // payload: ExamReleaseRequest -> SUCCESS: ExamRelease
        GET_RELEASED_EXAMS,         // payload: null -> SUCCESS: List<ExamRelease>

        // ----- Exam execution and live extension -----
        START_EXAM_SESSION,          // payload: StartExamRequest -> SUCCESS: ExamForm
        SAVE_ANSWERS,                // payload: SaveAnswersRequest -> SUCCESS: ExamSession
        SUBMIT_ANSWERS,              // payload: SubmitAnswersRequest -> SUCCESS: ExamSession
        GET_EXAM_SESSION,            // payload: Integer sessionId -> SUCCESS: ExamSession
        EXTEND_EXAM_TIME,             // payload: ExtendExamTimeRequest -> SUCCESS: number extended
        GET_EXECUTION_SUMMARY,        // payload: Integer releaseId -> SUCCESS: ExamExecutionSummary
        GET_RELEASE_SESSIONS,         // payload: Integer releaseId -> SUCCESS: List<ExamSession>
        GET_RELEASE_GRADES,           // payload: Integer releaseId -> SUCCESS: List<Grade>

        // ----- Grading -----
        GRADE_EXAM_AUTO,             // payload: Integer sessionId -> SUCCESS: Grade
        APPROVE_GRADE,               // payload: Integer gradeId -> SUCCESS: Grade
        OVERRIDE_GRADE,              // payload: OverrideGradeRequest -> SUCCESS: Grade

        // ----- Results -----
        GET_STUDENT_RESULTS,          // payload: null -> SUCCESS: List<StudentResultSummary>
        GET_CHECKED_EXAM,             // payload: Integer gradeId -> SUCCESS: CheckedExamResult
        GET_EXAM_STATISTICS,          // payload: Integer releaseId -> SUCCESS: TeacherExamResults

        // ----- Principal read-only access and reports -----
        GET_PRINCIPAL_DATA,          // payload: null -> SUCCESS: PrincipalData
        GET_PRINCIPAL_READ_ONLY,     // payload: null -> SUCCESS: PrincipalReadOnlyData
        GET_REPORT,                  // payload: PrincipalReportRequest -> SUCCESS: PrincipalReport
        GET_EXAM_COMPARISON_REPORT,  // payload: ExamComparisonReportRequest -> SUCCESS: PrincipalReport

        // ----- Study bot -----
        CREATE_STUDY_BOT,            // payload: CreateStudyBotRequest
        GET_STUDY_BOT,               // payload: Integer courseId
        SET_STUDY_BOT_AVAILABILITY,  // payload: SetStudyBotAvailabilityRequest
        ADD_STUDY_BOT_SOURCE,        // payload: StudyBotSourceRequest
        ADD_STUDY_BOT_DOCUMENT_SOURCE,// payload: StudyBotDocumentSourceRequest
        UPDATE_STUDY_BOT_SOURCE,     // payload: UpdateStudyBotSourceRequest
        DELETE_STUDY_BOT_SOURCE,     // payload: Integer sourceId
        GET_STUDY_BOT_SOURCES,       // payload: Integer courseId
        ASK_STUDY_BOT,               // payload: StudyBotQuestionRequest -> StudyBotAnswer
        GET_MY_STUDY_BOT_HISTORY,    // payload: null -> List<StudyBotAnswer>
        GET_STUDY_BOT_USAGE,         // payload: Integer courseId -> anonymized StudyBotUsageReport
        // ----- server -> client -----
        SUCCESS,
        ERROR
    }

    private Command command;
    private Object  payload;

    public Message() { }

    public Message(Command command) { this.command = command; }

    public Message(Command command, Object payload) {
        this.command = command;
        this.payload = payload;
    }

    public Command getCommand() { return command; }
    public void setCommand(Command command) { this.command = command; }

    public Object getPayload() { return payload; }
    public void setPayload(Object payload) { this.payload = payload; }

    @Override
    public String toString() {
        return "Message{command=" + command + ", payload=" + payload + '}';
    }
}
