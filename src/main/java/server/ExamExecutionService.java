package server;

import common.entities.*;
import common.network.*;
import server.db.ExamDAO;
import server.db.ExamReleaseDAO;
import server.db.ExamSessionDAO;
import server.db.ExamSnapshotDAO;
import server.db.ExamSnapshotQuestion;
import server.db.ExecutionReportDAO;
import server.db.QuestionDAO;
import server.db.UserDAO;

import java.io.Serializable;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Server rules for scenarios 6 and 7. The server clock is authoritative. */
public class ExamExecutionService {
 private final ExamReleaseDAO releaseDAO; private final ExamDAO examDAO; private final ExamSessionDAO sessionDAO; private final QuestionDAO questionDAO; private final UserDAO userDAO; private final ExamSnapshotDAO snapshotDAO; private final ExecutionReportDAO reportDAO; private final Clock clock;
 public ExamExecutionService(ExamReleaseDAO r, ExamDAO e, ExamSessionDAO s, QuestionDAO q, UserDAO u){this(r,e,s,q,u,new ExamSnapshotDAO(),new ExecutionReportDAO(),Clock.systemDefaultZone());}
 ExamExecutionService(ExamReleaseDAO r, ExamDAO e, ExamSessionDAO s, QuestionDAO q, UserDAO u, Clock c){this(r,e,s,q,u,new ExamSnapshotDAO(),new ExecutionReportDAO(),c);}
 ExamExecutionService(ExamReleaseDAO r, ExamDAO e, ExamSessionDAO s, QuestionDAO q, UserDAO u, ExamSnapshotDAO snap, ExecutionReportDAO rep, Clock c){releaseDAO=r;examDAO=e;sessionDAO=s;questionDAO=q;userDAO=u;snapshotDAO=snap;reportDAO=rep;clock=c;}
 public Message previewByCode(User caller,Object payload){
  Authorization.requireRole(caller,Role.STUDENT);
  if(!(payload instanceof String)||((String)payload).isBlank())
   return error("PREVIEW_EXAM_BY_CODE requires a non-empty execution code.");
  String code=((String)payload).trim().toUpperCase();
  if(!code.matches("[A-Za-z0-9]{4}"))return error("Execution code must contain exactly 4 letters or digits.");
  LocalDateTime now=LocalDateTime.now(clock);
  sessionDAO.expireOverdueSessions(now);
  ExamRelease rel;
  try{rel=releaseDAO.getOpenByExecutionCode(code,now);}
  catch(IllegalStateException ex){return error("Could not validate the execution code.");}
  if(rel==null)return error("Invalid execution code or the exam is not currently open.");
  Exam exam=examDAO.getById(rel.getExamId());
  if(exam==null)return error("The released exam no longer exists.");
  if(!userDAO.isEnrolled(caller.getId(),exam.getCourseId()))
   return error("You are not enrolled in this course.");
  int qCount=snapshotDAO.getByRelease(rel.getId()).size();
  if(qCount==0){
   // Snapshot may be missing on legacy rows — fall back to live exam size.
   qCount=exam.getQuestions()==null?0:exam.getQuestions().size();
  }
  return success(new ExamPreview(rel.getId(),exam.getId(),exam.getTitle(),
          exam.getStudentInstructions(),exam.getDurationMinutes(),qCount,code));
 }
 public Message start(User caller,Object payload){Authorization.requireRole(caller,Role.STUDENT);if(!(payload instanceof StartExamRequest))return error("START_EXAM_SESSION requires a StartExamRequest payload.");StartExamRequest req=(StartExamRequest)payload;if(req.getExecutionCode()==null||!req.getExecutionCode().matches("[A-Za-z0-9]{4}"))return error("Execution code must contain exactly 4 letters or digits.");if(req.getIdNumber()==null||req.getIdNumber().isBlank())return error("Student ID number is required.");if(!req.getIdNumber().equals(caller.getIdNumber()))return error("The ID number does not match the logged-in student.");LocalDateTime now=LocalDateTime.now(clock);sessionDAO.expireOverdueSessions(now);ExamRelease rel;
 try { rel=releaseDAO.getOpenByExecutionCode(req.getExecutionCode().toUpperCase(),now); }
 catch(IllegalStateException ex){return error("Could not validate the execution code.");}
 if(rel==null)return error("Invalid execution code or the exam is not currently open.");Exam exam=examDAO.getById(rel.getExamId());if(exam==null)return error("The released exam no longer exists.");if(!userDAO.isEnrolled(caller.getId(),exam.getCourseId()))return error("You are not enrolled in this course.");ExamSession old=sessionDAO.getByReleaseAndStudent(rel.getId(),caller.getId());if(old!=null){if(old.isActiveAt(now))return success(buildForm(old,exam));return error("You have already attempted this exam.");}LocalDateTime deadline=now.plusMinutes(exam.getDurationMinutes());if(deadline.isAfter(rel.getCloseTime()))deadline=rel.getCloseTime();ExamSession at=sessionDAO.create(new ExamSession(rel.getId(),exam.getId(),caller.getId(),now,deadline));return at==null?error("Could not start the exam session."):success(buildForm(at,exam));}
 public Message save(User caller,Object payload){Authorization.requireRole(caller,Role.STUDENT);if(!(payload instanceof SaveAnswersRequest))return error("SAVE_ANSWERS requires a SaveAnswersRequest payload.");SaveAnswersRequest req=(SaveAnswersRequest)payload;ExamSession at=sessionDAO.getById(req.getSessionId());if(at==null)return error("Exam session was not found.");if(at.getStudentId()!=caller.getId())throw new AuthorizationException("You may only save your own exam session.");LocalDateTime now=LocalDateTime.now(clock);if(at.getStatus()!=ExamSessionStatus.IN_PROGRESS||!now.isBefore(at.getDeadline())){sessionDAO.expireOverdueSession(at.getId(),now);return error("The exam is closed.");}Exam exam=examDAO.getById(at.getExamId());String v=validateAnswers(req.getAnswers(),exam,at.getReleaseId());if(v!=null)return error(v);ExamSession saved=sessionDAO.saveAnswers(at.getId(),req.getAnswers());return saved==null?error("Could not save answers."):success(saved);}
 public Message submit(User caller,Object payload){Authorization.requireRole(caller,Role.STUDENT);if(!(payload instanceof SubmitAnswersRequest))return error("SUBMIT_ANSWERS requires a SubmitAnswersRequest payload.");SubmitAnswersRequest req=(SubmitAnswersRequest)payload;LocalDateTime now=LocalDateTime.now(clock);ExamSession at=sessionDAO.getById(req.getSessionId());if(at==null)return error("Exam session was not found.");if(at.getStudentId()!=caller.getId())throw new AuthorizationException("You may only submit your own exam session.");if(at.getStatus()==ExamSessionStatus.IN_PROGRESS&&now.isAfter(at.getDeadline())){sessionDAO.expireOverdueSession(at.getId(),now);return error("The exam time has expired. Late answers were not accepted; previously saved answers were submitted automatically.");}if(at.getStatus()!=ExamSessionStatus.IN_PROGRESS)return error("This exam session is already closed.");Exam exam=examDAO.getById(at.getExamId());String v=validateAnswers(req.getAnswers(),exam,at.getReleaseId());if(v!=null)return error(v);ExamSession saved=sessionDAO.submit(at.getId(),req.getAnswers(),now,ExamSessionStatus.SUBMITTED);return saved==null?error("Could not close the exam session."):success(saved);}
 public Message getSession(User caller,Object payload){Authorization.requireRole(caller,Role.STUDENT,Role.TEACHER);if(!(payload instanceof Integer)||(Integer)payload<=0)return error("GET_EXAM_SESSION requires a valid session ID.");sessionDAO.expireOverdueSessions(LocalDateTime.now(clock));ExamSession at=sessionDAO.getById((Integer)payload);if(at==null)return error("Exam session was not found.");if(caller.getRole()==Role.STUDENT&&at.getStudentId()!=caller.getId())throw new AuthorizationException("You may only view your own exam session.");if(caller.getRole()==Role.TEACHER){Exam e=examDAO.getById(at.getExamId());if(e==null||e.getTeacherId()!=caller.getId())throw new AuthorizationException("You may only view sessions for exams that you authored.");}return success(at);}
 public Message extend(User caller,Object payload){Authorization.requireRole(caller,Role.TEACHER);if(!(payload instanceof ExtendExamTimeRequest))return error("EXTEND_EXAM_TIME requires an ExtendExamTimeRequest payload.");ExtendExamTimeRequest r=(ExtendExamTimeRequest)payload;if(r.getReleaseId()<=0||r.getExtraMinutes()<=0||r.getExtraMinutes()>180)return error("Extension must be between 1 and 180 minutes.");LocalDateTime now=LocalDateTime.now(clock);ExamRelease rel=releaseDAO.getById(r.getReleaseId());if(rel==null)return error("Exam release was not found.");if(rel.getReleasedBy()!=caller.getId())throw new AuthorizationException("You may only extend an exam that you released.");if(now.isBefore(rel.getOpenTime())||now.isAfter(rel.getCloseTime()))return error("Exam time can be extended only while the release is live.");sessionDAO.expireOverdueSessions(now);int n=sessionDAO.extendActiveSessions(rel.getId(),r.getExtraMinutes(),now);return n<=0?error(n==0?"There are no active sessions to extend.":"Could not extend the exam time."):success(Integer.valueOf(n));}
 public Message summary(User caller,Object payload){Authorization.requireRole(caller,Role.TEACHER,Role.COORDINATOR,Role.PRINCIPAL);if(!(payload instanceof Integer)||(Integer)payload<=0)return error("A valid release ID is required.");ExamExecutionSummary s=reportDAO.summary((Integer)payload);if(s==null)return error("Exam execution was not found.");Exam e=examDAO.getById(s.getExamId());if(e==null)return error("Exam was not found.");if(caller.getRole()==Role.TEACHER){ExamRelease rel=releaseDAO.getById(s.getReleaseId());if(rel==null)return error("Exam release was not found.");if(e.getTeacherId()!=caller.getId()&&rel.getReleasedBy()!=caller.getId())throw new AuthorizationException("You may only view executions you authored or administered.");}return success(s);}
 public Message sessionsForRelease(User caller,Object payload){
  Authorization.requireRole(caller,Role.TEACHER,Role.COORDINATOR,Role.PRINCIPAL);
  if(!(payload instanceof Integer)||(Integer)payload<=0)return error("A valid release ID is required.");
  int releaseId=(Integer)payload;
  ExamRelease rel=releaseDAO.getById(releaseId);
  if(rel==null)return error("Exam release was not found.");
  Exam e=examDAO.getById(rel.getExamId());
  if(e==null)return error("Exam was not found.");
  if(caller.getRole()==Role.TEACHER
          &&e.getTeacherId()!=caller.getId()
          &&rel.getReleasedBy()!=caller.getId()){
   throw new AuthorizationException("You may only view sessions for executions you authored or administered.");
  }
  sessionDAO.expireOverdueSessions(LocalDateTime.now(clock));
  return success((Serializable)sessionDAO.getByRelease(releaseId));
 }
  private ExamForm buildForm(ExamSession at,Exam exam){
  List<ExamSnapshotQuestion> snap=snapshotDAO.getByRelease(at.getReleaseId());
  if(snap.isEmpty())throw new IllegalStateException("The released exam has no preserved questions.");
  List<ExamFormQuestion>safe=new ArrayList<>();
  for(ExamSnapshotQuestion q:snap)safe.add(new ExamFormQuestion(q.getQuestionId(),q.getPoints(),q.getPosition(),q.getText(),q.getA1(),q.getA2(),q.getA3(),q.getA4(),q.getImagePath()));
  return new ExamForm(at,exam.getTitle(),exam.getStudentInstructions(),safe);
 }
 private String validateAnswers(List<StudentAnswer>a,Exam exam,int releaseId){if(a==null)return "Answers are required.";List<ExamSnapshotQuestion> snapshot=snapshotDAO.getByRelease(releaseId);if(snapshot.isEmpty())return "The released exam has no preserved questions.";Set<Integer>allowed=new HashSet<>();for(ExamSnapshotQuestion q:snapshot)allowed.add(q.getQuestionId());Set<Integer>seen=new HashSet<>();for(StudentAnswer x:a){if(x==null||!allowed.contains(x.getQuestionId()))return "Submission contains a question that is not part of this released exam.";if(!seen.add(x.getQuestionId()))return "A question may only be answered once.";if(x.getSelectedAnswer()<1||x.getSelectedAnswer()>4)return "Every selected answer must be between 1 and 4.";}return null;}
 public boolean isBotLockedFor(User s){if(s==null||s.getRole()!=Role.STUDENT)return false;LocalDateTime n=LocalDateTime.now(clock);sessionDAO.expireOverdueSessions(n);return sessionDAO.hasActiveSession(s.getId(),n);} private static Message success(Serializable v){return new Message(Message.Command.SUCCESS,v);}private static Message error(String r){return new Message(Message.Command.ERROR,r);}
}
