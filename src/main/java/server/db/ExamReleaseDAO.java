package server.db;

import common.entities.ExamRelease;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.time.LocalDateTime;
import java.util.List;

/** Persistence operations for scheduled exam releases. */
public class ExamReleaseDAO {

    public ExamRelease create(ExamRelease release) {
        Transaction transaction = null;

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();
            session.persist(release);
            transaction.commit();
            return release;
        } catch (Exception exception) {
            if (transaction != null && transaction.isActive()) {
                transaction.rollback();
            }
            System.err.println("[ExamReleaseDAO] create failed: " + exception.getMessage());
            return null;
        }
    }

    public ExamRelease getById(int releaseId) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.get(ExamRelease.class, releaseId);
        } catch (Exception exception) {
            System.err.println("[ExamReleaseDAO] getById failed: " + exception.getMessage());
            return null;
        }
    }

    /** Returns the release currently open for this reusable execution code. */
    public ExamRelease getOpenByExecutionCode(String executionCode, LocalDateTime now) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                            "FROM ExamRelease WHERE UPPER(executionCode)=:code "
                                    + "AND openTime<=:now AND closeTime>=:now "
                                    + "ORDER BY openTime DESC",
                            ExamRelease.class)
                    .setParameter("code", executionCode.toUpperCase())
                    .setParameter("now", now)
                    .setMaxResults(1)
                    .uniqueResult();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not find the active exam release.", exception);
        }
    }

    /** Compatibility lookup. Prefer getOpenByExecutionCode for starting an exam. */
    public ExamRelease getByExecutionCode(String executionCode) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                            "FROM ExamRelease WHERE UPPER(executionCode)=:code ORDER BY openTime DESC",
                            ExamRelease.class)
                    .setParameter("code", executionCode.toUpperCase())
                    .setMaxResults(1)
                    .uniqueResult();
        } catch (Exception exception) {
            throw new IllegalStateException("Could not find the exam release.", exception);
        }
    }

    public boolean executionCodeConflicts(String executionCode, LocalDateTime openTime,
                                          LocalDateTime closeTime) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Long count = session.createQuery(
                    "SELECT COUNT(r.id) FROM ExamRelease r WHERE UPPER(r.executionCode)=:code " +
                    "AND r.openTime < :closeTime AND r.closeTime > :openTime", Long.class)
                    .setParameter("code", executionCode.toUpperCase())
                    .setParameter("openTime", openTime).setParameter("closeTime", closeTime)
                    .uniqueResult();
            return count != null && count > 0;
        } catch (Exception e) {
            throw new IllegalStateException("Could not validate execution code.", e);
        }
    }

    public void delete(int releaseId) {
        Transaction tx=null; try(Session session=HibernateUtil.getSessionFactory().openSession()) {
            tx=session.beginTransaction(); ExamRelease r=session.get(ExamRelease.class,releaseId);
            if(r!=null)session.remove(r); tx.commit();
        } catch(Exception e){if(tx!=null)tx.rollback(); System.err.println("[ExamReleaseDAO] delete failed: "+e.getMessage());}
    }

    public boolean executionCodeExists(String executionCode) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Long count = session.createQuery(
                            "SELECT COUNT(r.id) FROM ExamRelease r "
                                    + "WHERE r.executionCode = :code",
                            Long.class)
                    .setParameter("code", executionCode)
                    .uniqueResult();
            return count != null && count > 0;
        } catch (Exception exception) {
            System.err.println("[ExamReleaseDAO] executionCodeExists failed: "
                    + exception.getMessage());
            return false;
        }
    }

    public List<ExamRelease> getAll() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                            "FROM ExamRelease ORDER BY openTime DESC",
                            ExamRelease.class)
                    .list();
        } catch (Exception exception) {
            System.err.println("[ExamReleaseDAO] getAll failed: " + exception.getMessage());
            return List.of();
        }
    }

    public List<ExamRelease> getByTeacher(int teacherId) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                            "FROM ExamRelease WHERE releasedBy = :teacherId "
                                    + "ORDER BY openTime DESC",
                            ExamRelease.class)
                    .setParameter("teacherId", teacherId)
                    .list();
        } catch (Exception exception) {
            System.err.println("[ExamReleaseDAO] getByTeacher failed: "
                    + exception.getMessage());
            return List.of();
        }
    }
}
