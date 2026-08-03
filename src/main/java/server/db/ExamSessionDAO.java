package server.db;

import common.entities.ExamSession;
import common.entities.ExamSessionStatus;
import common.entities.StudentAnswer;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.time.LocalDateTime;
import java.util.List;

/** Persistence for exam attempts and their answers. */
public class ExamSessionDAO {

    public ExamSession create(ExamSession examSession) {
        Transaction tx = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            session.persist(examSession);
            tx.commit();
            return examSession;
        } catch (Exception e) {
            Transactions.rollbackQuietly(tx);
            System.err.println("[ExamSessionDAO] create failed: " + e.getMessage());
            return null;
        }
    }

    public ExamSession getById(int id) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            ExamSession result = session.get(ExamSession.class, id);
            if (result != null) result.setAnswers(getAnswers(id));
            return result;
        } catch (Exception e) {
            System.err.println("[ExamSessionDAO] getById failed: " + e.getMessage());
            return null;
        }
    }

    public ExamSession getByReleaseAndStudent(int releaseId, int studentId) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            ExamSession result = session.createQuery(
                    "FROM ExamSession WHERE releaseId=:r AND studentId=:s ORDER BY id DESC",
                    ExamSession.class)
                    .setParameter("r", releaseId).setParameter("s", studentId)
                    .setMaxResults(1).uniqueResult();
            if (result != null) result.setAnswers(getAnswers(result.getId()));
            return result;
        } catch (Exception e) {
            System.err.println("[ExamSessionDAO] getByReleaseAndStudent failed: " + e.getMessage());
            return null;
        }
    }

    /** All attempts for one release, oldest first (no answer payloads). */
    public List<ExamSession> getByRelease(int releaseId) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                            "FROM ExamSession WHERE releaseId=:r ORDER BY id",
                            ExamSession.class)
                    .setParameter("r", releaseId)
                    .list();
        } catch (Exception e) {
            System.err.println("[ExamSessionDAO] getByRelease failed: " + e.getMessage());
            return List.of();
        }
    }

    public List<StudentAnswer> getAnswers(int sessionId) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery("FROM StudentAnswer WHERE sessionId=:id ORDER BY questionId", StudentAnswer.class)
                    .setParameter("id", sessionId).list();
        } catch (Exception e) {
            System.err.println("[ExamSessionDAO] getAnswers failed: " + e.getMessage());
            return List.of();
        }
    }


    /** Upserts the student's current answer snapshot without closing the attempt. */
    public ExamSession saveAnswers(int sessionId, List<StudentAnswer> answers) {
        Transaction tx=null;
        try(Session session=HibernateUtil.getSessionFactory().openSession()){
            tx=session.beginTransaction(); ExamSession attempt=session.get(ExamSession.class,sessionId);
            if(attempt==null||attempt.getStatus()!=ExamSessionStatus.IN_PROGRESS){tx.rollback();return null;}
            session.createMutationQuery("DELETE FROM StudentAnswer WHERE sessionId=:id").setParameter("id",sessionId).executeUpdate();
            if(answers!=null)for(StudentAnswer answer:answers){StudentAnswer saved=new StudentAnswer(answer.getQuestionId(),answer.getSelectedAnswer());saved.setSessionId(sessionId);session.persist(saved);}
            tx.commit(); attempt.setAnswers(answers); return attempt;
        }catch(Exception e){Transactions.rollbackQuietly(tx);System.err.println("[ExamSessionDAO] saveAnswers failed: "+e.getMessage());return null;}
    }

    /** Replaces the answer snapshot and closes the attempt atomically. */
    public ExamSession submit(int sessionId, List<StudentAnswer> answers,
                              LocalDateTime submittedAt, ExamSessionStatus status) {
        Transaction tx = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            ExamSession attempt = session.get(ExamSession.class, sessionId);
            if (attempt == null || attempt.getStatus() != ExamSessionStatus.IN_PROGRESS) {
                tx.rollback();
                return null;
            }
            session.createMutationQuery("DELETE FROM StudentAnswer WHERE sessionId=:id")
                    .setParameter("id", sessionId).executeUpdate();
            if (answers != null) {
                for (StudentAnswer answer : answers) {
                    StudentAnswer saved = new StudentAnswer(answer.getQuestionId(), answer.getSelectedAnswer());
                    saved.setSessionId(sessionId);
                    session.persist(saved);
                }
            }
            attempt.setSubmittedAt(submittedAt);
            attempt.setActualDurationMinutes((int)Math.max(0, java.time.Duration.between(attempt.getStartedAt(), submittedAt).toMinutes()));
            attempt.setStatus(status);
            session.merge(attempt);
            tx.commit();
            attempt.setAnswers(answers);
            return attempt;
        } catch (Exception e) {
            Transactions.rollbackQuietly(tx);
            System.err.println("[ExamSessionDAO] submit failed: " + e.getMessage());
            return null;
        }
    }


    /** Marks one overdue in-progress session as timed out without accepting new answers. */
    public ExamSession expireOverdueSession(int sessionId, LocalDateTime now) {
        Transaction tx = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            ExamSession attempt = session.get(ExamSession.class, sessionId);
            if (attempt != null
                    && attempt.getStatus() == ExamSessionStatus.IN_PROGRESS
                    && now.isAfter(attempt.getDeadline())) {
                attempt.setSubmittedAt(attempt.getDeadline());
                attempt.setActualDurationMinutes((int)Math.max(0, java.time.Duration.between(attempt.getStartedAt(), attempt.getDeadline()).toMinutes()));
                attempt.setStatus(ExamSessionStatus.TIMED_OUT);
                session.merge(attempt);
            }
            tx.commit();
            if (attempt != null) attempt.setAnswers(getAnswers(sessionId));
            return attempt;
        } catch (Exception e) {
            Transactions.rollbackQuietly(tx);
            throw new IllegalStateException("Could not expire exam session " + sessionId, e);
        }
    }

    /** Keeps persisted statuses synchronized with the authoritative server clock. */
    public int expireOverdueSessions(LocalDateTime now) {
        Transaction tx = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            int changed = session.createNativeMutationQuery(
                    "UPDATE ExamSessions SET status=\'TIMED_OUT\', submitted_at=deadline, " +
                    "actual_duration_minutes=TIMESTAMPDIFF(MINUTE,started_at,deadline) " +
                    "WHERE status=\'IN_PROGRESS\' AND deadline<:now")
                    .setParameter("now", now)
                    .executeUpdate();
            tx.commit();
            return changed;
        } catch (Exception e) {
            Transactions.rollbackQuietly(tx);
            throw new IllegalStateException("Could not expire overdue exam sessions", e);
        }
    }

    /** Extends all non-expired active attempts for a release. */
    public int extendActiveSessions(int releaseId, int extraMinutes, LocalDateTime now) {
        Transaction tx = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            List<ExamSession> active = session.createQuery(
                    "FROM ExamSession WHERE releaseId=:r AND status=:st AND deadline>:now",
                    ExamSession.class)
                    .setParameter("r", releaseId)
                    .setParameter("st", ExamSessionStatus.IN_PROGRESS)
                    .setParameter("now", now).list();
            for (ExamSession attempt : active) {
                attempt.setDeadline(attempt.getDeadline().plusMinutes(extraMinutes));
                attempt.setExtensionMinutes(attempt.getExtensionMinutes() + extraMinutes);
                session.merge(attempt);
            }
            tx.commit();
            return active.size();
        } catch (Exception e) {
            Transactions.rollbackQuietly(tx);
            System.err.println("[ExamSessionDAO] extendActiveSessions failed: " + e.getMessage());
            return -1;
        }
    }


    public boolean hasActiveSessionForCourse(int studentId, int courseId, LocalDateTime now) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Number count = (Number) session.createNativeQuery(
                    "SELECT COUNT(*) FROM ExamSessions es JOIN Exams e ON e.id=es.exam_id " +
                    "WHERE es.student_id=:student AND e.course_id=:course " +
                    "AND es.status='IN_PROGRESS' AND es.deadline>:now")
                    .setParameter("student", studentId)
                    .setParameter("course", courseId)
                    .setParameter("now", now)
                    .uniqueResult();
            return count != null && count.longValue() > 0;
        } catch (Exception e) {
            throw new IllegalStateException("Could not check active course exam sessions.", e);
        }
    }

    public boolean hasActiveSession(int studentId, LocalDateTime now) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Long count = session.createQuery(
                    "SELECT COUNT(s) FROM ExamSession s WHERE s.studentId=:student AND s.status=:st AND s.deadline>:now",
                    Long.class).setParameter("student", studentId)
                    .setParameter("st", ExamSessionStatus.IN_PROGRESS)
                    .setParameter("now", now).uniqueResult();
            return count != null && count > 0;
        } catch (Exception e) {
            System.err.println("[ExamSessionDAO] hasActiveSession failed: " + e.getMessage());
            return false;
        }
    }
}
