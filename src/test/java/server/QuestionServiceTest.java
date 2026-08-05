package server;

import common.entities.Question;
import common.entities.Role;
import common.entities.User;
import common.network.Message;
import common.network.Message.Command;
import common.network.QuestionFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import server.db.CourseDAO;
import server.db.QuestionDAO;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link QuestionService} (Person 2, Phase 5) — the question
 * bank's authorization + validation gate, tested with Mockito instead of a
 * socket: the DAOs are mocks, the caller is a plain {@link User}, so every
 * security rule runs in milliseconds with no database.
 */
@ExtendWith(MockitoExtension.class)
class QuestionServiceTest {

    @Mock private QuestionDAO dao;
    @Mock private CourseDAO courseDAO;

    private QuestionService service() {
        lenient().when(courseDAO.isTeacherAssigned(anyInt(), anyInt())).thenReturn(true);
        return new QuestionService(dao, courseDAO);
    }

    private static User user(Role role) {
        User u = new User();
        u.setId(7);
        u.setUsername("someone");
        u.setRole(role);
        return u;
    }

    private static Question validQuestion() {
        return new Question(1, "What is 2+2?", "3", "4", "5", "22", 2, null, "Math", "EASY");
    }

    // ===== every command requires a logged-in caller ======================

    @Test
    void anonymousCallersAreRejectedEverywhere() {
        QuestionService s = service();
        assertThatExceptionOfType(AuthorizationException.class).isThrownBy(() -> s.getCourses(null));
        assertThatExceptionOfType(AuthorizationException.class).isThrownBy(() -> s.getBank(null));
        assertThatExceptionOfType(AuthorizationException.class).isThrownBy(() -> s.getByCourse(null, 1));
        assertThatExceptionOfType(AuthorizationException.class).isThrownBy(() -> s.getFiltered(null, new QuestionFilter(1, null, null)));
        assertThatExceptionOfType(AuthorizationException.class).isThrownBy(() -> s.getHistory(null, 1));
        assertThatExceptionOfType(AuthorizationException.class).isThrownBy(() -> s.getImage(null, 1));
        assertThatExceptionOfType(AuthorizationException.class).isThrownBy(() -> s.add(null, validQuestion()));
        assertThatExceptionOfType(AuthorizationException.class).isThrownBy(() -> s.update(null, validQuestion()));
        assertThatExceptionOfType(AuthorizationException.class).isThrownBy(() -> s.delete(null, 1));
        verifyNoInteractions(dao, courseDAO);
    }

    // ===== reads: any logged-in role ======================================

    @Test
    void aStudentMayReadTheBank() {
        when(dao.getAllCurrent()).thenReturn(List.of(validQuestion()));
        Message r = service().getBank(user(Role.STUDENT));
        assertThat(r.getCommand()).isEqualTo(Command.SUCCESS);
        assertThat((List<?>) r.getPayload()).hasSize(1);
    }

    @Test
    void filteredReadDelegatesTheFilterFields() {
        service().getFiltered(user(Role.TEACHER), new QuestionFilter(3, "Sorting", "EASY"));
        verify(dao).getByCourseFiltered(3, "Sorting", "EASY");
    }

    @Test
    void readsRejectWrongPayloadTypes() {
        QuestionService s = service();
        User teacher = user(Role.TEACHER);
        assertThat(s.getByCourse(teacher, "one").getCommand()).isEqualTo(Command.ERROR);
        assertThat(s.getFiltered(teacher, 42).getCommand()).isEqualTo(Command.ERROR);
        assertThat(s.getHistory(teacher, "x").getCommand()).isEqualTo(Command.ERROR);
        assertThat(s.getImage(teacher, null).getCommand()).isEqualTo(Command.ERROR);
        verifyNoInteractions(dao);
    }

    // ===== mutations: teachers only (R-001, R-063) ========================

    @Test
    void studentsAndOtherRolesCannotMutateTheBank() {
        QuestionService s = service();
        for (Role role : new Role[]{Role.STUDENT, Role.COORDINATOR, Role.PRINCIPAL}) {
            User caller = user(role);
            assertThatExceptionOfType(AuthorizationException.class)
                    .as("%s must not add", role).isThrownBy(() -> s.add(caller, validQuestion()));
            assertThatExceptionOfType(AuthorizationException.class)
                    .as("%s must not update", role).isThrownBy(() -> s.update(caller, validQuestion()));
            assertThatExceptionOfType(AuthorizationException.class)
                    .as("%s must not delete", role).isThrownBy(() -> s.delete(caller, 1));
        }
        verifyNoInteractions(dao);
    }

    @Test
    void addRejectsWrongPayloadTypeWithoutTouchingTheDao() {
        Message r = service().add(user(Role.TEACHER), "not a question");
        assertThat(r.getCommand()).isEqualTo(Command.ERROR);
        verifyNoInteractions(dao);
    }

    @Test
    void addRejectsInvalidContentWithTheValidatorsMessage() {
        Question broken = validQuestion();
        broken.setQuestionText("  ");
        Message r = service().add(user(Role.TEACHER), broken);
        assertThat(r.getCommand()).isEqualTo(Command.ERROR);
        assertThat(String.valueOf(r.getPayload())).containsIgnoringCase("text");
        verifyNoInteractions(dao);
    }

    @Test
    void validAddRepliesWithTheSavedQuestionOnlyAndNoImageBytes() {
        // NFR 18: mutation replies are surgical — the affected question, never
        // the whole bank, and never an echo of the uploaded illustration bytes.
        Question q = validQuestion();
        q.setImagePath("d.png");
        q.setImageData(new byte[]{1, 2, 3});
        when(dao.add(q)).thenReturn(q);

        Message r = service().add(user(Role.TEACHER), q);

        assertThat(r.getCommand()).isEqualTo(Command.SUCCESS);
        Question saved = (Question) r.getPayload();
        assertThat(saved.getImagePath()).isEqualTo("d.png");
        assertThat(saved.getImageData()).as("no byte echo (NFR 18)").isNull();
        verify(dao).add(q);
    }

    @Test
    void teacherCannotAddQuestionForCourseTheyDoNotTeach() {
        when(courseDAO.isTeacherAssigned(7, 1)).thenReturn(false);
        QuestionService s = new QuestionService(dao, courseDAO);

        assertThatExceptionOfType(AuthorizationException.class)
                .isThrownBy(() -> s.add(user(Role.TEACHER), validQuestion()))
                .withMessageContaining("courses you teach");
        verify(dao, never()).add(any());
    }

    @Test
    void teacherCannotUpdateQuestionForCourseTheyDoNotTeach() {
        when(courseDAO.isTeacherAssigned(7, 1)).thenReturn(false);
        QuestionService s = new QuestionService(dao, courseDAO);
        Question q = validQuestion();
        q.setBaseId(5);

        assertThatExceptionOfType(AuthorizationException.class)
                .isThrownBy(() -> s.update(user(Role.TEACHER), q))
                .withMessageContaining("courses you teach");
        verify(dao, never()).update(any());
    }

    @Test
    void addFailureInTheDaoBecomesACleanError() {
        when(dao.add(any())).thenReturn(null);
        Message r = service().add(user(Role.TEACHER), validQuestion());
        assertThat(r.getCommand()).isEqualTo(Command.ERROR);
    }

    @Test
    void updateRequiresTheVersionFamilyId() {
        Question q = validQuestion();   // baseId == 0: the client forgot it
        Message r = service().update(user(Role.TEACHER), q);
        assertThat(r.getCommand()).isEqualTo(Command.ERROR);
        assertThat(String.valueOf(r.getPayload())).containsIgnoringCase("version");
        verify(dao, never()).update(any());
    }

    @Test
    void validUpdateRepliesWithTheNewCurrentVersion() {
        Question q = validQuestion();
        q.setBaseId(5);
        when(dao.update(q)).thenReturn(q);

        Message r = service().update(user(Role.TEACHER), q);

        assertThat(r.getCommand()).isEqualTo(Command.SUCCESS);
        assertThat(r.getPayload()).isSameAs(q);
        verify(dao).update(q);
    }

    @Test
    void deleteValidatesThePayloadAndRepliesWithTheRemovedFamilyId() {
        QuestionService s = service();
        assertThat(s.delete(user(Role.TEACHER), "family").getCommand()).isEqualTo(Command.ERROR);

        when(dao.delete(99)).thenReturn(false);
        assertThat(s.delete(user(Role.TEACHER), 99).getCommand()).isEqualTo(Command.ERROR);

        when(dao.delete(1)).thenReturn(true);
        Message r = s.delete(user(Role.TEACHER), 1);
        assertThat(r.getCommand()).isEqualTo(Command.SUCCESS);
        assertThat(r.getPayload()).as("reply = removed baseId (NFR 18)").isEqualTo(1);
    }

    @Test
    void imageFetchReturnsTheBytes() {
        when(dao.getImage(4)).thenReturn(new byte[]{1, 2});
        Message r = service().getImage(user(Role.STUDENT), 4);
        assertThat(r.getCommand()).isEqualTo(Command.SUCCESS);
        assertThat((byte[]) r.getPayload()).containsExactly(1, 2);
    }

    @Test
    void updateFailureInTheDaoBecomesACleanError() {
        Question q = validQuestion();
        q.setBaseId(5);
        when(dao.update(q)).thenReturn(null);
        Message r = service().update(user(Role.TEACHER), q);
        assertThat(r.getCommand()).isEqualTo(Command.ERROR);
    }

    @Test
    void remainingReadsDelegateToTheirQueries() {
        QuestionService s = service();
        User student = user(Role.STUDENT);

        when(courseDAO.getAll()).thenReturn(List.of());
        assertThat(s.getCourses(student).getCommand()).isEqualTo(Command.SUCCESS);

        when(dao.getByCourse(2)).thenReturn(List.of(validQuestion()));
        assertThat((List<?>) s.getByCourse(student, 2).getPayload()).hasSize(1);

        when(dao.getHistory(9)).thenReturn(List.of(validQuestion()));
        assertThat((List<?>) s.getHistory(student, 9).getPayload()).hasSize(1);
    }
}
