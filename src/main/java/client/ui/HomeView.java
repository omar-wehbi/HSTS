package client.ui;

import common.entities.User;
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
 * <p>Each role sees a different set of actions. Features owned by other team
 * members appear as disabled "coming soon" entries for now; the Teacher's
 * "Question Bank" links to the working {@link QuestionsView}.
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
        switch (user.getRole()) {
            case TEACHER:
                buttons.add(navButton("Question Bank",
                        () -> ScreenManager.getInstance().setScreen(new QuestionsView())));
                buttons.add(soon("Build Exams"));
                buttons.add(soon("Grade Exams"));
                buttons.add(soon("Exam Results"));
                break;
            case COORDINATOR:
                buttons.add(soon("Approve Exams"));
                break;
            case PRINCIPAL:
                buttons.add(soon("View Data"));
                buttons.add(soon("Reports"));
                break;
            case STUDENT:
                buttons.add(soon("Take Exam"));
                buttons.add(soon("My Grades"));
                buttons.add(soon("Study Bot"));
                break;
        }
        return buttons;
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
