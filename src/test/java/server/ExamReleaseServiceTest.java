package server;

import common.entities.*;
import common.network.ExamReleaseRequest;
import common.network.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import server.db.*;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExamReleaseServiceTest {

    private static final int COURSE_ID = 5;

    @Mock private ExamDAO examDAO;
    @Mock private ExamReleaseDAO releaseDAO;
    @Mock private CourseDAO courseDAO;
    @Mock private ExamSnapshotDAO snapshotDAO;
    @Mock private QuestionDAO questionDAO;

    private ExamReleaseService service() {
        // Fully-mocked constructor: release() checks CourseDAO.isTeacherAssigned()
        // and would otherwise hit a real, unmocked database.
        return new ExamReleaseService(examDAO, releaseDAO, courseDAO, snapshotDAO, questionDAO);
    }

    private static User user(int id, Role role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        return user;
    }

    private static Exam exam(int id, int teacherId, ExamStatus status, boolean current) {
        Exam exam = new Exam();
        exam.setId(id);
        exam.setTeacherId(teacherId);
        exam.setCourseId(COURSE_ID);
        exam.setStatus(status);
        exam.setCurrent(current);
        return exam;
    }

    private static ExamReleaseRequest validRequest() {
        LocalDateTime open = LocalDateTime.of(2026, 8, 1, 9, 0);
        return new ExamReleaseRequest(15, "0042", open, open.plusHours(2));
    }

    @Test
    void anonymousAndNonTeachersCannotRelease() {
        ExamReleaseService service = service();

        assertThatExceptionOfType(AuthorizationException.class)
                .isThrownBy(() -> service.release(null, validRequest()));
        assertThatExceptionOfType(AuthorizationException.class)
                .isThrownBy(() -> service.release(user(2, Role.STUDENT), validRequest()));
        assertThatExceptionOfType(AuthorizationException.class)
                .isThrownBy(() -> service.release(user(3, Role.COORDINATOR), validRequest()));
        assertThatExceptionOfType(AuthorizationException.class)
                .isThrownBy(() -> service.release(user(4, Role.PRINCIPAL), validRequest()));

        verifyNoInteractions(examDAO, releaseDAO, courseDAO);
    }

    @Test
    void rejectsWrongPayloadAndInvalidFieldsBeforeDatabaseAccess() {
        ExamReleaseService service = service();
        User teacher = user(10, Role.TEACHER);

        assertError(service.release(teacher, "wrong"),
                "RELEASE_EXAM requires an ExamReleaseRequest payload.");

        ExamReleaseRequest badCode = validRequest();
        badCode.setExecutionCode("42"); // too short under the current alphanumeric 4-char rule
        assertError(service.release(teacher, badCode),
                "Execution code must contain exactly 4 letters or digits.");

        ExamReleaseRequest badTimes = validRequest();
        badTimes.setCloseTime(badTimes.getOpenTime());
        assertError(service.release(teacher, badTimes),
                "Close time must be after open time.");

        verifyNoInteractions(examDAO, releaseDAO, courseDAO);
    }

    @Test
    void onlyCurrentApprovedExamCanBeReleased() {
        ExamReleaseService service = service();
        User teacher = user(10, Role.TEACHER);
        ExamReleaseRequest request = validRequest();

        when(examDAO.getById(15))
                .thenReturn(exam(15, 10, ExamStatus.DRAFT, true))
                .thenReturn(exam(15, 10, ExamStatus.APPROVED, false));
        when(courseDAO.isTeacherAssigned(10, COURSE_ID)).thenReturn(true);

        assertError(service.release(teacher, request),
                "Only an approved exam can be released.");
        assertError(service.release(teacher, request),
                "Only the current exam version can be released.");

        verify(releaseDAO, never()).create(any());
    }

    @Test
    void teacherCannotReleaseExamInACourseTheyDoNotTeach() {
        // exam(15, 99, ...) belongs to another teacher, in a course this caller
        // (id 10) is not assigned to — courseDAO.isTeacherAssigned(10, COURSE_ID)
        // is left unstubbed, so the mock's default (false) applies.
        when(examDAO.getById(15))
                .thenReturn(exam(15, 99, ExamStatus.APPROVED, true));

        assertThatExceptionOfType(AuthorizationException.class)
                .isThrownBy(() -> service().release(
                        user(10, Role.TEACHER), validRequest()))
                .withMessage("You may release only exams in courses you teach.");

        verifyNoInteractions(releaseDAO);
    }

    @Test
    void duplicateExecutionCodeIsRejected() {
        when(examDAO.getById(15))
                .thenReturn(exam(15, 10, ExamStatus.APPROVED, true));
        when(courseDAO.isTeacherAssigned(10, COURSE_ID)).thenReturn(true);
        when(releaseDAO.executionCodeConflicts(eq("0042"), any(), any())).thenReturn(true);

        Message response = service().release(
                user(10, Role.TEACHER), validRequest());

        assertError(response, "Execution code conflicts with another active or scheduled release.");
        verify(releaseDAO, never()).create(any());
    }

    @Test
    void validApprovedExamIsReleasedWithTeacherAndLeadingZeroCode() {
        User teacher = user(10, Role.TEACHER);
        ExamReleaseRequest request = validRequest();
        when(examDAO.getById(15))
                .thenReturn(exam(15, 10, ExamStatus.APPROVED, true));
        when(courseDAO.isTeacherAssigned(10, COURSE_ID)).thenReturn(true);
        when(releaseDAO.executionCodeConflicts(eq("0042"), any(), any())).thenReturn(false);
        when(snapshotDAO.createForRelease(eq(7), any(), eq(questionDAO))).thenReturn(true);
        when(releaseDAO.create(any(ExamRelease.class)))
                .thenAnswer(invocation -> {
                    ExamRelease release = invocation.getArgument(0);
                    release.setId(7);
                    return release;
                });

        Message response = service().release(teacher, request);

        assertThat(response.getCommand()).isEqualTo(Message.Command.SUCCESS);
        assertThat(response.getPayload()).isInstanceOf(ExamRelease.class);
        ExamRelease saved = (ExamRelease) response.getPayload();
        assertThat(saved.getId()).isEqualTo(7);
        assertThat(saved.getExamId()).isEqualTo(15);
        assertThat(saved.getReleasedBy()).isEqualTo(10);
        assertThat(saved.getExecutionCode()).isEqualTo("0042");
        assertThat(saved.getOpenTime()).isEqualTo(request.getOpenTime());
        assertThat(saved.getCloseTime()).isEqualTo(request.getCloseTime());
    }

    @Test
    void releasedExamListsRespectRoles() {
        ExamRelease release = new ExamRelease();
        when(releaseDAO.getByTeacher(10)).thenReturn(List.of(release));
        when(releaseDAO.getAll()).thenReturn(List.of(release));

        Message teacherResponse = service().getReleased(user(10, Role.TEACHER));
        Message coordinatorResponse = service().getReleased(user(20, Role.COORDINATOR));
        Message principalResponse = service().getReleased(user(30, Role.PRINCIPAL));

        assertThat(teacherResponse.getCommand()).isEqualTo(Message.Command.SUCCESS);
        assertThat(coordinatorResponse.getCommand()).isEqualTo(Message.Command.SUCCESS);
        assertThat(principalResponse.getCommand()).isEqualTo(Message.Command.SUCCESS);
        verify(releaseDAO).getByTeacher(10);
        verify(releaseDAO, org.mockito.Mockito.times(2)).getAll();

        assertThatExceptionOfType(AuthorizationException.class)
                .isThrownBy(() -> service().getReleased(user(40, Role.STUDENT)));
    }

    private static void assertError(Message response, String message) {
        assertThat(response.getCommand()).isEqualTo(Message.Command.ERROR);
        assertThat(response.getPayload()).isEqualTo(message);
    }
}
