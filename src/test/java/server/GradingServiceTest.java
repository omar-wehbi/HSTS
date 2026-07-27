package server;

import common.entities.*;
import common.network.Message;
import common.network.OverrideGradeRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import server.db.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GradingServiceTest {
    @Mock GradeDAO gradeDAO;
    @Mock ExamSessionDAO sessionDAO;
    @Mock ExamDAO examDAO;
    @Mock QuestionDAO questionDAO;
    @Mock ExamSnapshotDAO snapshotDAO;
    @Mock ExecutionReportDAO reportDAO;

    private GradingService service;
    private User teacher;
    private Exam exam;
    private ExamSession attempt;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-21T12:00:00Z"), ZoneOffset.UTC);
        // Fully-mocked constructor: autoGrade() reads from the immutable release
        // snapshot (ExamSnapshotDAO), not live QuestionDAO rows, so that has to be
        // mocked too or these tests silently fall through to a real database.
        service = new GradingService(gradeDAO, sessionDAO, examDAO, questionDAO, snapshotDAO, reportDAO, clock);
        teacher = new User(7, "teacher", Role.TEACHER, "Teacher", null);

        exam = new Exam();
        exam.setId(20);
        exam.setTeacherId(7);
        exam.setQuestions(List.of(
                new ExamQuestion(101, 40, 1),
                new ExamQuestion(102, 60, 2)));

        attempt = new ExamSession();
        attempt.setId(30);
        attempt.setExamId(20);
        attempt.setStudentId(50);
        attempt.setStatus(ExamSessionStatus.SUBMITTED);
        attempt.setAnswers(List.of(
                new StudentAnswer(101, 2),
                new StudentAnswer(102, 1)));
    }

    @Test
    void autoGradeAddsPointsOnlyForCorrectAnswers() {
        attempt.setReleaseId(10);
        when(sessionDAO.getById(30)).thenReturn(attempt);
        when(examDAO.getById(20)).thenReturn(exam);
        when(snapshotDAO.getByRelease(10)).thenReturn(List.of(
                snapshotQuestion(101, 40, 1, 2),
                snapshotQuestion(102, 60, 2, 4)));
        when(gradeDAO.create(any(Grade.class))).thenAnswer(invocation -> {
            Grade grade = invocation.getArgument(0);
            grade.setId(1);
            return grade;
        });

        Message response = service.autoGrade(teacher, 30);

        assertThat(response.getCommand()).isEqualTo(Message.Command.SUCCESS);
        Grade grade = (Grade) response.getPayload();
        assertThat(grade.getAutoScore()).isEqualTo(40);
        assertThat(grade.getStatus()).isEqualTo(GradeStatus.AUTO_GRADED);
        assertThat(grade.getFinalScore()).isNull();
    }

    @Test
    void autoGradeIsIdempotentWhenGradeAlreadyExists() {
        Grade existing = new Grade(30, 20, 50, 80, java.time.LocalDateTime.now());
        when(sessionDAO.getById(30)).thenReturn(attempt);
        when(examDAO.getById(20)).thenReturn(exam);
        when(gradeDAO.getBySessionId(30)).thenReturn(existing);

        Message response = service.autoGrade(teacher, 30);

        assertThat(response.getCommand()).isEqualTo(Message.Command.SUCCESS);
        assertThat(response.getPayload()).isSameAs(existing);
        verify(gradeDAO, never()).create(any());
    }

    @Test
    void overdueSessionIsExpiredBeforeGrading() {
        attempt.setStatus(ExamSessionStatus.IN_PROGRESS);
        attempt.setDeadline(java.time.LocalDateTime.of(2026, 7, 21, 11, 59));
        ExamSession timedOut = new ExamSession();
        timedOut.setId(30);
        timedOut.setExamId(20);
        timedOut.setStudentId(50);
        timedOut.setStatus(ExamSessionStatus.TIMED_OUT);
        timedOut.setAnswers(attempt.getAnswers());
        when(sessionDAO.getById(30)).thenReturn(timedOut);
        when(examDAO.getById(20)).thenReturn(exam);
        when(gradeDAO.create(any(Grade.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Message response = service.autoGrade(teacher, 30);

        assertThat(response.getCommand()).isEqualTo(Message.Command.SUCCESS);
        verify(sessionDAO).expireOverdueSessions(java.time.LocalDateTime.of(2026, 7, 21, 12, 0));
    }

    @Test
    void cannotGradeAnExamStillInProgress() {
        attempt.setStatus(ExamSessionStatus.IN_PROGRESS);
        when(sessionDAO.getById(30)).thenReturn(attempt);

        Message response = service.autoGrade(teacher, 30);

        assertThat(response.getCommand()).isEqualTo(Message.Command.ERROR);
        verifyNoInteractions(examDAO, questionDAO, gradeDAO);
    }

    @Test
    void teacherCannotGradeAnotherTeachersExam() {
        exam.setTeacherId(999);
        when(sessionDAO.getById(30)).thenReturn(attempt);
        when(examDAO.getById(20)).thenReturn(exam);

        assertThatThrownBy(() -> service.autoGrade(teacher, 30))
                .isInstanceOf(AuthorizationException.class)
                .hasMessageContaining("authored");
    }

    @Test
    void approveCopiesAutomaticScoreToFinalScore() {
        Grade grade = grade(1, GradeStatus.AUTO_GRADED);
        when(gradeDAO.getById(1)).thenReturn(grade);
        when(examDAO.getById(20)).thenReturn(exam);
        // GradingService.approve() always calls the 4-arg overload (teacherComment
        // is null here, not omitted) — stubbing the 3-arg overload never matches it.
        when(gradeDAO.approve(eq(1), eq(7), any(), any())).thenAnswer(invocation -> {
            grade.setFinalScore(grade.getAutoScore());
            grade.setStatus(GradeStatus.APPROVED);
            return grade;
        });

        Message response = service.approve(teacher, 1);

        assertThat(response.getCommand()).isEqualTo(Message.Command.SUCCESS);
        Grade approved = (Grade) response.getPayload();
        assertThat(approved.getFinalScore()).isEqualTo(70);
        assertThat(approved.isVisibleToStudent()).isTrue();
    }

    @Test
    void overrideRequiresWrittenJustification() {
        Message response = service.override(teacher,
                new OverrideGradeRequest(1, 85, "   "));

        assertThat(response.getCommand()).isEqualTo(Message.Command.ERROR);
        verifyNoInteractions(gradeDAO);
    }

    @Test
    void overrideRejectsScoreOutsideZeroToOneHundred() {
        Message response = service.override(teacher,
                new OverrideGradeRequest(1, 101, "Manual review"));

        assertThat(response.getCommand()).isEqualTo(Message.Command.ERROR);
        verifyNoInteractions(gradeDAO);
    }

    @Test
    void validOverrideStoresTrimmedReason() {
        Grade grade = grade(1, GradeStatus.APPROVED);
        when(gradeDAO.getById(1)).thenReturn(grade);
        when(examDAO.getById(20)).thenReturn(exam);
        when(gradeDAO.override(eq(1), eq(85), eq("Accepted alternative answer"), eq(7), any()))
                .thenAnswer(invocation -> {
                    grade.setFinalScore(85);
                    grade.setStatus(GradeStatus.OVERRIDDEN);
                    grade.setOverrideJustification(invocation.getArgument(2));
                    return grade;
                });

        Message response = service.override(teacher,
                new OverrideGradeRequest(1, 85, "  Accepted alternative answer  "));

        assertThat(response.getCommand()).isEqualTo(Message.Command.SUCCESS);
        Grade overridden = (Grade) response.getPayload();
        assertThat(overridden.getEffectiveScore()).isEqualTo(85);
        assertThat(overridden.getOverrideJustification())
                .isEqualTo("Accepted alternative answer");
        assertThat(overridden.isVisibleToStudent()).isTrue();
    }

    @Test
    void studentsCannotUseGradingCommands() {
        User student = new User(50, "student", Role.STUDENT, "Student", "123");
        assertThatThrownBy(() -> service.autoGrade(student, 30))
                .isInstanceOf(AuthorizationException.class);
    }

    private static ExamSnapshotQuestion snapshotQuestion(int questionId, int points, int position, int correctAnswer) {
        return new ExamSnapshotQuestion(questionId, points, position, "Question " + questionId,
                "A", "B", "C", "D", correctAnswer, null);
    }

    private static Grade grade(int id, GradeStatus status) {
        Grade grade = new Grade(30, 20, 50, 70, java.time.LocalDateTime.now());
        grade.setId(id);
        grade.setStatus(status);
        return grade;
    }
}
