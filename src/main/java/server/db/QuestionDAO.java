package server.db;

import common.entities.Question;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.util.List;

/**
 * Data Access Object for the {@code Questions} table (Data tier) — Hibernate
 * ORM implementation (Person 2, Phase 7; migrated from JDBC following the
 * {@code UserDAO} reference, public API unchanged so the server and all tests
 * were untouched by the persistence-technology swap — the point of the DAO
 * pattern, proven by the Phase 2 characterization suite staying green).
 *
 * <p>Implements the four bank operations from the assignment:
 * <ul>
 *   <li><b>view</b>  — {@link #getAllCurrent()} / {@link #getByCourse(int)} /
 *                      {@link #getByCourseFiltered(int, String, String)}</li>
 *   <li><b>add</b>   — {@link #add(Question)}</li>
 *   <li><b>edit</b>  — {@link #update(Question)} (versioned: the old version stays)</li>
 *   <li><b>delete</b>— {@link #delete(int)}</li>
 * </ul>
 *
 * <p>Every query uses named parameters — user input can never run as SQL.
 * Illustration bytes ({@code image_data}) are deliberately handled with
 * targeted <b>native</b> statements instead of the entity mapping: the column
 * is a LONGBLOB that must never ride along in list queries (NFR 18), exactly
 * like the password column is unmapped on {@code User}.
 *
 * <p>Read-only consumers (exam auto-build, study bot) should depend on the
 * {@link QuestionSource} interface this class implements, not on the DAO itself.
 */
public class QuestionDAO implements QuestionSource {

    // ===== READ ===========================================================

    /** The current question bank (latest version of every question). */
    public List<Question> getAllCurrent() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                            "FROM Question WHERE current = true ORDER BY baseId", Question.class)
                    .list();
        } catch (Exception e) {
            System.err.println("[QuestionDAO] getAllCurrent failed: " + e.getMessage());
            return List.of();
        }
    }

    /** Current questions for one course. */
    @Override
    public List<Question> getByCourse(int courseId) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                            "FROM Question WHERE current = true AND courseId = :course " +
                            "ORDER BY baseId", Question.class)
                    .setParameter("course", courseId)
                    .list();
        } catch (Exception e) {
            System.err.println("[QuestionDAO] getByCourse failed: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Current questions for one course narrowed by topic and/or difficulty
     * (the pool query for automatic exam building, scenario 3.4, and for
     * study-bot source material). Null/blank filter = no filter on that axis.
     */
    @Override
    public List<Question> getByCourseFiltered(int courseId, String topic, String difficulty) {
        StringBuilder hql = new StringBuilder(
                "FROM Question WHERE current = true AND courseId = :course");
        boolean byTopic = topic != null && !topic.trim().isEmpty();
        boolean byDifficulty = difficulty != null && !difficulty.trim().isEmpty();
        if (byTopic) hql.append(" AND topic = :topic");
        if (byDifficulty) hql.append(" AND difficulty = :difficulty");
        hql.append(" ORDER BY baseId");

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            var query = session.createQuery(hql.toString(), Question.class)
                    .setParameter("course", courseId);
            if (byTopic) query.setParameter("topic", topic.trim());
            if (byDifficulty) query.setParameter("difficulty", difficulty.trim());
            return query.list();
        } catch (Exception e) {
            System.err.println("[QuestionDAO] filtered query failed: " + e.getMessage());
            return List.of();
        }
    }

    /** All versions of one question (its history), oldest first. */
    @Override
    public List<Question> getHistory(int baseId) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return session.createQuery(
                            "FROM Question WHERE baseId = :base ORDER BY version", Question.class)
                    .setParameter("base", baseId)
                    .list();
        } catch (Exception e) {
            System.err.println("[QuestionDAO] getHistory failed: " + e.getMessage());
            return List.of();
        }
    }

    // ===== ADD ============================================================

    /**
     * Inserts a brand-new question as version 1 and sets its version-family id
     * ({@code base_id}) to its own generated id — one transaction, so a failure
     * leaves no partial row.
     *
     * @return the same question with its new {@code id} and {@code baseId} filled in.
     */
    public Question add(Question q) {
        // NOT try-with-resources: the rollback must run BEFORE the session
        // closes, otherwise the pooled connection is returned with the failed
        // transaction still open and a later operation implicitly commits the
        // half-done work (caught by QuestionDAOUpdateTest's rollback test).
        Session session = HibernateUtil.getSessionFactory().openSession();
        Transaction tx = null;
        try {
            tx = session.beginTransaction();

            q.setId(0);              // always a fresh row (IDENTITY-generated)
            q.setVersion(1);
            q.setCurrent(true);
            session.persist(q);
            session.flush();         // materialise the generated id

            q.setBaseId(q.getId());  // the first version's family id is its own id
            // (dirty-checking issues the UPDATE inside the same transaction)

            writeImage(session, q.getId(), q.getImageData());
            tx.commit();
            return q;
        } catch (Exception e) {
            System.err.println("[QuestionDAO] add failed: " + e.getMessage());
            rollback(tx);
            return null;
        } finally {
            session.close();
        }
    }

    // ===== EDIT (versioned) ==============================================

    /**
     * Edits a question WITHOUT overwriting the old version. In one transaction:
     * the current version is marked {@code is_current = FALSE} and a new row is
     * inserted (same {@code base_id}, {@code version + 1}, {@code is_current = TRUE}).
     * The previous version stays in the bank, as the assignment requires.
     *
     * <p>Illustration intent (see {@code Question#imageData}): path+bytes = new
     * image · path only = keep the previous version's image · no path = no image.
     *
     * @param q the edited question; must carry the {@code baseId} of the family.
     * @return the new current version (with its new id/version), or null on failure.
     */
    public Question update(Question q) {
        // Manual session lifecycle for the same rollback-before-close reason as add().
        Session session = HibernateUtil.getSessionFactory().openSession();
        Transaction tx = null;
        try {
            tx = session.beginTransaction();

            // 0) resolve the illustration BEFORE retiring the old version
            byte[] imageData = q.getImageData();
            if (imageData == null && q.getImagePath() != null) {
                imageData = readCurrentImage(session, q.getBaseId());
            }

            // 1) retire the current version
            session.createMutationQuery(
                            "UPDATE Question SET current = false WHERE baseId = :base AND current = true")
                    .setParameter("base", q.getBaseId())
                    .executeUpdate();

            // 2) find the next version number
            Integer maxVersion = session.createQuery(
                            "SELECT MAX(version) FROM Question WHERE baseId = :base", Integer.class)
                    .setParameter("base", q.getBaseId())
                    .uniqueResult();
            int nextVersion = (maxVersion == null ? 0 : maxVersion) + 1;

            // 3) insert the new current version
            q.setId(0);
            q.setVersion(nextVersion);
            q.setCurrent(true);
            session.persist(q);
            session.flush();

            writeImage(session, q.getId(), imageData);
            tx.commit();
            return q;
        } catch (Exception e) {
            System.err.println("[QuestionDAO] update failed: " + e.getMessage());
            rollback(tx);
            return null;
        } finally {
            session.close();
        }
    }

    // ===== DELETE =========================================================

    /**
     * Deletes a question and all of its versions (the whole family).
     *
     * @return true if at least one row was removed.
     */
    public boolean delete(int baseId) {
        // Manual session lifecycle for the same rollback-before-close reason as add().
        Session session = HibernateUtil.getSessionFactory().openSession();
        Transaction tx = null;
        try {
            tx = session.beginTransaction();
            int removed = session.createMutationQuery(
                            "DELETE FROM Question WHERE baseId = :base")
                    .setParameter("base", baseId)
                    .executeUpdate();
            tx.commit();
            return removed > 0;
        } catch (Exception e) {
            System.err.println("[QuestionDAO] delete failed: " + e.getMessage());
            rollback(tx);
            return false;
        } finally {
            session.close();
        }
    }

    // ===== illustration (lazy, native SQL — image_data is unmapped) =======

    /**
     * The illustration bytes of ONE question row (any version), or null if it
     * has no image. Deliberately separate from the list queries so bank
     * responses never haul BLOBs across the wire (NFR 18); clients call this
     * only when they actually display the image.
     */
    @Override
    public byte[] getImage(int questionId) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            return (byte[]) session.createNativeQuery(
                            "SELECT image_data FROM Questions WHERE id = :id", byte[].class)
                    .setParameter("id", questionId)
                    .uniqueResult();
        } catch (Exception e) {
            System.err.println("[QuestionDAO] getImage failed: " + e.getMessage());
            return null;
        }
    }

    /** Stores the bytes for one row inside the caller's transaction (no-op for null). */
    private void writeImage(Session session, int questionId, byte[] bytes) {
        if (bytes == null) return;
        session.createNativeMutationQuery(
                        "UPDATE Questions SET image_data = :data WHERE id = :id")
                .setParameter("data", bytes)
                .setParameter("id", questionId)
                .executeUpdate();
    }

    /** The current version's image bytes for a family (used by the keep-image rule). */
    private byte[] readCurrentImage(Session session, int baseId) {
        return (byte[]) session.createNativeQuery(
                        "SELECT image_data FROM Questions WHERE base_id = :base AND is_current = TRUE",
                        byte[].class)
                .setParameter("base", baseId)
                .uniqueResult();
    }

    // ===== helpers ========================================================

    private static void rollback(Transaction tx) {
        if (tx != null) {
            try { tx.rollback(); } catch (Exception ignored) { }
        }
    }
}
