package server;

import common.entities.*;
import common.network.ApproveGradeRequest;
import common.network.Message;
import common.network.OverrideGradeRequest;
import server.db.ExamDAO;
import server.db.ExamSessionDAO;
import server.db.ExamSnapshotDAO;
import server.db.ExamSnapshotQuestion;
import server.db.ExecutionReportDAO;
import server.db.GradeDAO;
import server.db.QuestionDAO;

import java.io.Serializable;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/** Scenario 8: immutable-snapshot grading, approval, comments and justified overrides. */
public class GradingService {
 private final GradeDAO gradeDAO; private final ExamSessionDAO sessionDAO; private final ExamDAO examDAO; private final QuestionDAO questionDAO; private final ExamSnapshotDAO snapshotDAO; private final ExecutionReportDAO reportDAO; private final Clock clock;
 public GradingService(GradeDAO g, ExamSessionDAO s, ExamDAO e, QuestionDAO q){this(g,s,e,q,new ExamSnapshotDAO(),new ExecutionReportDAO(),Clock.systemDefaultZone());}
 GradingService(GradeDAO g, ExamSessionDAO s, ExamDAO e, QuestionDAO q, Clock c){this(g,s,e,q,new ExamSnapshotDAO(),new ExecutionReportDAO(),c);}
 GradingService(GradeDAO g, ExamSessionDAO s, ExamDAO e, QuestionDAO q, ExamSnapshotDAO sn, ExecutionReportDAO r, Clock c){gradeDAO=g;sessionDAO=s;examDAO=e;questionDAO=q;snapshotDAO=sn;reportDAO=r;clock=c;}
 public Message autoGrade(User caller,Object payload){Authorization.requireRole(caller,Role.TEACHER);Integer id=pos(payload);if(id==null)return error("GRADE_EXAM_AUTO requires a valid session ID.");LocalDateTime now=LocalDateTime.now(clock);sessionDAO.expireOverdueSessions(now);ExamSession at=sessionDAO.getById(id);if(at==null)return error("Exam session was not found.");if(at.getStatus()==ExamSessionStatus.IN_PROGRESS)return error("An exam can be graded only after it is submitted or timed out.");Exam exam=examDAO.getById(at.getExamId());if(exam==null)return error("Exam was not found.");author(caller,exam);Grade old=gradeDAO.getBySessionId(id);if(old!=null)return success(old);Map<Integer,Integer>ans=new HashMap<>();for(StudentAnswer a:at.getAnswers())ans.put(a.getQuestionId(),a.getSelectedAnswer());int score=0;for(ExamSnapshotQuestion q:snapshotDAO.getByRelease(at.getReleaseId()))if(ans.get(q.getQuestionId())!=null&&ans.get(q.getQuestionId())==q.getCorrectAnswer())score+=q.getPoints();Grade saved=gradeDAO.create(new Grade(at.getId(),exam.getId(),at.getStudentId(),score,now));return saved==null?error("Could not save the computerized grade."):success(saved);}
 public Message approve(User caller,Object payload){Authorization.requireRole(caller,Role.TEACHER);int id;String comment=null;if(payload instanceof Integer)id=(Integer)payload;else if(payload instanceof ApproveGradeRequest){id=((ApproveGradeRequest)payload).getGradeId();comment=cleanComment(((ApproveGradeRequest)payload).getTeacherComment());}else return error("APPROVE_GRADE requires a grade ID or ApproveGradeRequest.");if(id<=0)return error("A valid grade ID is required.");Grade g=gradeDAO.getById(id);if(g==null)return error("Grade was not found.");Exam e=examDAO.getById(g.getExamId());author(caller,e);if(g.getStatus()!=GradeStatus.AUTO_GRADED)return error("Only an unapproved computerized grade can be approved.");Grade saved=gradeDAO.approve(id,caller.getId(),LocalDateTime.now(clock),comment);if(saved!=null){ExamSession at=sessionDAO.getById(saved.getSessionId());if(at!=null)reportDAO.refreshStatistics(at.getReleaseId());}return saved==null?error("Could not approve the grade."):success(saved);}
 public Message override(User caller,Object payload){Authorization.requireRole(caller,Role.TEACHER);if(!(payload instanceof OverrideGradeRequest))return error("OVERRIDE_GRADE requires an OverrideGradeRequest payload.");OverrideGradeRequest r=(OverrideGradeRequest)payload;if(r.getGradeId()<=0||r.getNewScore()<0||r.getNewScore()>100)return error("A valid grade and score from 0 to 100 are required.");String why=r.getJustification();if(why==null||why.trim().isEmpty())return error("A written justification is required when changing a score.");Grade g=gradeDAO.getById(r.getGradeId());if(g==null)return error("Grade was not found.");author(caller,examDAO.getById(g.getExamId()));Grade saved=gradeDAO.override(g.getId(),r.getNewScore(),why.trim(),caller.getId(),LocalDateTime.now(clock));if(saved!=null){ExamSession at=sessionDAO.getById(saved.getSessionId());if(at!=null)reportDAO.refreshStatistics(at.getReleaseId());}return saved==null?error("Could not change the grade."):success(saved);}
 public Message listByRelease(User caller,Object payload){
  Authorization.requireRole(caller,Role.TEACHER);
  Integer releaseId=pos(payload);
  if(releaseId==null)return error("GET_RELEASE_GRADES requires a valid release ID.");
  java.util.List<Grade> grades=gradeDAO.getByRelease(releaseId);
  if(!grades.isEmpty()){
   author(caller,examDAO.getById(grades.get(0).getExamId()));
   return success((Serializable)grades);
  }
  java.util.List<ExamSession> sessions=sessionDAO.getByRelease(releaseId);
  if(!sessions.isEmpty())author(caller,examDAO.getById(sessions.get(0).getExamId()));
  return success((Serializable)grades);
 }
 private static String cleanComment(String x){if(x==null||x.isBlank())return null;x=x.trim();if(x.length()>2000)throw new IllegalArgumentException("Teacher comment must contain at most 2000 characters.");return x;}private static void author(User u,Exam e){if(e==null)throw new AuthorizationException("Exam was not found.");if(e.getTeacherId()!=u.getId())throw new AuthorizationException("You may only grade exams that you authored.");}private static Integer pos(Object x){return x instanceof Integer&&(Integer)x>0?(Integer)x:null;}private static Message success(Serializable x){return new Message(Message.Command.SUCCESS,x);}private static Message error(String x){return new Message(Message.Command.ERROR,x);}
}
