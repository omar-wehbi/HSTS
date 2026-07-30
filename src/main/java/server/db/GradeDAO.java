package server.db;

import common.entities.Grade;
import common.entities.GradeStatus;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.time.LocalDateTime;

/** Persistence for computerized grades and teacher decisions. */
public class GradeDAO {

    public Grade create(Grade grade) {
        Transaction tx = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            session.persist(grade);
            tx.commit();
            return grade;
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            System.err.println("[GradeDAO] create failed: " + e.getMessage());
            return null;
        }
    }

    public Grade getById(int id) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.get(Grade.class, id);
        } catch (Exception e) {
            System.err.println("[GradeDAO] getById failed: " + e.getMessage());
            return null;
        }
    }

    public Grade getBySessionId(int sessionId) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "FROM Grade WHERE sessionId=:sessionId", Grade.class)
                    .setParameter("sessionId", sessionId)
                    .uniqueResult();
        } catch (Exception e) {
            System.err.println("[GradeDAO] getBySessionId failed: " + e.getMessage());
            return null;
        }
    }

    public Grade approve(int gradeId, int teacherId, LocalDateTime approvedAt) { return approve(gradeId, teacherId, approvedAt, null); }

    public Grade approve(int gradeId, int teacherId, LocalDateTime approvedAt, String teacherComment) {
        Transaction tx = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            Grade grade = session.get(Grade.class, gradeId);
            if (grade == null || grade.getStatus() != GradeStatus.AUTO_GRADED) {
                tx.rollback();
                return null;
            }
            grade.setFinalScore(grade.getAutoScore());
            grade.setStatus(GradeStatus.APPROVED);
            grade.setApprovedBy(teacherId);
            grade.setApprovedAt(approvedAt);
            grade.setTeacherComment(teacherComment);
            session.merge(grade);
            tx.commit();
            return grade;
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            System.err.println("[GradeDAO] approve failed: " + e.getMessage());
            return null;
        }
    }

    public Grade override(int gradeId, int newScore, String justification,
                          int teacherId, LocalDateTime approvedAt) {
        Transaction tx = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            tx = session.beginTransaction();
            Grade grade = session.get(Grade.class, gradeId);
            if (grade == null) {
                tx.rollback();
                return null;
            }
            grade.setFinalScore(newScore);
            grade.setStatus(GradeStatus.OVERRIDDEN);
            grade.setOverrideJustification(justification);
            grade.setApprovedBy(teacherId);
            grade.setApprovedAt(approvedAt);
            session.merge(grade);
            tx.commit();
            return grade;
        } catch (Exception e) {
            if (tx != null) tx.rollback();
            System.err.println("[GradeDAO] override failed: " + e.getMessage());
            return null;
        }
    }

    /** Approved/overridden grades visible to one student, newest first. */
    public java.util.List<Grade> getVisibleByStudent(int studentId) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "FROM Grade WHERE studentId=:studentId " +
                    "AND status IN (:approved, :overridden) " +
                    "ORDER BY approvedAt DESC, id DESC", Grade.class)
                    .setParameter("studentId", studentId)
                    .setParameter("approved", GradeStatus.APPROVED)
                    .setParameter("overridden", GradeStatus.OVERRIDDEN)
                    .list();
        } catch (Exception e) {
            System.err.println("[GradeDAO] getVisibleByStudent failed: " + e.getMessage());
            return java.util.List.of();
        }
    }

    public java.util.List<Grade> getVisibleByRelease(int releaseId) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery("SELECT g FROM Grade g, ExamSession es WHERE g.sessionId=es.id AND es.releaseId=:r AND g.status IN(:a,:o) ORDER BY g.studentId", Grade.class)
                    .setParameter("r",releaseId).setParameter("a",GradeStatus.APPROVED).setParameter("o",GradeStatus.OVERRIDDEN).list();
        } catch(Exception e){System.err.println("[GradeDAO] getVisibleByRelease failed: "+e.getMessage());return java.util.List.of();}
    }

    /** All grades for sessions of one release (including AUTO_GRADED), for the teacher grading UI. */
    public java.util.List<Grade> getByRelease(int releaseId) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                            "SELECT g FROM Grade g, ExamSession es "
                                    + "WHERE g.sessionId = es.id AND es.releaseId = :r "
                                    + "ORDER BY g.studentId, g.id",
                            Grade.class)
                    .setParameter("r", releaseId)
                    .list();
        } catch (Exception e) {
            System.err.println("[GradeDAO] getByRelease failed: " + e.getMessage());
            return java.util.List.of();
        }
    }

    /** Every computerized grade for one exam, used by its author. */
    public java.util.List<Grade> getByExamId(int examId) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                    "FROM Grade WHERE examId=:examId ORDER BY studentId, id", Grade.class)
                    .setParameter("examId", examId)
                    .list();
        } catch (Exception e) {
            System.err.println("[GradeDAO] getByExamId failed: " + e.getMessage());
            return java.util.List.of();
        }
    }

}
