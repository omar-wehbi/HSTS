package server;

import common.entities.Exam;
import common.entities.ExamRelease;
import common.entities.Role;
import common.entities.User;
import common.network.ExamReleaseRequest;
import common.network.Message;
import server.db.CourseDAO;
import server.db.ExamDAO;
import server.db.ExamReleaseDAO;
import server.db.ExamSnapshotDAO;
import server.db.QuestionDAO;

import java.io.Serializable;
import java.time.LocalDateTime;

/** Server-side rules for Scenario 5: releasing an approved exam. */
public class ExamReleaseService {
    private final ExamDAO examDAO; private final ExamReleaseDAO releaseDAO;
    private final CourseDAO courseDAO; private final ExamSnapshotDAO snapshotDAO; private final QuestionDAO questionDAO;

    public ExamReleaseService(ExamDAO examDAO, ExamReleaseDAO releaseDAO) {
        this(examDAO, releaseDAO, new CourseDAO(), new ExamSnapshotDAO(), new QuestionDAO());
    }
    public ExamReleaseService(ExamDAO examDAO, ExamReleaseDAO releaseDAO, CourseDAO courseDAO,
                              ExamSnapshotDAO snapshotDAO, QuestionDAO questionDAO) {
        this.examDAO=examDAO; this.releaseDAO=releaseDAO; this.courseDAO=courseDAO;
        this.snapshotDAO=snapshotDAO; this.questionDAO=questionDAO;
    }

    public Message release(User caller, Object payload) {
        Authorization.requireRole(caller, Role.TEACHER);
        if (!(payload instanceof ExamReleaseRequest)) return error("RELEASE_EXAM requires an ExamReleaseRequest payload.");
        ExamReleaseRequest request=(ExamReleaseRequest)payload;
        String invalid=validateRequest(request); if(invalid!=null)return error(invalid);
        Exam exam=examDAO.getById(request.getExamId());
        if(exam==null)return error("Exam not found.");
        if(exam.getDurationMinutes()<=0)return error("Exam duration must be positive.");
        LocalDateTime expectedClose=request.getOpenTime().plusMinutes(exam.getDurationMinutes());
        if(!request.getCloseTime().equals(expectedClose))
            return error("Close time must equal open time plus the exam duration ("
                    + exam.getDurationMinutes() + " minutes).");
        if(!courseDAO.isTeacherAssigned(caller.getId(), exam.getCourseId()))
            throw new AuthorizationException("You may release only exams in courses you teach.");
        if(!exam.isCurrent())return error("Only the current exam version can be released.");
        if(!exam.isApproved())return error("Only an approved exam can be released.");
        String code=request.getExecutionCode().toUpperCase();
        if(releaseDAO.executionCodeConflicts(code, request.getOpenTime(), request.getCloseTime()))
            return error("Execution code conflicts with another active or scheduled release.");
        ExamRelease saved=releaseDAO.create(new ExamRelease(exam.getId(),caller.getId(),code,request.getOpenTime(),request.getCloseTime()));
        if(saved==null)return error("Exam release failed.");
        saved.setExamTitle(exam.getTitle());
        if(!snapshotDAO.createForRelease(saved.getId(),exam,questionDAO)){
            releaseDAO.delete(saved.getId());
            return error("Exam release failed while preserving the exam version.");
        }
        return success(saved);
    }

    public Message getReleased(User caller) {
        Authorization.requireRole(caller,Role.TEACHER,Role.COORDINATOR,Role.PRINCIPAL);
        if(caller.getRole()==Role.TEACHER)return success((Serializable)releaseDAO.getByTeacher(caller.getId()));
        return success((Serializable)releaseDAO.getAll());
    }

    private static String validateRequest(ExamReleaseRequest request){
        if(request.getExamId()<=0)return "A valid exam ID is required.";
        if(request.getExecutionCode()==null||!request.getExecutionCode().matches("[A-Za-z0-9]{4}"))
            return "Execution code must contain exactly 4 letters or digits.";
        if(request.getOpenTime()==null||request.getCloseTime()==null)return "Open and close date/time are required.";
        if(!request.getCloseTime().isAfter(request.getOpenTime()))return "Close time must be after open time.";
        return null;
    }
    private static Message success(Serializable p){return new Message(Message.Command.SUCCESS,p);} private static Message error(String s){return new Message(Message.Command.ERROR,s);}
}
