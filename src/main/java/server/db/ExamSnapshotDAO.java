package server.db;

import common.entities.Exam;
import common.entities.ExamQuestion;
import common.entities.Question;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.util.ArrayList;
import java.util.List;

/** Persists and reads immutable released-exam question snapshots. */
public class ExamSnapshotDAO {
 public boolean createForRelease(int releaseId, Exam exam, QuestionDAO questions){Transaction tx=null; try(Session s=HibernateUtil.getSessionFactory().openSession()){tx=s.beginTransaction(); for(ExamQuestion link:exam.getQuestions()){Question q=questions.getById(link.getQuestionId()); if(q==null) throw new IllegalStateException("Missing question "+link.getQuestionId()); s.createNativeMutationQuery("INSERT INTO ReleasedExamQuestions(release_id,question_id,points,position,question_text,answer_1,answer_2,answer_3,answer_4,correct_answer,image_path) VALUES(:r,:q,:p,:pos,:t,:a1,:a2,:a3,:a4,:c,:img)").setParameter("r",releaseId).setParameter("q",q.getId()).setParameter("p",link.getPoints()).setParameter("pos",link.getPosition()).setParameter("t",q.getQuestionText()).setParameter("a1",q.getAnswer1()).setParameter("a2",q.getAnswer2()).setParameter("a3",q.getAnswer3()).setParameter("a4",q.getAnswer4()).setParameter("c",q.getCorrectAnswer()).setParameter("img",q.getImagePath()).executeUpdate();} tx.commit(); return true;}catch(Exception e){Transactions.rollbackQuietly(tx);System.err.println("[ExamSnapshotDAO] create failed: "+e.getMessage());return false;}}
 @SuppressWarnings("unchecked") public List<ExamSnapshotQuestion> getByRelease(int releaseId){try(Session s=HibernateUtil.getSessionFactory().openSession()){List<Object[]> rows=s.createNativeQuery("SELECT question_id,points,position,question_text,answer_1,answer_2,answer_3,answer_4,correct_answer,image_path FROM ReleasedExamQuestions WHERE release_id=:r ORDER BY position").setParameter("r",releaseId).getResultList(); List<ExamSnapshotQuestion> out=new ArrayList<>(); for(Object[]x:rows)out.add(new ExamSnapshotQuestion(((Number)x[0]).intValue(),((Number)x[1]).intValue(),((Number)x[2]).intValue(),String.valueOf(x[3]),String.valueOf(x[4]),String.valueOf(x[5]),String.valueOf(x[6]),String.valueOf(x[7]),((Number)x[8]).intValue(),x[9]==null?null:x[9].toString())); return out;}catch(Exception e){System.err.println("[ExamSnapshotDAO] read failed: "+e.getMessage());return List.of();}}
}