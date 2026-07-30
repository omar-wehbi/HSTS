package server;

import common.entities.*;
import common.network.*;
import server.db.ExamDAO;
import server.db.ExamReleaseDAO;
import server.db.ExamSessionDAO;
import server.db.ExamSnapshotDAO;
import server.db.ExamSnapshotQuestion;
import server.db.ExecutionReportDAO;
import server.db.GradeDAO;
import server.db.QuestionDAO;

import java.io.Serializable;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;

/** Student results and per-execution teacher statistics (Scenarios 9-10). */
public class ResultsService {
 private final GradeDAO gradeDAO; private final ExamSessionDAO sessionDAO; private final ExamDAO examDAO; private final QuestionDAO questionDAO; private final ExamReleaseDAO releaseDAO; private final ExamSnapshotDAO snapshotDAO; private final ExecutionReportDAO reportDAO; private final Clock clock;
 public ResultsService(GradeDAO g, ExamSessionDAO s, ExamDAO e, QuestionDAO q){this(g,s,e,q,new ExamReleaseDAO(),new ExamSnapshotDAO(),new ExecutionReportDAO(),Clock.systemDefaultZone());}
 ResultsService(GradeDAO g, ExamSessionDAO s, ExamDAO e, QuestionDAO q, Clock c){this(g,s,e,q,new ExamReleaseDAO(),new ExamSnapshotDAO(),new ExecutionReportDAO(),c);}
 ResultsService(GradeDAO g, ExamSessionDAO s, ExamDAO e, QuestionDAO q, ExamReleaseDAO r, ExamSnapshotDAO sn, ExecutionReportDAO rep, Clock c){gradeDAO=g;sessionDAO=s;examDAO=e;questionDAO=q;releaseDAO=r;snapshotDAO=sn;reportDAO=rep;clock=c;}
 public Message getStudentResults(User caller){Authorization.requireRole(caller,Role.STUDENT);sessionDAO.expireOverdueSessions(LocalDateTime.now(clock));List<StudentResultSummary>out=new ArrayList<>();for(Grade g:gradeDAO.getVisibleByStudent(caller.getId())){Exam e=examDAO.getById(g.getExamId());ExamSession a=sessionDAO.getById(g.getSessionId());if(e!=null&&a!=null)out.add(new StudentResultSummary(g.getId(),a.getId(),e.getId(),e.getTitle(),g.getEffectiveScore(),g.getStatus(),a.getSubmittedAt()));}return success((Serializable)out);}
 public Message getCheckedExam(User caller,Object payload){Authorization.requireRole(caller,Role.STUDENT);Integer id=pos(payload);if(id==null)return error("GET_CHECKED_EXAM requires a valid grade ID.");Grade g=gradeDAO.getById(id);if(g==null)return error("Grade was not found.");if(g.getStudentId()!=caller.getId())throw new AuthorizationException("You may only view your own checked exams.");if(!g.isVisibleToStudent())return error("The grade is not available until the teacher approves it.");Exam e=examDAO.getById(g.getExamId());ExamSession a=sessionDAO.getById(g.getSessionId());if(e==null||a==null)return error("The checked exam data was not found.");Map<Integer,Integer>selected=new HashMap<>();for(StudentAnswer x:a.getAnswers())selected.put(x.getQuestionId(),x.getSelectedAnswer());List<CheckedAnswer>checked=new ArrayList<>();for(ExamSnapshotQuestion q:snapshotDAO.getByRelease(a.getReleaseId())){Integer c=selected.get(q.getQuestionId());checked.add(new CheckedAnswer(q.getQuestionId(),q.getText(),q.getA1(),q.getA2(),q.getA3(),q.getA4(),c,q.getCorrectAnswer(),q.getPoints(),c!=null&&c==q.getCorrectAnswer()));}return success(new CheckedExamResult(g.getId(),a.getId(),e.getId(),e.getTitle(),g.getEffectiveScore(),g.getStatus(),g.getOverrideJustification(),g.getTeacherComment(),checked));}
 /** Payload is a release ID, because each administered sitting has separate statistics. */
 public Message getTeacherExamResults(User caller,Object payload){Authorization.requireRole(caller,Role.TEACHER);Integer releaseId=pos(payload);if(releaseId==null)return error("GET_EXAM_STATISTICS requires a valid release ID.");sessionDAO.expireOverdueSessions(LocalDateTime.now(clock));ExamRelease rel=releaseDAO.getById(releaseId);if(rel==null)return error("Exam execution was not found.");Exam e=examDAO.getById(rel.getExamId());if(e==null)return error("Exam was not found.");if(e.getTeacherId()!=caller.getId())throw new AuthorizationException("You may only view results for exams that you authored, including sittings administered by other teachers.");List<TeacherResultRow>rows=new ArrayList<>();List<Integer>scores=new ArrayList<>();for(Grade g:gradeDAO.getVisibleByRelease(releaseId)){ExamSession a=sessionDAO.getById(g.getSessionId());if(a==null||a.getReleaseId()!=releaseId)continue;rows.add(new TeacherResultRow(g.getId(),a.getId(),g.getStudentId(),g.getAutoScore(),g.getFinalScore(),g.getStatus(),a.getStatus(),a.getSubmittedAt()));scores.add(g.getEffectiveScore());}Collections.sort(scores);reportDAO.refreshStatistics(releaseId);return success(new TeacherExamResults(releaseId,e.getId(),e.getTitle(),rows,histogram(scores),scores.size(),scores.stream().mapToInt(Integer::intValue).average().orElse(0),median(scores),scores.isEmpty()?null:scores.get(0),scores.isEmpty()?null:scores.get(scores.size()-1)));}
 private static List<HistogramBin>histogram(List<Integer>s){int[]c=new int[10];for(int x:s)c[x==100?9:Math.max(0,Math.min(99,x))/10]++;List<HistogramBin>b=new ArrayList<>();for(int i=0;i<10;i++)b.add(new HistogramBin(i*10,i==9?100:i*10+9,c[i]));return b;}private static double median(List<Integer>s){if(s.isEmpty())return 0;int m=s.size()/2;return s.size()%2==1?s.get(m):(s.get(m-1)+s.get(m))/2.0;}private static Integer pos(Object x){return x instanceof Integer&&(Integer)x>0?(Integer)x:null;}private static Message success(Serializable x){return new Message(Message.Command.SUCCESS,x);}private static Message error(String x){return new Message(Message.Command.ERROR,x);}
}
