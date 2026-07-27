package server;

import common.entities.Role;
import common.entities.User;
import common.network.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import server.db.PrincipalGradeRecord;
import server.db.PrincipalReadOnlyDAO;
import server.db.PrincipalReportDAO;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PrincipalReportServiceTest {
    @Mock PrincipalReportDAO dao;
    @Mock PrincipalReadOnlyDAO readOnlyDao;
    private PrincipalReportService service;
    private User principal;

    @BeforeEach
    void setUp() {
        // Two-arg constructor so getReadOnlyData() never touches a real Hibernate DAO in a unit test.
        service = new PrincipalReportService(dao, readOnlyDao);
        principal = new User(1, "principal", Role.PRINCIPAL, "Principal", null);
    }

    @Test
    void principalCatalogIsReadOnlyDto() {
        PrincipalData data = catalog();
        when(dao.getCatalog()).thenReturn(data);

        Message response = service.getPrincipalData(principal);
        assertEquals(Message.Command.SUCCESS, response.getCommand());
        assertSame(data, response.getPayload());
        verify(dao).getCatalog();
        verifyNoMoreInteractions(dao);
    }

    @Test
    void nonPrincipalIsRejected() {
        User teacher = new User(2, "teacher", Role.TEACHER, "Teacher", null);
        assertThrows(AuthorizationException.class, () -> service.getPrincipalData(teacher));
        assertThrows(AuthorizationException.class, () -> service.getReport(teacher,
                new PrincipalReportRequest(ReportDimension.COURSE, List.of())));
        verifyNoInteractions(dao);
    }

    @Test
    void courseReportComputesMeanMedianAndNineDeciles() {
        when(dao.getCatalog()).thenReturn(catalog());
        when(dao.getVisibleGradeRecords()).thenReturn(List.of(
                row(10, "T1", 100, "Math", 1, "S1", 50),
                row(10, "T1", 100, "Math", 2, "S2", 70),
                row(11, "T2", 100, "Math", 3, "S3", 90),
                row(10, "T1", 200, "English", 1, "S1", 80)));

        Message response = service.getReport(principal,
                new PrincipalReportRequest(ReportDimension.COURSE, List.of()));
        assertEquals(Message.Command.SUCCESS, response.getCommand());
        PrincipalReport report = (PrincipalReport) response.getPayload();
        assertEquals(2, report.getGroups().size());
        ReportGroup math = report.getGroups().stream().filter(g -> g.getEntityId() == 100).findFirst().orElseThrow();
        assertEquals(3, math.getCount());
        assertEquals(70.0, math.getMean(), 0.001);
        assertEquals(70.0, math.getMedian(), 0.001);
        assertEquals(50, math.getMinimum());
        assertEquals(90, math.getMaximum());
        assertEquals(9, math.getDeciles().size());
        assertEquals(54.0, math.getDeciles().get(0), 0.001);
        assertEquals(86.0, math.getDeciles().get(8), 0.001);
    }

    @Test
    void selectedIdsRestrictComparison() {
        when(dao.getCatalog()).thenReturn(catalog());
        when(dao.getVisibleGradeRecords()).thenReturn(List.of(
                row(10, "T1", 100, "Math", 1, "S1", 50),
                row(11, "T2", 100, "Math", 2, "S2", 90)));
        PrincipalReport report = (PrincipalReport) service.getReport(principal,
                new PrincipalReportRequest(ReportDimension.TEACHER, List.of(11))).getPayload();
        assertEquals(1, report.getGroups().size());
        assertEquals(11, report.getGroups().get(0).getEntityId());
    }

    @Test
    void invalidSelectedIdReturnsClearError() {
        when(dao.getCatalog()).thenReturn(catalog());
        when(dao.getVisibleGradeRecords()).thenReturn(List.of());

        Message response = service.getReport(principal,
                new PrincipalReportRequest(ReportDimension.COURSE, List.of(999)));

        assertEquals(Message.Command.ERROR, response.getCommand());
        assertEquals("Course 999 does not exist.", response.getPayload());
    }

    @Test
    void selectedEntityWithNoGradesStillAppears() {
        when(dao.getCatalog()).thenReturn(catalog());
        when(dao.getVisibleGradeRecords()).thenReturn(List.of());

        Message response = service.getReport(principal,
                new PrincipalReportRequest(ReportDimension.TEACHER, List.of(11)));

        assertEquals(Message.Command.SUCCESS, response.getCommand());
        ReportGroup group = ((PrincipalReport) response.getPayload()).getGroups().get(0);
        assertEquals(11, group.getEntityId());
        assertEquals(0, group.getCount());
        assertEquals(0.0, group.getMean());
        assertEquals(0.0, group.getMedian());
        assertNull(group.getMinimum());
        assertNull(group.getMaximum());
        assertEquals(9, group.getDeciles().size());
    }

    @Test
    void allCatalogEntitiesAppearEvenWithoutGrades() {
        when(dao.getCatalog()).thenReturn(catalog());
        when(dao.getVisibleGradeRecords()).thenReturn(List.of());

        PrincipalReport report = (PrincipalReport) service.getReport(principal,
                new PrincipalReportRequest(ReportDimension.STUDENT, List.of())).getPayload();

        assertEquals(3, report.getGroups().size());
        assertTrue(report.getGroups().stream().allMatch(g -> g.getCount() == 0));
    }

    @Test
    void invalidPayloadReturnsProtocolError() {
        Message response = service.getReport(principal, Integer.valueOf(5));
        assertEquals(Message.Command.ERROR, response.getCommand());
        verifyNoInteractions(dao);
    }

    @Test
    void databaseErrorIsReturnedWithoutLeakingCause() {
        when(dao.getCatalog()).thenThrow(new IllegalStateException("Could not load principal data."));
        Message response = service.getReport(principal,
                new PrincipalReportRequest(ReportDimension.COURSE, List.of()));
        assertEquals(Message.Command.ERROR, response.getCommand());
        assertEquals("Could not load principal data.", response.getPayload());
    }

    private static PrincipalData catalog() {
        return new PrincipalData(
                List.of(new PrincipalDataItem(10, "T1"), new PrincipalDataItem(11, "T2")),
                List.of(new PrincipalDataItem(100, "Math"), new PrincipalDataItem(200, "English")),
                List.of(new PrincipalDataItem(1, "S1"), new PrincipalDataItem(2, "S2"),
                        new PrincipalDataItem(3, "S3")),
                4);
    }

    private static PrincipalGradeRecord row(int teacherId, String teacherName, int courseId,
                                            String courseName, int studentId, String studentName, int score) {
        return new PrincipalGradeRecord(teacherId, teacherName, courseId, courseName,
                studentId, studentName, score);
    }

    private static PrincipalGradeRecord examRow(int teacherId, int examId, int releaseId,
                                                 String examTitle, int studentId, int score) {
        return new PrincipalGradeRecord(teacherId, "Teacher " + teacherId, 0, "",
                studentId, "Student " + studentId, examId, releaseId, examTitle, score);
    }

    // ----- getExamComparisonReport() --------------------------------------

    @Test
    void nonPrincipalCannotRequestExamComparisonReport() {
        User teacher = new User(2, "teacher", Role.TEACHER, "Teacher", null);
        assertThrows(AuthorizationException.class, () -> service.getExamComparisonReport(teacher,
                new ExamComparisonReportRequest(ReportDimension.TEACHER, 10)));
        verifyNoInteractions(dao);
    }

    @Test
    void examComparisonRejectsMissingFilter() {
        Message response = service.getExamComparisonReport(principal,
                new ExamComparisonReportRequest(null, 0));
        assertEquals(Message.Command.ERROR, response.getCommand());
        verifyNoInteractions(dao);
    }

    @Test
    void examComparisonRejectsWrongPayloadType() {
        Message response = service.getExamComparisonReport(principal, Integer.valueOf(5));
        assertEquals(Message.Command.ERROR, response.getCommand());
        verifyNoInteractions(dao);
    }

    @Test
    void examComparisonGroupsByExecutionForTheFilteredTeacherOnly() {
        when(dao.getVisibleGradeRecords()).thenReturn(List.of(
                examRow(10, 1, 100, "Midterm", 1, 60),
                examRow(10, 1, 100, "Midterm", 2, 80),   // same sitting as above -> merged
                examRow(10, 2, 200, "Final", 1, 90),      // different sitting -> separate group
                examRow(11, 3, 300, "Other", 1, 50)));    // different teacher -> excluded

        ExamComparisonReportRequest request = new ExamComparisonReportRequest(ReportDimension.TEACHER, 10);
        Message response = service.getExamComparisonReport(principal, request);

        assertEquals(Message.Command.SUCCESS, response.getCommand());
        PrincipalReport report = (PrincipalReport) response.getPayload();
        assertEquals(ReportDimension.TEACHER, report.getDimension());
        assertEquals(2, report.getGroups().size());

        ReportGroup midterm = report.getGroups().stream()
                .filter(g -> g.getEntityId() == 100).findFirst().orElseThrow();
        assertEquals(2, midterm.getCount());
        assertEquals(70.0, midterm.getMean(), 0.001);
        assertTrue(midterm.getLabel().contains("Midterm"));
        assertTrue(midterm.getLabel().contains("execution 100"));

        ReportGroup finalExam = report.getGroups().stream()
                .filter(g -> g.getEntityId() == 200).findFirst().orElseThrow();
        assertEquals(1, finalExam.getCount());
        assertEquals(90.0, finalExam.getMean(), 0.001);
    }

    @Test
    void examComparisonCanGroupByExamInsteadOfExecution() {
        when(dao.getVisibleGradeRecords()).thenReturn(List.of(
                examRow(10, 1, 100, "Midterm", 1, 60),   // exam 1, sitting 100
                examRow(10, 1, 200, "Midterm", 2, 80)));  // exam 1 released again, sitting 200

        ExamComparisonReportRequest request = new ExamComparisonReportRequest(ReportDimension.TEACHER, 10);
        request.setGroupByRelease(false);
        Message response = service.getExamComparisonReport(principal, request);

        PrincipalReport report = (PrincipalReport) response.getPayload();
        assertEquals(1, report.getGroups().size());
        ReportGroup group = report.getGroups().get(0);
        assertEquals(1, group.getEntityId());
        assertEquals(2, group.getCount());
        assertEquals("Midterm", group.getLabel());
    }

    // ----- getReadOnlyData() -----------------------------------------------

    @Test
    void nonPrincipalCannotRequestReadOnlyData() {
        User teacher = new User(2, "teacher", Role.TEACHER, "Teacher", null);
        assertThrows(AuthorizationException.class, () -> service.getReadOnlyData(teacher));
        verifyNoInteractions(readOnlyDao);
    }

    @Test
    void readOnlyDataReturnsThePrincipalSnapshotUnchanged() {
        // The service must not touch or reshape this payload — it's a straight pass-through.
        PrincipalReadOnlyData snapshot = new PrincipalReadOnlyData();
        when(readOnlyDao.load()).thenReturn(snapshot);

        Message response = service.getReadOnlyData(principal);

        assertEquals(Message.Command.SUCCESS, response.getCommand());
        assertSame(snapshot, response.getPayload());
    }

    @Test
    void readOnlyDataErrorIsReturnedWithoutLeakingCause() {
        when(readOnlyDao.load()).thenThrow(new IllegalStateException("Could not load principal read-only data."));

        Message response = service.getReadOnlyData(principal);

        assertEquals(Message.Command.ERROR, response.getCommand());
        assertEquals("Could not load principal read-only data.", response.getPayload());
    }
}
