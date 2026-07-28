package client.ui;

import common.entities.Role;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Home menu wiring for Person 5 scenarios 1–4 (enabled vs coming-soon).
 *
 * <p>Uses the package-visible {@link HomeView#menuLabelsFor(Role)} helper so we
 * do not need a JavaFX toolkit to verify role menus.
 */
class HomeViewNavigationTest {

    @Test
    void teacherMenuEnablesBuildExamsAndBank() {
        List<String> labels = HomeView.menuLabelsFor(Role.TEACHER);
        assertThat(labels).contains("Question Bank", "Build Exams");
        assertThat(labels).anyMatch(s -> s.contains("Grade Exams") && s.contains("coming soon"));
    }

    @Test
    void coordinatorMenuEnablesApproveExams() {
        List<String> labels = HomeView.menuLabelsFor(Role.COORDINATOR);
        assertThat(labels).containsExactly("Approve Exams");
    }

    @Test
    void studentMenuStillComingSoon() {
        List<String> labels = HomeView.menuLabelsFor(Role.STUDENT);
        assertThat(labels).allMatch(s -> s.contains("coming soon"));
    }
}
