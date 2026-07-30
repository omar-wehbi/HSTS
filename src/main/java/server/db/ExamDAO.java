package server.db;

import common.entities.Exam;
import common.entities.ExamQuestion;
import common.entities.ExamStatus;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data Access Object for exams stored in the exam drawer.
 *
 * <p>Supports:</p>
 * <ul>
 *     <li>creating a new exam;</li>
 *     <li>creating a new version while retaining the old version;</li>
 *     <li>reading exams and their questions;</li>
 *     <li>submitting an exam for coordinator approval;</li>
 *     <li>approving or rejecting an exam.</li>
 * </ul>
 *
 * <p>The exam question list is transient in the Exam entity.
 * Therefore, exam questions are stored and loaded separately through
 * the {@code ExamQuestions} table.</p>
 */
public class ExamDAO {

    // ===== READ ===========================================================

    /**
     * Returns one exam version by its database ID.
     *
     * @param examId exact exam-version ID
     * @return exam with its questions, or null when it does not exist
     */
    public Exam getById(int examId) {
        try (Session session =
                     HibernateUtil.getSessionFactory().openSession()) {

            Exam exam = session.get(Exam.class, examId);

            if (exam == null) {
                return null;
            }

            loadQuestions(session, exam);
            loadCourseNames(session, List.of(exam));
            return exam;

        } catch (Exception e) {
            System.err.println(
                    "[ExamDAO] getById failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Returns the current version of every exam.
     */
    public List<Exam> getAllCurrent() {
        try (Session session =
                     HibernateUtil.getSessionFactory().openSession()) {

            List<Exam> exams = session.createQuery(
                            "FROM Exam " +
                                    "WHERE current = true " +
                                    "ORDER BY baseId",
                            Exam.class
                    )
                    .list();

            loadQuestions(session, exams);
            loadCourseNames(session, exams);
            return exams;

        } catch (Exception e) {
            System.err.println(
                    "[ExamDAO] getAllCurrent failed: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Returns current exams authored by one teacher.
     */
    public List<Exam> getCurrentByTeacher(int teacherId) {
        try (Session session =
                     HibernateUtil.getSessionFactory().openSession()) {

            List<Exam> exams = session.createQuery(
                            "FROM Exam " +
                                    "WHERE current = true " +
                                    "AND teacherId = :teacher " +
                                    "ORDER BY baseId",
                            Exam.class
                    )
                    .setParameter("teacher", teacherId)
                    .list();

            loadQuestions(session, exams);
            loadCourseNames(session, exams);
            return exams;

        } catch (Exception e) {
            System.err.println(
                    "[ExamDAO] getCurrentByTeacher failed: "
                            + e.getMessage());
            return List.of();
        }
    }

    /**
     * Returns all current exams waiting for coordinator approval.
     */
    public List<Exam> getPendingApproval() {
        try (Session session =
                     HibernateUtil.getSessionFactory().openSession()) {

            List<Exam> exams = session.createQuery(
                            "FROM Exam " +
                                    "WHERE current = true " +
                                    "AND status = :status " +
                                    "ORDER BY id",
                            Exam.class
                    )
                    .setParameter(
                            "status",
                            ExamStatus.PENDING_APPROVAL
                    )
                    .list();

            loadQuestions(session, exams);
            return exams;

        } catch (Exception e) {
            System.err.println(
                    "[ExamDAO] getPendingApproval failed: "
                            + e.getMessage());
            return List.of();
        }
    }

    /**
     * Returns every stored version of an exam family, oldest first.
     */
    public List<Exam> getHistory(int baseId) {
        try (Session session =
                     HibernateUtil.getSessionFactory().openSession()) {

            List<Exam> exams = session.createQuery(
                            "FROM Exam " +
                                    "WHERE baseId = :base " +
                                    "ORDER BY version",
                            Exam.class
                    )
                    .setParameter("base", baseId)
                    .list();

            loadQuestions(session, exams);
            return exams;

        } catch (Exception e) {
            System.err.println(
                    "[ExamDAO] getHistory failed: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Deletes an exam and all its questions. Only drafts/rejected exams may be deleted.
     * Returns true on success.
     */
    public boolean delete(int examId) {
        Transaction tx = null;
        Session session = HibernateUtil.getSessionFactory().openSession();
        try {
            tx = session.beginTransaction();
            session.createQuery("DELETE FROM ExamQuestion WHERE examId = :eid")
                    .setParameter("eid", examId)
                    .executeUpdate();
            int deleted = session.createQuery("DELETE FROM Exam WHERE id = :eid")
                    .setParameter("eid", examId)
                    .executeUpdate();
            tx.commit();
            return deleted > 0;
        } catch (Exception e) {
            System.err.println("[ExamDAO] delete failed: " + e.getMessage());
            rollback(tx);
            return false;
        } finally {
            session.close();
        }
    }

    // ===== CREATE =========================================================

    /**
     * Stores a brand-new exam as version 1.
     *
     * <p>The generated exam ID also becomes the exam-family
     * {@code baseId}. Its questions are inserted in the same transaction.</p>
     *
     * @return saved exam, or null on failure
     */
    public Exam create(Exam exam) {
        Session session =
                HibernateUtil.getSessionFactory().openSession();

        Transaction tx = null;

        try {
            tx = session.beginTransaction();

            exam.setId(0);
            exam.setBaseId(0);
            exam.setVersion(1);
            exam.setCurrent(true);

            if (exam.getStatus() == null) {
                exam.setStatus(ExamStatus.DRAFT);
            }

            List<ExamQuestion> questions =
                    copyQuestions(exam.getQuestions());

            /*
             * Questions are transient in Exam, so they do not affect
             * persistence of the exam row.
             */
            exam.setQuestions(new ArrayList<>());

            session.persist(exam);
            session.flush();

            /*
             * The first version's family ID is its own generated ID.
             */
            exam.setBaseId(exam.getId());

            persistQuestions(
                    session,
                    exam.getId(),
                    questions
            );

            tx.commit();

            exam.setQuestions(questions);
            setExamIdOnQuestions(exam);

            return exam;

        } catch (Exception e) {
            System.err.println(
                    "[ExamDAO] create failed: " + e.getMessage());
            rollback(tx);
            return null;

        } finally {
            session.close();
        }
    }

    // ===== UPDATE — VERSIONED ============================================

    /**
     * Creates a new version of an existing exam.
     *
     * <p>In one transaction:</p>
     * <ol>
     *     <li>the old current version is marked non-current;</li>
     *     <li>a new exam row is inserted;</li>
     *     <li>the new version's question rows are inserted;</li>
     *     <li>the previous version remains stored unchanged.</li>
     * </ol>
     *
     * @param currentExamId ID of the version currently being edited
     * @param newVersion edited exam data
     * @return newly stored current version, or null on failure
     */
    public Exam createNewVersion(int currentExamId,
                                 Exam newVersion) {

        Session session =
                HibernateUtil.getSessionFactory().openSession();

        Transaction tx = null;

        try {
            tx = session.beginTransaction();

            Exam current = session.get(
                    Exam.class,
                    currentExamId
            );

            if (current == null || !current.isCurrent()) {
                rollback(tx);
                return null;
            }

            int familyBaseId =
                    current.getBaseId() > 0
                            ? current.getBaseId()
                            : current.getId();

            Integer maximumVersion = session.createQuery(
                            "SELECT MAX(version) " +
                                    "FROM Exam " +
                                    "WHERE baseId = :base",
                            Integer.class
                    )
                    .setParameter("base", familyBaseId)
                    .uniqueResult();

            int nextVersion =
                    (maximumVersion == null
                            ? current.getVersion()
                            : maximumVersion) + 1;

            /*
             * Retire the previous current version.
             */
            session.createMutationQuery(
                            "UPDATE Exam " +
                                    "SET current = false " +
                                    "WHERE baseId = :base " +
                                    "AND current = true"
                    )
                    .setParameter("base", familyBaseId)
                    .executeUpdate();

            List<ExamQuestion> questions =
                    copyQuestions(newVersion.getQuestions());

            newVersion.setId(0);
            newVersion.setBaseId(familyBaseId);
            newVersion.setVersion(nextVersion);
            newVersion.setCurrent(true);
            newVersion.setStatus(ExamStatus.DRAFT);
            newVersion.setCoordinatorId(null);
            newVersion.setRejectionReason(null);
            newVersion.setQuestions(new ArrayList<>());

            session.persist(newVersion);
            session.flush();

            persistQuestions(
                    session,
                    newVersion.getId(),
                    questions
            );

            tx.commit();

            newVersion.setQuestions(questions);
            setExamIdOnQuestions(newVersion);

            return newVersion;

        } catch (Exception e) {
            System.err.println(
                    "[ExamDAO] createNewVersion failed: "
                            + e.getMessage());
            rollback(tx);
            return null;

        } finally {
            session.close();
        }
    }

    // ===== APPROVAL WORKFLOW =============================================

    /**
     * Changes a draft or rejected exam to PENDING_APPROVAL.
     */
    public Exam submitForApproval(int examId) {
        Session session =
                HibernateUtil.getSessionFactory().openSession();

        Transaction tx = null;

        try {
            tx = session.beginTransaction();

            Exam exam = session.get(Exam.class, examId);

            if (exam == null || !exam.isCurrent()) {
                rollback(tx);
                return null;
            }

            if (exam.getStatus() != ExamStatus.DRAFT
                    && exam.getStatus() != ExamStatus.REJECTED) {

                rollback(tx);
                return null;
            }

            exam.setStatus(ExamStatus.PENDING_APPROVAL);
            exam.setCoordinatorId(null);
            exam.setRejectionReason(null);

            tx.commit();

            loadQuestionsAfterTransaction(session, exam);
            return exam;

        } catch (Exception e) {
            System.err.println(
                    "[ExamDAO] submitForApproval failed: "
                            + e.getMessage());
            rollback(tx);
            return null;

        } finally {
            session.close();
        }
    }

    /**
     * Approves an exam that is waiting for approval.
     */
    public Exam approve(int examId,
                        int coordinatorId) {

        Session session =
                HibernateUtil.getSessionFactory().openSession();

        Transaction tx = null;

        try {
            tx = session.beginTransaction();

            Exam exam = session.get(Exam.class, examId);

            if (exam == null || !exam.isCurrent()) {
                rollback(tx);
                return null;
            }

            if (exam.getStatus()
                    != ExamStatus.PENDING_APPROVAL) {

                rollback(tx);
                return null;
            }

            exam.setStatus(ExamStatus.APPROVED);
            exam.setCoordinatorId(coordinatorId);
            exam.setRejectionReason(null);

            tx.commit();

            loadQuestionsAfterTransaction(session, exam);
            return exam;

        } catch (Exception e) {
            System.err.println(
                    "[ExamDAO] approve failed: " + e.getMessage());
            rollback(tx);
            return null;

        } finally {
            session.close();
        }
    }

    /**
     * Rejects an exam and stores the coordinator's written reason.
     */
    public Exam reject(int examId,
                       int coordinatorId,
                       String reason) {

        Session session =
                HibernateUtil.getSessionFactory().openSession();

        Transaction tx = null;

        try {
            tx = session.beginTransaction();

            Exam exam = session.get(Exam.class, examId);

            if (exam == null || !exam.isCurrent()) {
                rollback(tx);
                return null;
            }

            if (exam.getStatus()
                    != ExamStatus.PENDING_APPROVAL) {

                rollback(tx);
                return null;
            }

            exam.setStatus(ExamStatus.REJECTED);
            exam.setCoordinatorId(coordinatorId);
            exam.setRejectionReason(reason);

            tx.commit();

            loadQuestionsAfterTransaction(session, exam);
            return exam;

        } catch (Exception e) {
            System.err.println(
                    "[ExamDAO] reject failed: " + e.getMessage());
            rollback(tx);
            return null;

        } finally {
            session.close();
        }
    }

    // ===== INTERNAL QUESTION OPERATIONS ==================================

    /**
     * Loads the questions belonging to one exam.
     */
    private void loadQuestions(Session session,
                               Exam exam) {

        List<ExamQuestion> questions =
                session.createQuery(
                                "FROM ExamQuestion " +
                                        "WHERE examId = :exam " +
                                        "ORDER BY position",
                                ExamQuestion.class
                        )
                        .setParameter("exam", exam.getId())
                        .list();

        exam.setQuestions(questions);
    }

    /**
     * Loads questions for several exams while the session is open.
     */
    private void loadQuestions(Session session,
                               List<Exam> exams) {

        for (Exam exam : exams) {
            loadQuestions(session, exam);
        }
    }

    @SuppressWarnings("unchecked")
    private void loadCourseNames(Session session, List<Exam> exams) {
        if (exams.isEmpty()) return;
        List<Object[]> rows = session.createNativeQuery(
                "SELECT id, name FROM Courses", Object[].class).list();
        Map<Integer, String> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((Integer) row[0], (String) row[1]);
        }
        for (Exam exam : exams) {
            exam.setCourseName(map.getOrDefault(exam.getCourseId(), "Course #" + exam.getCourseId()));
        }
    }

    /**
     * Loads question rows after a workflow transaction commits.
     */
    private void loadQuestionsAfterTransaction(Session session,
                                               Exam exam) {

        loadQuestions(session, exam);
    }

    /**
     * Persists all questions of one exam.
     */
    private void persistQuestions(Session session,
                                  int examId,
                                  List<ExamQuestion> questions) {

        for (ExamQuestion examQuestion : questions) {
            if (examQuestion == null) {
                continue;
            }

            examQuestion.setId(0);
            examQuestion.setExamId(examId);

            session.persist(examQuestion);
        }

        session.flush();
    }

    /**
     * Creates a defensive copy so changing IDs during persistence
     * does not replace the caller's list object.
     */
    private List<ExamQuestion> copyQuestions(
            List<ExamQuestion> source) {

        List<ExamQuestion> copy = new ArrayList<>();

        if (source == null) {
            return copy;
        }

        for (ExamQuestion original : source) {
            if (original == null) {
                continue;
            }

            ExamQuestion cloned = new ExamQuestion(
                    original.getQuestionId(),
                    original.getPoints(),
                    original.getPosition()
            );

            copy.add(cloned);
        }

        return copy;
    }

    /**
     * Ensures every returned ExamQuestion carries its parent exam ID.
     */
    private void setExamIdOnQuestions(Exam exam) {
        for (ExamQuestion question : exam.getQuestions()) {
            if (question != null) {
                question.setExamId(exam.getId());
            }
        }
    }

    // ===== HELPERS ========================================================

    private static void rollback(Transaction tx) {
        if (tx != null) {
            try {
                tx.rollback();
            } catch (Exception ignored) {
                // Preserve the original database failure.
            }
        }
    }
}
