package server.db;

import common.network.PrincipalReadOnlyData;
import org.hibernate.Session;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Generic read-only projections; no mutable entities are exposed to the principal client. */
public class PrincipalReadOnlyDAO {
 public PrincipalReadOnlyData load(){try(Session s=HibernateUtil.getSessionFactory().openSession()){return new PrincipalReadOnlyData(rows(s,"SELECT id,course_id,question_text,topic,difficulty,version,is_current FROM Questions ORDER BY id","id","courseId","text","topic","difficulty","version","current"),rows(s,"SELECT id,course_id,teacher_id,title,duration_minutes,status,version,is_current FROM Exams ORDER BY id","id","courseId","teacherId","title","durationMinutes","status","version","current"),rows(s,"SELECT id,exam_id,released_by,execution_code,open_time,close_time FROM ExamReleases ORDER BY id","id","examId","releasedBy","executionCode","openTime","closeTime"),rows(s,"SELECT id,release_id,exam_id,student_id,started_at,submitted_at,status,actual_duration_minutes FROM ExamSessions ORDER BY id","id","releaseId","examId","studentId","startedAt","submittedAt","status","actualDurationMinutes"),rows(s,"SELECT id,session_id,exam_id,student_id,auto_score,final_score,status,teacher_comment,override_justification FROM Grades ORDER BY id","id","sessionId","examId","studentId","autoScore","finalScore","status","teacherComment","overrideJustification"));}catch(Exception e){throw new IllegalStateException("Could not load principal read-only data.",e);}}
 @SuppressWarnings("unchecked") private static List<Map<String,Serializable>> rows(Session s,String sql,String...keys){List<?> raw=s.createNativeQuery(sql).getResultList();List<Map<String,Serializable>>out=new ArrayList<>();for(Object x:raw){Object[]a=x instanceof Object[]?(Object[])x:new Object[]{x};Map<String,Serializable>m=new LinkedHashMap<>();for(int i=0;i<keys.length;i++)m.put(keys[i],a[i]==null?null:(Serializable)(a[i] instanceof Character?String.valueOf(a[i]):a[i]));out.add(m);}return out;}
}
