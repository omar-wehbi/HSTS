package client.ui;

import common.entities.Role;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HomeViewNavigationTest {

    @Test
    void teacherMenuEnablesAllTeacherFlows() {
        List<String> labels = HomeView.menuLabelsFor(Role.TEACHER);
        assertThat(labels).containsExactly(
                "Question Bank",
                "Build Exams",
                "Release Exams",
                "Grade Exams",
                "Exam Results",
                "Study Bot");
        assertThat(labels).noneMatch(s -> s.contains("coming soon"));
    }

    @Test
    void coordinatorMenuEnablesApproveExams() {
        List<String> labels = HomeView.menuLabelsFor(Role.COORDINATOR);
        assertThat(labels).containsExactly("Approve Exams");
    }

    @Test
    void principalMenuEnablesDataAndReports() {
        List<String> labels = HomeView.menuLabelsFor(Role.PRINCIPAL);
        assertThat(labels).containsExactly("View Data", "Reports");
        assertThat(labels).noneMatch(s -> s.contains("coming soon"));
    }

    @Test
    void studentMenuEnablesExamGradesAndBot() {
        List<String> labels = HomeView.menuLabelsFor(Role.STUDENT);
        assertThat(labels).containsExactly("Take Exam", "My Grades", "Study Bot");
        assertThat(labels).noneMatch(s -> s.contains("coming soon"));
    }
}
