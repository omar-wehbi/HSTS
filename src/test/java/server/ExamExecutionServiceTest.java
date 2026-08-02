package server;

import common.entities.*;
import common.network.ExamExecutionSummary;
import common.network.ExamForm;
import common.network.ExtendExamTimeRequest;
import common.network.Message;
import common.network.SaveAnswersRequest;
import common.network.StartExamRequest;
import common.network.SubmitAnswersRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import server.db.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExamExecutionServiceTest {
    @Mock ExamReleaseDAO releaseDAO;
    @Mock ExamDAO examDAO;
    @Mock ExamSessionDAO sessionDAO;
    @Mock QuestionDAO questionDAO;
    @Mock UserDAO userDAO;
    @Mock ExamSnapshotDAO snapshotDAO;
    @Mock ExecutionReportDAO reportDAO;

    private ExamExecutionService service;
    private User student;
    private User teacher;
    private User coordinator;
    private User principal;
    private ExamSession attempt;
    private Exam exam;
    private final LocalDateTime now = LocalDateTime.of(2026, 7, 21, 12, 0);

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-21T12:00:00Z"), ZoneOffset.UTC);
        // Use the fully-mocked constructor everywhere so summary()'s snapshotDAO/reportDAO
        // calls never touch a real Hibernate SessionFactory during unit tests.
        service = new ExamExecutionService(releaseDAO, examDAO, sessionDAO,
                questionDAO, userDAO, snapshotDAO, reportDAO, clock);
        student = new User(50, "student", Role.STUDENT, "Student", "123456789");
        teacher = new User(7, "teacher", Role.TEACHER, "Teacher", null);
        coordinator = new User(8, "coordinator", Role.COORDINATOR, "Coordinator", null);
        principal = new User(9, "principal", Role.PRINCIPAL, "Principal", null);

        attempt = new ExamSession();
        attempt.setId(30);
        attempt.setReleaseId(10);
        attempt.setExamId(20);
        attempt.setStudentId(50);
        attempt.setStatus(ExamSessionStatus.IN_PROGRESS);
        attempt.setDeadline(now.plusMinutes(10));

        exam = new Exam();
        exam.setId(20);
        exam.setTeacherId(7);
        exam.setQuestions(List.of(new ExamQuestion(101, 100, 1)));
    }

    @Test
    void lateSubmissionIsRejectedWithoutSavingLateAnswers() {
        attempt.setDeadline(now.minusSeconds(1));
        when(sessionDAO.getById(30)).thenReturn(attempt);
        when(sessionDAO.expireOverdueSession(30, now)).thenReturn(attempt);

        Message response = service.submit(student,
                new SubmitAnswersRequest(30, List.of(new StudentAnswer(101, 2))));

        assertThat(response.getCommand()).isEqualTo(Message.Command.ERROR);
        assertThat(String.valueOf(response.getPayload())).contains("Late answers were not accepted");
        verify(sessionDAO).expireOverdueSession(30, now);
        verify(sessionDAO, never()).submit(anyInt(), anyList(), any(), any());
        verifyNoInteractions(examDAO);
    }

    @Test
    void teacherCannotReadAnotherTeachersSession() {
        exam.setTeacherId(999);
        when(sessionDAO.getById(30)).thenReturn(attempt);
        when(examDAO.getById(20)).thenReturn(exam);

        assertThatThrownBy(() -> service.getSession(teacher, 30))
                .isInstanceOf(AuthorizationException.class)
                .hasMessageContaining("authored");
    }

    @Test
    void extensionIsRejectedBeforeReleaseOpens() {
        ExamRelease release = new ExamRelease(20, 7, "0042",
                now.plusMinutes(5), now.plusHours(1));
        release.setId(10);
        when(releaseDAO.getById(10)).thenReturn(release);

        Message response = service.extend(teacher, new ExtendExamTimeRequest(10, 15));

        assertThat(response.getCommand()).isEqualTo(Message.Command.ERROR);
        assertThat(String.valueOf(response.getPayload())).contains("only while the release is live");
        verify(sessionDAO, never()).extendActiveSessions(anyInt(), anyInt(), any());
    }

    @Test
    void extensionIsRejectedAfterReleaseCloses() {
        ExamRelease release = new ExamRelease(20, 7, "0042",
                now.minusHours(2), now.minusMinutes(1));
        release.setId(10);
        when(releaseDAO.getById(10)).thenReturn(release);

        Message response = service.extend(teacher, new ExtendExamTimeRequest(10, 15));

        assertThat(response.getCommand()).isEqualTo(Message.Command.ERROR);
        verify(sessionDAO, never()).extendActiveSessions(anyInt(), anyInt(), any());
    }

    @Test
    void validLiveExtensionUpdatesActiveSessions() {
        ExamRelease release = new ExamRelease(20, 7, "0042",
                now.minusMinutes(5), now.plusHours(1));
        release.setId(10);
        when(releaseDAO.getById(10)).thenReturn(release);
        when(sessionDAO.extendActiveSessions(10, 15, now)).thenReturn(3);

        Message response = service.extend(teacher, new ExtendExamTimeRequest(10, 15));

        assertThat(response.getCommand()).isEqualTo(Message.Command.SUCCESS);
        assertThat(response.getPayload()).isEqualTo(3);
    }

    // ----- summary() -----------------------------------------------------

    @Test
    void studentCannotViewExecutionSummary() {
        assertThatThrownBy(() -> service.summary(student, 10))
                .isInstanceOf(AuthorizationException.class);
        verifyNoInteractions(reportDAO);
    }

    @Test
    void summaryRejectsInvalidPayload() {
        Message response = service.summary(teacher, "not-an-id");

        assertThat(response.getCommand()).isEqualTo(Message.Command.ERROR);
        verifyNoInteractions(reportDAO);
    }

    @Test
    void summaryReturnsErrorWhenExecutionNotFound() {
        when(reportDAO.summary(10)).thenReturn(null);

        Message response = service.summary(teacher, 10);

        assertThat(response.getCommand()).isEqualTo(Message.Command.ERROR);
        assertThat(String.valueOf(response.getPayload())).contains("Exam execution was not found");
        verifyNoInteractions(examDAO);
        verifyNoInteractions(releaseDAO);
    }

    @Test
    void summaryReturnsErrorWhenExamIsMissing() {
        ExamExecutionSummary summary = new ExamExecutionSummary(
                10, 20, "Midterm", now.minusHours(1), now.plusHours(1), 60, 5, 3, 1);
        when(reportDAO.summary(10)).thenReturn(summary);
        when(examDAO.getById(20)).thenReturn(null);

        Message response = service.summary(teacher, 10);

        assertThat(response.getCommand()).isEqualTo(Message.Command.ERROR);
        assertThat(response.getPayload()).isEqualTo("Exam was not found.");
        verifyNoInteractions(releaseDAO);
    }

    @Test
    void summaryReturnsErrorWhenReleaseIsMissingForATeacher() {
        // Even the exam's own author should get a clean error, not an NPE,
        // if the release row backing this execution has disappeared.
        exam.setTeacherId(7);
        ExamExecutionSummary summary = new ExamExecutionSummary(
                10, 20, "Midterm", now.minusHours(1), now.plusHours(1), 60, 5, 3, 1);
        when(reportDAO.summary(10)).thenReturn(summary);
        when(examDAO.getById(20)).thenReturn(exam);
        when(releaseDAO.getById(10)).thenReturn(null);

        Message response = service.summary(teacher, 10);

        assertThat(response.getCommand()).isEqualTo(Message.Command.ERROR);
        assertThat(response.getPayload()).isEqualTo("Exam release was not found.");
    }

    @Test
    void teacherCannotViewSummaryForAnExamTheyNeitherAuthoredNorAdministered() {
        exam.setTeacherId(999);
        ExamExecutionSummary summary = new ExamExecutionSummary(
                10, 20, "Midterm", now.minusHours(1), now.plusHours(1), 60, 5, 3, 1);
        ExamRelease release = new ExamRelease(20, 555, "AB12", now.minusHours(1), now.plusHours(1));
        release.setId(10);
        when(reportDAO.summary(10)).thenReturn(summary);
        when(examDAO.getById(20)).thenReturn(exam);
        when(releaseDAO.getById(10)).thenReturn(release);

        assertThatThrownBy(() -> service.summary(teacher, 10))
                .isInstanceOf(AuthorizationException.class)
                .hasMessageContaining("authored or administered");
    }

    @Test
    void examAuthorCanViewSummaryEvenIfAnotherTeacherAdministeredIt() {
        exam.setTeacherId(7);
        ExamExecutionSummary summary = new ExamExecutionSummary(
                10, 20, "Midterm", now.minusHours(1), now.plusHours(1), 60, 5, 3, 1);
        ExamRelease release = new ExamRelease(20, 555, "AB12", now.minusHours(1), now.plusHours(1));
        release.setId(10);
        when(reportDAO.summary(10)).thenReturn(summary);
        when(examDAO.getById(20)).thenReturn(exam);
        when(releaseDAO.getById(10)).thenReturn(release);

        Message response = service.summary(teacher, 10);

        assertThat(response.getCommand()).isEqualTo(Message.Command.SUCCESS);
        assertThat(response.getPayload()).isSameAs(summary);
    }

    @Test
    void administeringTeacherCanViewSummaryEvenIfAnotherTeacherAuthoredIt() {
        exam.setTeacherId(999);
        ExamExecutionSummary summary = new ExamExecutionSummary(
                10, 20, "Midterm", now.minusHours(1), now.plusHours(1), 60, 5, 3, 1);
        ExamRelease release = new ExamRelease(20, 7, "AB12", now.minusHours(1), now.plusHours(1));
        release.setId(10);
        when(reportDAO.summary(10)).thenReturn(summary);
        when(examDAO.getById(20)).thenReturn(exam);
        when(releaseDAO.getById(10)).thenReturn(release);

        Message response = service.summary(teacher, 10);

        assertThat(response.getCommand()).isEqualTo(Message.Command.SUCCESS);
        assertThat(response.getPayload()).isSameAs(summary);
    }

    @Test
    void coordinatorAndPrincipalSkipTheReleaseOwnershipCheck() {
        ExamExecutionSummary summary = new ExamExecutionSummary(
                10, 20, "Midterm", now.minusHours(1), now.plusHours(1), 60, 5, 3, 1);
        exam.setTeacherId(999);
        when(reportDAO.summary(10)).thenReturn(summary);
        when(examDAO.getById(20)).thenReturn(exam);

        Message coordinatorResponse = service.summary(coordinator, 10);
        Message principalResponse = service.summary(principal, 10);

        assertThat(coordinatorResponse.getCommand()).isEqualTo(Message.Command.SUCCESS);
        assertThat(principalResponse.getCommand()).isEqualTo(Message.Command.SUCCESS);
        verify(releaseDAO, never()).getById(anyInt());
    }

    // ----- start / save / submit happy paths --------------------------------

    @Test
    void startRejectsBadCodeAndMismatchedId() {
        assertThat(service.start(student, "bad").getCommand()).isEqualTo(Message.Command.ERROR);
        assertThat(service.start(student, new StartExamRequest("12", "123456789")).getCommand())
                .isEqualTo(Message.Command.ERROR);
        assertThat(service.start(student, new StartExamRequest("ABCD", "999")).getCommand())
                .isEqualTo(Message.Command.ERROR);
    }

    @Test
    void startCreatesSessionAndReturnsForm() {
        ExamRelease release = new ExamRelease(20, 7, "AB12", now.minusMinutes(5), now.plusHours(1));
        release.setId(10);
        exam.setCourseId(1);
        exam.setDurationMinutes(45);
        exam.setStudentInstructions("Go");
        when(releaseDAO.getOpenByExecutionCode("AB12", now)).thenReturn(release);
        when(examDAO.getById(20)).thenReturn(exam);
        when(userDAO.isEnrolled(50, 1)).thenReturn(true);
        when(sessionDAO.getByReleaseAndStudent(10, 50)).thenReturn(null);
        ExamSession created = new ExamSession(10, 20, 50, now, now.plusMinutes(45));
        created.setId(99);
        when(sessionDAO.create(any(ExamSession.class))).thenReturn(created);
        when(snapshotDAO.getByRelease(10)).thenReturn(List.of(
                new ExamSnapshotQuestion(101, 100, 1, "Q?", "a", "b", "c", "d", 2, null)));

        Message response = service.start(student, new StartExamRequest("AB12", "123456789"));
        assertThat(response.getCommand()).isEqualTo(Message.Command.SUCCESS);
        assertThat(response.getPayload()).isInstanceOf(ExamForm.class);
    }

    @Test
    void startRejectsWhenNotEnrolledOrAlreadyAttempted() {
        ExamRelease release = new ExamRelease(20, 7, "AB12", now.minusMinutes(5), now.plusHours(1));
        release.setId(10);
        exam.setCourseId(1);
        when(releaseDAO.getOpenByExecutionCode("AB12", now)).thenReturn(release);
        when(examDAO.getById(20)).thenReturn(exam);
        when(userDAO.isEnrolled(50, 1)).thenReturn(false);
        assertThat(service.start(student, new StartExamRequest("AB12", "123456789")).getCommand())
                .isEqualTo(Message.Command.ERROR);

        when(userDAO.isEnrolled(50, 1)).thenReturn(true);
        attempt.setStatus(ExamSessionStatus.SUBMITTED);
        when(sessionDAO.getByReleaseAndStudent(10, 50)).thenReturn(attempt);
        assertThat(service.start(student, new StartExamRequest("AB12", "123456789")).getCommand())
                .isEqualTo(Message.Command.ERROR);
    }

    @Test
    void saveAndSubmitHappyPath() {
        when(sessionDAO.getById(30)).thenReturn(attempt);
        when(examDAO.getById(20)).thenReturn(exam);
        when(snapshotDAO.getByRelease(10)).thenReturn(List.of(
                new ExamSnapshotQuestion(101, 100, 1, "Q?", "a", "b", "c", "d", 2, null)));
        when(sessionDAO.saveAnswers(eq(30), anyList())).thenReturn(attempt);
        when(sessionDAO.submit(eq(30), anyList(), eq(now), eq(ExamSessionStatus.SUBMITTED)))
                .thenReturn(attempt);

        Message saved = service.save(student, new SaveAnswersRequest(30, List.of(new StudentAnswer(101, 2))));
        assertThat(saved.getCommand()).isEqualTo(Message.Command.SUCCESS);

        Message submitted = service.submit(student,
                new SubmitAnswersRequest(30, List.of(new StudentAnswer(101, 2))));
        assertThat(submitted.getCommand()).isEqualTo(Message.Command.SUCCESS);
    }

    @Test
    void sessionsForReleaseAuthorisedForTeacher() {
        ExamRelease release = new ExamRelease(20, 7, "AB12", now.minusMinutes(5), now.plusHours(1));
        release.setId(10);
        when(releaseDAO.getById(10)).thenReturn(release);
        when(examDAO.getById(20)).thenReturn(exam);
        when(sessionDAO.getByRelease(10)).thenReturn(List.of(attempt));

        Message response = service.sessionsForRelease(teacher, 10);
        assertThat(response.getCommand()).isEqualTo(Message.Command.SUCCESS);
        assertThat((List<?>) response.getPayload()).hasSize(1);
    }
}
