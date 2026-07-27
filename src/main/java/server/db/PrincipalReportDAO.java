package server.db;

import common.network.PrincipalData;
import common.network.PrincipalDataItem;
import org.hibernate.Session;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Read-only persistence queries for scenarios 11 and 12. */
public class PrincipalReportDAO {
    private static final Logger LOGGER = Logger.getLogger(PrincipalReportDAO.class.getName());

    public PrincipalData getCatalog() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            List<PrincipalDataItem> teachers = items(session,
                    "SELECT id, display_name FROM Users WHERE role='TEACHER' ORDER BY display_name");
            List<PrincipalDataItem> courses = items(session,
                    "SELECT id, name FROM Courses ORDER BY name");
            List<PrincipalDataItem> students = items(session,
                    "SELECT id, display_name FROM Users WHERE role='STUDENT' ORDER BY display_name");
            Number count = (Number) session.createNativeQuery(
                    "SELECT COUNT(*) FROM Grades WHERE status IN ('APPROVED','OVERRIDDEN')")
                    .getSingleResult();
            return new PrincipalData(teachers, courses, students, count == null ? 0 : count.intValue());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to load principal catalog.", e);
            throw new IllegalStateException("Could not load principal data.", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<PrincipalDataItem> items(Session session, String sql) {
        List<Object[]> rows = session.createNativeQuery(sql).getResultList();
        List<PrincipalDataItem> result = new ArrayList<>();
        for (Object[] row : rows) {
            int id = requiredNumber(row, 0, "catalog ID").intValue();
            String name = requiredText(row, 1, "catalog name", id);
            result.add(new PrincipalDataItem(id, name));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public List<PrincipalGradeRecord> getVisibleGradeRecords() {
        String sql = "SELECT e.teacher_id,t.display_name,e.course_id,c.name,g.student_id,s.display_name," +
                "e.id,es.release_id,e.title,COALESCE(g.final_score,g.auto_score) FROM Grades g " +
                "JOIN ExamSessions es ON es.id=g.session_id JOIN Exams e ON e.id=g.exam_id " +
                "JOIN Users t ON t.id=e.teacher_id JOIN Courses c ON c.id=e.course_id " +
                "JOIN Users s ON s.id=g.student_id WHERE g.status IN('APPROVED','OVERRIDDEN') " +
                "ORDER BY e.teacher_id,e.course_id,g.student_id,es.release_id,g.id";
        try(Session session=HibernateUtil.getSessionFactory().openSession()){
            List<Object[]> rows=session.createNativeQuery(sql).getResultList(); List<PrincipalGradeRecord> result=new ArrayList<>();
            for(Object[] r:rows)result.add(new PrincipalGradeRecord(((Number)r[0]).intValue(),r[1].toString(),((Number)r[2]).intValue(),r[3].toString(),((Number)r[4]).intValue(),r[5].toString(),((Number)r[6]).intValue(),((Number)r[7]).intValue(),r[8].toString(),((Number)r[9]).intValue()));
            return result;
        }catch(Exception e){LOGGER.log(Level.SEVERE,"Failed to load visible grades for principal reports.",e);throw new IllegalStateException("Could not load principal report grades.",e);}
    }

    private static Number requiredNumber(Object[] row, int index, String field) {
        if (row == null || index >= row.length || !(row[index] instanceof Number)) {
            throw new IllegalStateException("Invalid or missing " + field + " in principal report query result.");
        }
        return (Number) row[index];
    }

    private static String requiredText(Object[] row, int index, String field, int id) {
        if (row == null || index >= row.length || row[index] == null) {
            throw new IllegalStateException("Missing " + field + " for entity " + id + ".");
        }
        String value = row[index].toString().trim();
        if (value.isEmpty()) throw new IllegalStateException("Empty " + field + " for entity " + id + ".");
        return value;
    }
}
