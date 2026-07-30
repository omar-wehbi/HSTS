package client.ui;

import common.entities.User;
import common.entities.Role;
import common.network.Message;
import common.network.Message.Command;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Role-based home menu shown after a successful login (scenario 1: "after login,
 * show a menu appropriate to the user's role").
 *
 * <p>Teacher: Question Bank + Build Exams. Coordinator: Approve Exams.
 * Teacher/student/principal flows for scenarios 5–14 are wired below.
 */
public class HomeView extends AbstractScreenUI {

    private final User user;

    public HomeView(User user) {
        this.user = user;
    }

    @Override
    public Parent render() {
        // This screen declares no @Subscribe methods, so it simply receives no
        // server events (ScreenManager cleared the previous screen's handler).
        Label welcome = new Label("Welcome, " + user.getDisplayName());
        welcome.getStyleClass().add("header-title");
        Label roleLabel = new Label("Signed in as " + user.getRole());
        roleLabel.getStyleClass().add("header-subtitle");

        VBox menu = new VBox(10);
        menu.getChildren().addAll(roleButtons());

        Button logout = new Button("Log out");
        logout.getStyleClass().add("secondary-button");
        logout.setMaxWidth(Double.MAX_VALUE);
        logout.setOnAction(e -> onLogout());

        VBox card = new VBox(14, welcome, roleLabel, new Separator(),
                new Label("Menu"), menu, new Separator(), logout);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(24));
        card.setMaxWidth(440);
        card.setAlignment(Pos.TOP_LEFT);

        StackPane root = new StackPane(card);
        root.getStyleClass().add("connect-root");
        root.setPadding(new Insets(24));
        // Width is fixed; height follows the menu size so the Log out button
        // is always visible (the teacher menu is the tallest).
        root.setPrefWidth(560);
        return root;
    }

    /** The menu entries for this user's role. */
    private List<Button> roleButtons() {
        List<Button> buttons = new ArrayList<>();
        for (HomeMenuEntry entry : menuEntriesFor(user.getRole())) {
            if (entry.enabled()) {
                buttons.add(navButton(entry.label(), entry.action()));
            } else {
                buttons.add(soon(entry.label()));
            }
        }
        return buttons;
    }

    /**
     * Menu blueprint for a role (also used by unit tests without JavaFX nodes).
     */
    static List<String> menuLabelsFor(Role role) {
        List<String> labels = new ArrayList<>();
        for (HomeMenuEntry entry : menuEntriesFor(role)) {
            labels.add(entry.enabled() ? entry.label() : entry.label() + "  (coming soon)");
        }
        return labels;
    }

    private static List<HomeMenuEntry> menuEntriesFor(Role role) {
        List<HomeMenuEntry> entries = new ArrayList<>();
        switch (role) {
            case TEACHER -> {
                entries.add(new HomeMenuEntry("Question Bank", true,
                        () -> ScreenManager.getInstance().setScreen(new QuestionsView())));
                entries.add(new HomeMenuEntry("Build Exams", true,
                        () -> ScreenManager.getInstance().setScreen(new ExamListView())));
                entries.add(new HomeMenuEntry("Release Exams", true,
                        () -> ScreenManager.getInstance().setScreen(new ExamReleaseView())));
                entries.add(new HomeMenuEntry("Grade Exams", true,
                        () -> ScreenManager.getInstance().setScreen(new GradeExamsView())));
                entries.add(new HomeMenuEntry("Exam Results", true,
                        () -> ScreenManager.getInstance().setScreen(new ExamResultsView())));
                entries.add(new HomeMenuEntry("Study Bot", true,
                        () -> ScreenManager.getInstance().setScreen(new StudyBotTeacherView())));
            }
            case COORDINATOR -> entries.add(new HomeMenuEntry("Approve Exams", true,
                    () -> ScreenManager.getInstance().setScreen(new ExamApprovalView())));
            case PRINCIPAL -> {
                entries.add(new HomeMenuEntry("View Data", true,
                        () -> ScreenManager.getInstance().setScreen(new PrincipalDataView())));
                entries.add(new HomeMenuEntry("Reports", true,
                        () -> ScreenManager.getInstance().setScreen(new PrincipalReportsView())));
            }
            case STUDENT -> {
                entries.add(new HomeMenuEntry("Take Exam", true,
                        () -> ScreenManager.getInstance().setScreen(new TakeExamView())));
                entries.add(new HomeMenuEntry("My Grades", true,
                        () -> ScreenManager.getInstance().setScreen(new StudentGradesView())));
                entries.add(new HomeMenuEntry("Study Bot", true,
                        () -> ScreenManager.getInstance().setScreen(new StudyBotStudentView())));
            }
        }
        return entries;
    }

    private record HomeMenuEntry(String label, boolean enabled, Runnable action) {
    }

    private Button navButton(String text, Runnable action) {
        Button b = new Button(text);
        b.getStyleClass().add("primary-button");
        b.setMaxWidth(Double.MAX_VALUE);
        b.setOnAction(e -> action.run());
        return b;
    }

    /** A disabled placeholder for a feature another team member is building. */
    private Button soon(String text) {
        Button b = new Button(text + "  (coming soon)");
        b.getStyleClass().add("secondary-button");
        b.setMaxWidth(Double.MAX_VALUE);
        b.setDisable(true);
        return b;
    }

    private void onLogout() {
        try {
            client().send(new Message(Command.LOGOUT));
        } catch (IOException ignored) {
            // Even if the message fails, return to the login screen.
        }
        ScreenManager.getInstance().setCurrentUser(null);
        ScreenManager.getInstance().setScreen(new LoginView());
    }
}
