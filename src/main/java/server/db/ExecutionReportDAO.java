package server.db;

import common.network.ExamExecutionSummary;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Read models and persisted statistics for one administered exam sitting. */
public class ExecutionReportDAO {
    public ExamExecutionSummary summary(int releaseId) {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Object[] row = (Object[]) session.createNativeQuery(
                    "SELECT er.id, er.exam_id, e.title, er.open_time, er.close_time, "
                            + "COALESCE(MAX(TIMESTAMPDIFF(MINUTE, es.started_at, es.deadline)), e.duration_minutes), "
                            + "COUNT(es.id), "
                            + "SUM(CASE WHEN es.status='SUBMITTED' THEN 1 ELSE 0 END), "
                            + "SUM(CASE WHEN es.status='TIMED_OUT' THEN 1 ELSE 0 END) "
                            + "FROM ExamReleases er "
                            + "JOIN Exams e ON e.id=er.exam_id "
                            + "LEFT JOIN ExamSessions es ON es.release_id=er.id "
                            + "WHERE er.id=:releaseId "
                            + "GROUP BY er.id, er.exam_id, e.title, er.open_time, er.close_time, e.duration_minutes")
                    .setParameter("releaseId", releaseId)
                    .uniqueResult();
            if (row == null) return null;
            return new ExamExecutionSummary(
                    number(row[0]), number(row[1]), requiredText(row[2], "exam title"),
                    time(row[3]), time(row[4]), number(row[5]),
                    number(row[6]), number(row[7]), number(row[8]));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not load the exam execution summary.", exception);
        }
    }

    public void refreshStatistics(int releaseId) {
        Transaction transaction = null;
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            transaction = session.beginTransaction();
            List<?> raw = session.createNativeQuery(
                    "SELECT COALESCE(g.final_score,g.auto_score) "
                            + "FROM Grades g JOIN ExamSessions es ON es.id=g.session_id "
                            + "WHERE es.release_id=:releaseId "
                            + "AND g.status IN('APPROVED','OVERRIDDEN') ORDER BY 1")
                    .setParameter("releaseId", releaseId).getResultList();
            List<Integer> scores = new ArrayList<>();
            for (Object value : raw) scores.add(number(value));
            int[] bins = new int[10];
            for (int score : scores) bins[score == 100 ? 9 : Math.max(0, score) / 10]++;
            double mean = scores.stream().mapToInt(Integer::intValue).average().orElse(0);
            double median = percentile(scores, 0.5);
            String sql = "INSERT INTO ExamExecutionStatistics(" 
                    + "release_id,graded_count,mean_score,median_score,d0,d1,d2,d3,d4,d5,d6,d7,d8,d9,calculated_at) "
                    + "VALUES(:releaseId,:count,:mean,:median,:b0,:b1,:b2,:b3,:b4,:b5,:b6,:b7,:b8,:b9,NOW()) "
                    + "ON DUPLICATE KEY UPDATE graded_count=VALUES(graded_count),mean_score=VALUES(mean_score),"
                    + "median_score=VALUES(median_score),d0=VALUES(d0),d1=VALUES(d1),d2=VALUES(d2),"
                    + "d3=VALUES(d3),d4=VALUES(d4),d5=VALUES(d5),d6=VALUES(d6),d7=VALUES(d7),"
                    + "d8=VALUES(d8),d9=VALUES(d9),calculated_at=NOW()";
            var query = session.createNativeMutationQuery(sql)
                    .setParameter("releaseId", releaseId).setParameter("count", scores.size())
                    .setParameter("mean", mean).setParameter("median", median);
            for (int i = 0; i < bins.length; i++) query.setParameter("b" + i, bins[i]);
            query.executeUpdate();
            transaction.commit();
        } catch (Exception exception) {
            if (transaction != null && transaction.isActive()) transaction.rollback();
            throw new IllegalStateException("Could not refresh exam execution statistics.", exception);
        }
    }

    private static int number(Object value) {
        if (value == null) return 0;
        if (!(value instanceof Number)) throw new IllegalStateException("Expected a numeric database value.");
        return ((Number) value).intValue();
    }

    private static LocalDateTime time(Object value) {
        if (value instanceof Timestamp ts) return ts.toLocalDateTime();
        if (value instanceof LocalDateTime ldt) return ldt;
        throw new IllegalStateException("Expected a datetime value.");
    }

    private static String requiredText(Object value, String field) {
        if (value == null || value.toString().isBlank())
            throw new IllegalStateException("Missing " + field + " in the database.");
        return value.toString();
    }

    private static double percentile(List<Integer> values, double percentile) {
        if (values.isEmpty()) return 0;
        double index = (values.size() - 1) * percentile;
        int lower = (int) Math.floor(index), upper = (int) Math.ceil(index);
        return lower == upper ? values.get(lower)
                : values.get(lower) + (index - lower) * (values.get(upper) - values.get(lower));
    }
}
