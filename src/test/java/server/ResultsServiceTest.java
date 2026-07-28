package server;

import common.entities.*;
import common.network.CheckedExamResult;
import common.network.Message;
import common.network.StudentResultSummary;
import common.network.TeacherExamResults;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import server.db.*;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResultsServiceTest {
    @Mock GradeDAO gradeDAO;
    @Mock ExamSessionDAO sessionDAO;
    @Mock ExamDAO examDAO;
    @Mock QuestionDAO questionDAO;
    @Mock ExamReleaseDAO releaseDAO;
    @Mock ExamSnapshotDAO snapshotDAO;
    @Mock ExecutionReportDAO reportDAO;

    private static final int RELEASE_ID = 10;

    private ResultsService service;
    private User student;
    private User teacher;
    private Exam exam;
    private ExamSession attempt;
    private Grade approved;

    @BeforeEach
    void setUp() {
        // Fully-mocked constructor: getCheckedExam() reads the immutable release
        // snapshot (ExamSnapshotDAO) and getTeacherExamResults() reads a release
        // (ExamReleaseDAO) — both must be mocked or these tests silently fall
        // through to a real database.
        service = new ResultsService(gradeDAO, sessionDAO, examDAO, questionDAO,
                releaseDAO, snapshotDAO, reportDAO, Clock.systemDefaultZone());
        student = new User(50, "student", Role.STUDENT, "Student", "123456789");
        teacher = new User(7, "teacher", Role.TEACHER, "Teacher", null);

        exam = new Exam();
        exam.setId(20);
        exam.setTeacherId(7);
        exam.setTitle("Algebra Final");
        exam.setQuestions(List.of(
                new ExamQuestion(101, 40, 1),
                new ExamQuestion(102, 60, 2)));

        attempt = new ExamSession();
        attempt.setId(30);
        attempt.setExamId(20);
        attempt.setStudentId(50);
        attempt.setReleaseId(RELEASE_ID);
        attempt.setStatus(ExamSessionStatus.SUBMITTED);
        attempt.setSubmittedAt(LocalDateTime.of(2026, 7, 21, 12, 0));
        attempt.setAnswers(List.of(new StudentAnswer(101, 2), new StudentAnswer(102, 1)));

        approved = new Grade(30, 20, 50, 40, LocalDateTime.now());
        approved.setId(1);
        approved.setStatus(GradeStatus.APPROVED);
        approved.setFinalScore(40);
    }

    @Test
    void studentListContainsOnlyTheirApprovedResults() {
        when(gradeDAO.getVisibleByStudent(50)).thenReturn(List.of(approved));
        when(examDAO.getById(20)).thenReturn(exam);
        when(sessionDAO.getById(30)).thenReturn(attempt);

        Message response = service.getStudentResults(student);

        assertThat(response.getCommand()).isEqualTo(Message.Command.SUCCESS);
        List<?> payload = (List<?>) response.getPayload();
        assertThat(payload).hasSize(1);
        StudentResultSummary summary = (StudentResultSummary) payload.get(0);
        assertThat(summary.getExamTitle()).isEqualTo("Algebra Final");
        assertThat(summary.getScore()).isEqualTo(40);
        verify(gradeDAO).getVisibleByStudent(50);
    }

    @Test
    void unapprovedGradeCannotBeOpenedAsCheckedExam() {
        approved.setStatus(GradeStatus.AUTO_GRADED);
        approved.setFinalScore(null);
        when(gradeDAO.getById(1)).thenReturn(approved);

        Message response = service.getCheckedExam(student, 1);

        assertThat(response.getCommand()).isEqualTo(Message.Command.ERROR);
        verifyNoInteractions(examDAO, sessionDAO, questionDAO);
    }

    @Test
    void studentCannotOpenAnotherStudentsCheckedExam() {
        approved.setStudentId(999);
        when(gradeDAO.getById(1)).thenReturn(approved);

        assertThatThrownBy(() -> service.getCheckedExam(student, 1))
                .isInstanceOf(AuthorizationException.class)
                .hasMessageContaining("own checked exams");
    }

    @Test
    void checkedExamMarksCorrectAndWrongAnswers() {
        when(gradeDAO.getById(1)).thenReturn(approved);
        when(examDAO.getById(20)).thenReturn(exam);
        when(sessionDAO.getById(30)).thenReturn(attempt);
        // getCheckedExam() reads the immutable release snapshot, not live Questions.
        when(snapshotDAO.getByRelease(RELEASE_ID)).thenReturn(List.of(
                snapshotQuestion(101, 40, 1, "2 + 2", 2),
                snapshotQuestion(102, 60, 2, "3 + 3", 4)));

        Message response = service.getCheckedExam(student, 1);

        assertThat(response.getCommand()).isEqualTo(Message.Command.SUCCESS);
        CheckedExamResult checked = (CheckedExamResult) response.getPayload();
        assertThat(checked.getAnswers()).hasSize(2);
        assertThat(checked.getAnswers().get(0).isCorrect()).isTrue();
        assertThat(checked.getAnswers().get(1).isCorrect()).isFalse();
        assertThat(checked.getAnswers().get(1).getCorrectAnswer()).isEqualTo(4);
    }

    @Test
    void teacherCannotViewResultsForAnotherTeachersExam() {
        exam.setTeacherId(999);
        ExamRelease release = new ExamRelease(20, 7, "AB12",
                LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1));
        release.setId(RELEASE_ID);
        when(releaseDAO.getById(RELEASE_ID)).thenReturn(release);
        when(examDAO.getById(20)).thenReturn(exam);

        // Payload is a release ID, not an exam ID — each administered sitting
        // has its own statistics.
        assertThatThrownBy(() -> service.getTeacherExamResults(teacher, RELEASE_ID))
                .isInstanceOf(AuthorizationException.class)
                .hasMessageContaining("authored");
    }

    @Test
    void teacherReceivesTableAndHistogramForOwnExam() {
        ExamRelease release = new ExamRelease(20, 7, "AB12",
                LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1));
        release.setId(RELEASE_ID);
        Grade g1 = grade(1, 30, 50, 40, 40, GradeStatus.APPROVED);
        Grade g2 = grade(2, 31, 51, 70, 80, GradeStatus.OVERRIDDEN);
        Grade g3 = grade(3, 32, 52, 100, null, GradeStatus.AUTO_GRADED);
        when(releaseDAO.getById(RELEASE_ID)).thenReturn(release);
        when(examDAO.getById(20)).thenReturn(exam);
        when(gradeDAO.getVisibleByRelease(RELEASE_ID)).thenReturn(List.of(g1, g2, g3));
        when(sessionDAO.getById(30)).thenReturn(session(30, 50));
        when(sessionDAO.getById(31)).thenReturn(session(31, 51));
        when(sessionDAO.getById(32)).thenReturn(session(32, 52));

        Message response = service.getTeacherExamResults(teacher, RELEASE_ID);

        assertThat(response.getCommand()).isEqualTo(Message.Command.SUCCESS);
        TeacherExamResults results = (TeacherExamResults) response.getPayload();
        assertThat(results.getRows()).hasSize(3);
        assertThat(results.getResultCount()).isEqualTo(3);
        assertThat(results.getMean()).isEqualTo(220.0 / 3.0);
        assertThat(results.getMedian()).isEqualTo(80.0);
        assertThat(results.getMinimum()).isEqualTo(40);
        assertThat(results.getMaximum()).isEqualTo(100);
        assertThat(results.getHistogram()).hasSize(10);
        assertThat(results.getHistogram().get(4).getCount()).isEqualTo(1); // 40-49
        assertThat(results.getHistogram().get(8).getCount()).isEqualTo(1); // 80-89
        assertThat(results.getHistogram().get(9).getCount()).isEqualTo(1); // 90-100
    }

    @Test
    void studentCannotRequestTeacherStatistics() {
        assertThatThrownBy(() -> service.getTeacherExamResults(student, RELEASE_ID))
                .isInstanceOf(AuthorizationException.class);
    }

    private static ExamSnapshotQuestion snapshotQuestion(int questionId, int points, int position,
                                                          String text, int correctAnswer) {
        return new ExamSnapshotQuestion(questionId, points, position, text,
                "A", "B", "C", "D", correctAnswer, null);
    }

    private static Grade grade(int id, int sessionId, int studentId,
                               int autoScore, Integer finalScore, GradeStatus status) {
        Grade grade = new Grade(sessionId, 20, studentId, autoScore, LocalDateTime.now());
        grade.setId(id);
        grade.setFinalScore(finalScore);
        grade.setStatus(status);
        return grade;
    }

    private static ExamSession session(int id, int studentId) {
        ExamSession s = new ExamSession();
        s.setId(id);
        s.setExamId(20);
        s.setReleaseId(RELEASE_ID);
        s.setStudentId(studentId);
        s.setStatus(ExamSessionStatus.SUBMITTED);
        s.setSubmittedAt(LocalDateTime.now());
        return s;
    }
}
