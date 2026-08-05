package server;

import common.entities.Exam;
import common.entities.ExamQuestion;
import common.entities.ExamStatus;
import common.entities.Question;
import common.entities.Role;
import common.entities.User;
import common.network.AutoExamRequest;
import common.network.AutoExamRequirement;
import common.network.ExamRejectionRequest;
import common.network.Message;
import common.network.Message.Command;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import server.db.CourseDAO;
import server.db.ExamDAO;
import server.db.QuestionSource;
import server.db.SubjectDAO;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ExamService} — the authorization and validation
 * facade for exam building and coordinator approval.
 *
 * <p>The DAO, question source and automatic generator are Mockito mocks.
 * Therefore, these tests require no database, socket or JavaFX.</p>
 */
@ExtendWith(MockitoExtension.class)
class ExamServiceTest {

    @Mock
    private ExamDAO examDAO;

    @Mock
    private QuestionSource questionSource;

    @Mock
    private AutoExamGenerator autoExamGenerator;

    @Mock
    private CourseDAO courseDAO;

    @Mock
    private SubjectDAO subjectDAO;

    private ExamService service() {
        lenient().when(courseDAO.isTeacherAssigned(anyInt(), anyInt())).thenReturn(true);
        lenient().when(subjectDAO.isCoordinatorForCourse(anyInt(), anyInt())).thenReturn(true);
        return new ExamService(
                examDAO,
                questionSource,
                autoExamGenerator,
                courseDAO,
                subjectDAO
        );
    }

    // ===== fixtures =======================================================

    private static User user(int id, Role role) {
        User user = new User();
        user.setId(id);
        user.setUsername(role.name().toLowerCase());
        user.setRole(role);
        return user;
    }

    private static User teacher() {
        return user(10, Role.TEACHER);
    }

    private static User otherTeacher() {
        return user(11, Role.TEACHER);
    }

    private static User coordinator() {
        return user(20, Role.COORDINATOR);
    }

    private static User principal() {
        return user(30, Role.PRINCIPAL);
    }

    private static User student() {
        return user(40, Role.STUDENT);
    }

    private static Exam validExam() {
        return new Exam(
                1,
                999,
                "Algorithms Midterm",
                90,
                "Answer all questions.",
                "Teacher-only notes.",
                List.of(
                        new ExamQuestion(101, 40, 1),
                        new ExamQuestion(102, 30, 2),
                        new ExamQuestion(103, 30, 3)
                )
        );
    }

    private static Exam storedExam(int id,
                                   int teacherId,
                                   ExamStatus status) {

        Exam exam = validExam();

        exam.setId(id);
        exam.setBaseId(id);
        exam.setTeacherId(teacherId);
        exam.setVersion(1);
        exam.setCurrent(true);
        exam.setStatus(status);

        return exam;
    }

    private static AutoExamRequest validAutoRequest() {
        return new AutoExamRequest(
                1,
                999,
                "Automatic Exam",
                60,
                "Answer all questions.",
                "Generated automatically.",
                List.of(
                        new AutoExamRequirement(
                                "Sorting",
                                "EASY",
                                2,
                                20
                        ),
                        new AutoExamRequirement(
                                "Complexity",
                                "MEDIUM",
                                2,
                                30
                        )
                )
        );
    }

    // ===== authentication and roles ======================================

    @Test
    void anonymousCallersAreRejectedEverywhere() {
        ExamService service = service();

        assertThatExceptionOfType(AuthorizationException.class)
                .isThrownBy(() -> service.getAll(null));

        assertThatExceptionOfType(AuthorizationException.class)
                .isThrownBy(() -> service.getById(null, 1));

        assertThatExceptionOfType(AuthorizationException.class)
                .isThrownBy(() -> service.getMine(null));

        assertThatExceptionOfType(AuthorizationException.class)
                .isThrownBy(() -> service.getPending(null));

        assertThatExceptionOfType(AuthorizationException.class)
                .isThrownBy(() -> service.create(null, validExam()));

        assertThatExceptionOfType(AuthorizationException.class)
                .isThrownBy(() -> service.update(null, validExam()));

        assertThatExceptionOfType(AuthorizationException.class)
                .isThrownBy(() ->
                        service.generateAuto(null, validAutoRequest()));

        assertThatExceptionOfType(AuthorizationException.class)
                .isThrownBy(() ->
                        service.submitForApproval(null, 1));

        assertThatExceptionOfType(AuthorizationException.class)
                .isThrownBy(() -> service.approve(null, 1));

        assertThatExceptionOfType(AuthorizationException.class)
                .isThrownBy(() ->
                        service.reject(
                                null,
                                new ExamRejectionRequest(1, 20, "Reason")
                        ));

        verifyNoInteractions(
                examDAO,
                questionSource,
                autoExamGenerator
        );
    }

    @Test
    void onlyTeachersMayCreateUpdateGenerateAndSubmit() {
        ExamService service = service();

        for (Role role : new Role[]{
                Role.STUDENT,
                Role.COORDINATOR,
                Role.PRINCIPAL
        }) {
            User caller = user(50, role);

            assertThatExceptionOfType(AuthorizationException.class)
                    .as("%s must not create exams", role)
                    .isThrownBy(() ->
                            service.create(caller, validExam()));

            assertThatExceptionOfType(AuthorizationException.class)
                    .as("%s must not update exams", role)
                    .isThrownBy(() ->
                            service.update(caller, validExam()));

            assertThatExceptionOfType(AuthorizationException.class)
                    .as("%s must not auto-generate exams", role)
                    .isThrownBy(() ->
                            service.generateAuto(
                                    caller,
                                    validAutoRequest()
                            ));

            assertThatExceptionOfType(AuthorizationException.class)
                    .as("%s must not submit exams", role)
                    .isThrownBy(() ->
                            service.submitForApproval(caller, 1));
        }

        verifyNoInteractions(
                examDAO,
                questionSource,
                autoExamGenerator
        );
    }

    @Test
    void onlyCoordinatorMayApproveOrReject() {
        ExamService service = service();

        for (Role role : new Role[]{
                Role.TEACHER,
                Role.STUDENT,
                Role.PRINCIPAL
        }) {
            User caller = user(50, role);

            assertThatExceptionOfType(AuthorizationException.class)
                    .as("%s must not approve exams", role)
                    .isThrownBy(() ->
                            service.approve(caller, 1));

            assertThatExceptionOfType(AuthorizationException.class)
                    .as("%s must not reject exams", role)
                    .isThrownBy(() ->
                            service.reject(
                                    caller,
                                    new ExamRejectionRequest(
                                            1,
                                            20,
                                            "Reason"
                                    )
                            ));
        }

        verifyNoInteractions(examDAO);
    }

    // ===== reads ==========================================================

    @Test
    void teacherCoordinatorAndPrincipalMayReadAllExams() {
        when(examDAO.getAllCurrent())
                .thenReturn(List.of(storedExam(
                        1,
                        10,
                        ExamStatus.DRAFT
                )));

        ExamService service = service();

        for (User caller : new User[]{
                teacher(),
                coordinator(),
                principal()
        }) {
            Message response = service.getAll(caller);

            assertThat(response.getCommand())
                    .isEqualTo(Command.SUCCESS);

            assertThat((List<?>) response.getPayload())
                    .hasSize(1);
        }

        verify(examDAO,
                org.mockito.Mockito.times(3))
                .getAllCurrent();
    }

    @Test
    void studentCannotReadAllExams() {
        assertThatExceptionOfType(AuthorizationException.class)
                .isThrownBy(() ->
                        service().getAll(student()));

        verifyNoInteractions(examDAO);
    }

    @Test
    void getByIdRejectsWrongPayloadType() {
        Message response =
                service().getById(teacher(), "not-an-id");

        assertThat(response.getCommand())
                .isEqualTo(Command.ERROR);

        verifyNoInteractions(examDAO);
    }

    @Test
    void teacherMayOnlyReadOwnExam() {
        Exam exam = storedExam(
                5,
                otherTeacher().getId(),
                ExamStatus.DRAFT
        );

        when(examDAO.getById(5))
                .thenReturn(exam);

        assertThatExceptionOfType(AuthorizationException.class)
                .isThrownBy(() ->
                        service().getById(teacher(), 5));

        verify(examDAO).getById(5);
    }

    @Test
    void getMineDelegatesAuthenticatedTeacherId() {
        when(examDAO.getCurrentByTeacher(10))
                .thenReturn(List.of(
                        storedExam(
                                1,
                                10,
                                ExamStatus.DRAFT
                        )
                ));

        Message response =
                service().getMine(teacher());

        assertThat(response.getCommand())
                .isEqualTo(Command.SUCCESS);

        assertThat((List<?>) response.getPayload())
                .hasSize(1);

        verify(examDAO)
                .getCurrentByTeacher(10);
    }

    @Test
    void coordinatorMayReadPendingExams() {
        when(examDAO.getPendingApproval())
                .thenReturn(List.of(
                        storedExam(
                                1,
                                10,
                                ExamStatus.PENDING_APPROVAL
                        )
                ));

        Message response =
                service().getPending(coordinator());

        assertThat(response.getCommand())
                .isEqualTo(Command.SUCCESS);

        assertThat((List<?>) response.getPayload())
                .hasSize(1);

        verify(examDAO)
                .getPendingApproval();
    }

    // ===== create =========================================================

    @Test
    void createRejectsWrongPayloadWithoutTouchingDao() {
        Message response =
                service().create(
                        teacher(),
                        "not-an-exam"
                );

        assertThat(response.getCommand())
                .isEqualTo(Command.ERROR);

        verifyNoInteractions(examDAO);
    }

    @Test
    void createRejectsInvalidExamBeforeDaoCall() {
        Exam exam = validExam();
        exam.setTitle("   ");

        Message response =
                service().create(teacher(), exam);

        assertThat(response.getCommand())
                .isEqualTo(Command.ERROR);

        assertThat(String.valueOf(response.getPayload()))
                .containsIgnoringCase("title");

        verify(examDAO, never())
                .create(any());
    }

    @Test
    void validCreateUsesAuthenticatedTeacherAndReturnsSavedExam() {
        User teacher = teacher();
        Exam exam = validExam();

        when(examDAO.create(exam))
                .thenAnswer(invocation -> {
                    Exam saved = invocation.getArgument(0);
                    saved.setId(7);
                    saved.setBaseId(7);
                    return saved;
                });

        Message response =
                service().create(teacher, exam);

        assertThat(response.getCommand())
                .isEqualTo(Command.SUCCESS);

        Exam saved = (Exam) response.getPayload();

        assertThat(saved.getTeacherId())
                .isEqualTo(teacher.getId());

        assertThat(saved.getStatus())
                .isEqualTo(ExamStatus.DRAFT);

        assertThat(saved.getVersion())
                .isEqualTo(1);

        assertThat(saved.isCurrent())
                .isTrue();

        assertThat(saved.getCoordinatorId())
                .isNull();

        assertThat(saved.getRejectionReason())
                .isNull();

        verify(examDAO).create(exam);
    }

    @Test
    void teacherCannotCreateExamForCourseTheyDoNotTeach() {
        when(courseDAO.isTeacherAssigned(10, 1)).thenReturn(false);
        ExamService s = new ExamService(examDAO, questionSource, autoExamGenerator, courseDAO, subjectDAO);

        assertThatExceptionOfType(AuthorizationException.class)
                .isThrownBy(() -> s.create(teacher(), validExam()))
                .withMessageContaining("courses you teach");

        verify(examDAO, never()).create(any());
    }

    @Test
    void teacherCannotAutoGenerateForCourseTheyDoNotTeach() {
        when(courseDAO.isTeacherAssigned(10, 1)).thenReturn(false);
        ExamService s = new ExamService(examDAO, questionSource, autoExamGenerator, courseDAO, subjectDAO);

        assertThatExceptionOfType(AuthorizationException.class)
                .isThrownBy(() -> s.generateAuto(teacher(), validAutoRequest()))
                .withMessageContaining("courses you teach");

        verify(autoExamGenerator, never()).generate(any(), any());
    }

    @Test
    void coordinatorCannotApproveExamOutsideTheirSubject() {
        Exam pending = storedExam(5, 10, ExamStatus.PENDING_APPROVAL);
        when(examDAO.getById(5)).thenReturn(pending);
        when(subjectDAO.isCoordinatorForCourse(20, 1)).thenReturn(false);
        ExamService s = new ExamService(examDAO, questionSource, autoExamGenerator, courseDAO, subjectDAO);

        assertThatExceptionOfType(AuthorizationException.class)
                .isThrownBy(() -> s.approve(coordinator(), 5))
                .withMessageContaining("subjects you coordinate");

        verify(examDAO, never()).approve(anyInt(), anyInt());
    }

    @Test
    void daoCreateFailureBecomesCleanError() {
        when(examDAO.create(any()))
                .thenReturn(null);

        Message response =
                service().create(
                        teacher(),
                        validExam()
                );

        assertThat(response.getCommand())
                .isEqualTo(Command.ERROR);
    }

    // ===== versioned update ==============================================

    @Test
    void updateRequiresCurrentExamId() {
        Exam requested = validExam();
        requested.setId(0);

        Message response =
                service().update(
                        teacher(),
                        requested
                );

        assertThat(response.getCommand())
                .isEqualTo(Command.ERROR);

        verify(examDAO, never())
                .getById(anyInt());
    }

    @Test
    void teacherCannotEditAnotherTeachersExam() {
        Exam current = storedExam(
                5,
                otherTeacher().getId(),
                ExamStatus.DRAFT
        );

        when(examDAO.getById(5))
                .thenReturn(current);

        Exam requested = validExam();
        requested.setId(5);

        assertThatExceptionOfType(AuthorizationException.class)
                .isThrownBy(() ->
                        service().update(
                                teacher(),
                                requested
                        ));

        verify(examDAO, never())
                .createNewVersion(anyInt(), any());
    }

    @Test
    void approvedExamCannotBeEdited() {
        Exam current = storedExam(
                5,
                teacher().getId(),
                ExamStatus.APPROVED
        );

        when(examDAO.getById(5))
                .thenReturn(current);

        Exam requested = validExam();
        requested.setId(5);

        Message response =
                service().update(
                        teacher(),
                        requested
                );

        assertThat(response.getCommand())
                .isEqualTo(Command.ERROR);

        assertThat(String.valueOf(response.getPayload()))
                .containsIgnoringCase("draft");

        verify(examDAO, never())
                .createNewVersion(anyInt(), any());
    }

    @Test
    void validUpdateCreatesNewVersion() {
        Exam current = storedExam(
                5,
                teacher().getId(),
                ExamStatus.DRAFT
        );

        current.setBaseId(5);
        current.setVersion(2);

        Exam requested = validExam();
        requested.setId(5);
        requested.setTitle("Updated title");

        when(examDAO.getById(5))
                .thenReturn(current);

        when(examDAO.createNewVersion(
                eq(5),
                eq(requested)
        )).thenReturn(requested);

        Message response =
                service().update(
                        teacher(),
                        requested
                );

        assertThat(response.getCommand())
                .isEqualTo(Command.SUCCESS);

        assertThat(requested.getBaseId())
                .isEqualTo(5);

        assertThat(requested.getVersion())
                .isEqualTo(3);

        assertThat(requested.getStatus())
                .isEqualTo(ExamStatus.DRAFT);

        assertThat(requested.getTeacherId())
                .isEqualTo(teacher().getId());

        verify(examDAO)
                .createNewVersion(5, requested);
    }

    // ===== automatic generation ==========================================

    @Test
    void automaticGenerationUsesQuestionSourceAndSavesExam() {
        AutoExamRequest request =
                validAutoRequest();

        List<Question> pool =
                List.of(new Question());

        Exam generated =
                validExam();

        when(questionSource.getByCourse(1))
                .thenReturn(pool);

        when(autoExamGenerator.generate(
                request,
                pool
        )).thenReturn(generated);

        when(examDAO.create(generated))
                .thenReturn(generated);

        Message response =
                service().generateAuto(
                        teacher(),
                        request
                );

        assertThat(response.getCommand())
                .isEqualTo(Command.SUCCESS);

        assertThat(request.getTeacherId())
                .isEqualTo(teacher().getId());

        assertThat(generated.getTeacherId())
                .isEqualTo(teacher().getId());

        assertThat(generated.getStatus())
                .isEqualTo(ExamStatus.DRAFT);

        verify(questionSource)
                .getByCourse(1);

        verify(autoExamGenerator)
                .generate(request, pool);

        verify(examDAO)
                .create(generated);
    }

    @Test
    void generatorFailureBecomesReadableError() {
        AutoExamRequest request =
                validAutoRequest();

        List<Question> pool = List.of();

        when(questionSource.getByCourse(1))
                .thenReturn(pool);

        when(autoExamGenerator.generate(
                request,
                pool
        )).thenThrow(
                new IllegalArgumentException(
                        "Not enough questions."
                )
        );

        Message response =
                service().generateAuto(
                        teacher(),
                        request
                );

        assertThat(response.getCommand())
                .isEqualTo(Command.ERROR);

        assertThat(String.valueOf(response.getPayload()))
                .containsIgnoringCase("not enough");

        verify(examDAO, never())
                .create(any());
    }

    // ===== submit for approval ===========================================

    @Test
    void teacherCannotSubmitAnotherTeachersExam() {
        Exam exam = storedExam(
                7,
                otherTeacher().getId(),
                ExamStatus.DRAFT
        );

        when(examDAO.getById(7))
                .thenReturn(exam);

        assertThatExceptionOfType(AuthorizationException.class)
                .isThrownBy(() ->
                        service().submitForApproval(
                                teacher(),
                                7
                        ));

        verify(examDAO, never())
                .submitForApproval(anyInt());
    }

    @Test
    void validDraftCanBeSubmittedForApproval() {
        Exam exam = storedExam(
                7,
                teacher().getId(),
                ExamStatus.DRAFT
        );

        Exam submitted = storedExam(
                7,
                teacher().getId(),
                ExamStatus.PENDING_APPROVAL
        );

        when(examDAO.getById(7))
                .thenReturn(exam);

        when(examDAO.submitForApproval(7))
                .thenReturn(submitted);

        Message response =
                service().submitForApproval(
                        teacher(),
                        7
                );

        assertThat(response.getCommand())
                .isEqualTo(Command.SUCCESS);

        assertThat(((Exam) response.getPayload()).getStatus())
                .isEqualTo(
                        ExamStatus.PENDING_APPROVAL
                );

        verify(examDAO)
                .submitForApproval(7);
    }

    // ===== coordinator approval and rejection ============================

    @Test
    void coordinatorCanApprovePendingExam() {
        Exam pending = storedExam(
                8,
                teacher().getId(),
                ExamStatus.PENDING_APPROVAL
        );

        Exam approved = storedExam(
                8,
                teacher().getId(),
                ExamStatus.APPROVED
        );

        approved.setCoordinatorId(
                coordinator().getId()
        );

        when(examDAO.getById(8))
                .thenReturn(pending);

        when(examDAO.approve(
                8,
                coordinator().getId()
        )).thenReturn(approved);

        Message response =
                service().approve(
                        coordinator(),
                        8
                );

        assertThat(response.getCommand())
                .isEqualTo(Command.SUCCESS);

        Exam result =
                (Exam) response.getPayload();

        assertThat(result.getStatus())
                .isEqualTo(ExamStatus.APPROVED);

        assertThat(result.getCoordinatorId())
                .isEqualTo(coordinator().getId());

        verify(examDAO)
                .approve(
                        8,
                        coordinator().getId()
                );
    }

    @Test
    void draftCannotBeApproved() {
        Exam draft = storedExam(
                8,
                teacher().getId(),
                ExamStatus.DRAFT
        );

        when(examDAO.getById(8))
                .thenReturn(draft);

        Message response =
                service().approve(
                        coordinator(),
                        8
                );

        assertThat(response.getCommand())
                .isEqualTo(Command.ERROR);

        verify(examDAO, never())
                .approve(anyInt(), anyInt());
    }

    @Test
    void rejectionRequiresReason() {
        Exam pending = storedExam(
                9,
                teacher().getId(),
                ExamStatus.PENDING_APPROVAL
        );

        when(examDAO.getById(9))
                .thenReturn(pending);

        ExamRejectionRequest request =
                new ExamRejectionRequest(
                        9,
                        999,
                        "   "
                );

        Message response =
                service().reject(
                        coordinator(),
                        request
                );

        assertThat(response.getCommand())
                .isEqualTo(Command.ERROR);

        verify(examDAO, never())
                .reject(
                        anyInt(),
                        anyInt(),
                        any()
                );
    }

    @Test
    void coordinatorCanRejectAndStoreReason() {
        Exam pending = storedExam(
                9,
                teacher().getId(),
                ExamStatus.PENDING_APPROVAL
        );

        Exam rejected = storedExam(
                9,
                teacher().getId(),
                ExamStatus.REJECTED
        );

        rejected.setCoordinatorId(
                coordinator().getId()
        );

        rejected.setRejectionReason(
                "Add more hard questions."
        );

        when(examDAO.getById(9))
                .thenReturn(pending);

        when(examDAO.reject(
                9,
                coordinator().getId(),
                "Add more hard questions."
        )).thenReturn(rejected);

        ExamRejectionRequest request =
                new ExamRejectionRequest(
                        9,
                        999,
                        "  Add more hard questions.  "
                );

        Message response =
                service().reject(
                        coordinator(),
                        request
                );

        assertThat(response.getCommand())
                .isEqualTo(Command.SUCCESS);

        Exam result =
                (Exam) response.getPayload();

        assertThat(result.getStatus())
                .isEqualTo(ExamStatus.REJECTED);

        assertThat(result.getRejectionReason())
                .isEqualTo(
                        "Add more hard questions."
                );

        /*
         * The client-supplied coordinator ID is ignored.
         * The authenticated caller's ID is used instead.
         */
        assertThat(request.getCoordinatorId())
                .isEqualTo(coordinator().getId());

        verify(examDAO)
                .reject(
                        9,
                        coordinator().getId(),
                        "Add more hard questions."
                );
    }
}