package client.ui;

import client.events.ServerMessageEvent;
import client.ui.exam.StudyBotStudentSession;
import common.entities.Course;
import common.entities.User;
import common.network.Message;
import common.network.StudyBotAnswer;
import common.network.StudyBotView;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import org.greenrobot.eventbus.Subscribe;

import java.io.IOException;
import java.io.UncheckedIOException;

/** Student study bot screen (scenarios 13–14). */
public class StudyBotStudentView extends AbstractScreenUI {

    private static final String FXML_PATH = "/fxml/StudyBotStudentView.fxml";
    private final StudyBotStudentSession session = new StudyBotStudentSession();

    @FXML private ComboBox<Course> courseCombo;
    @FXML private TextField courseIdField;
    @FXML private ListView<StudyBotAnswer> historyList;
    @FXML private TextArea questionField, answerArea;
    @FXML private Label botStatusLabel, statusLabel;

    @Override
    public Parent render() {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(FXML_PATH));
        loader.setController(this);
        try {
            return loader.load();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load " + FXML_PATH, e);
        }
    }

    @FXML
    private void initialize() {
        historyList.setCellFactory(lv -> new HistoryCell());
        courseCombo.getSelectionModel().selectedItemProperty()
                .addListener((o, w, c) -> {
                    if (c != null) {
                        courseIdField.setText(String.valueOf(c.getId()));
                        session.setSelectedCourseId(c.getId());
                    }
                });
    }

    @Override
    protected void onShown() {
        send(session.requestCourses());
        send(session.requestHistory());
        refreshStatus();
    }

    @FXML private void onBackToMenu() { goHome(); }
    @FXML private void onRefreshHistory() { send(session.requestHistory()); refreshStatus(); }

    @FXML
    private void onLoadBot() {
        int courseId = resolveCourseId();
        Message msg = session.requestStudyBot(courseId);
        if (msg == null) {
            alert(session.getLastError());
            return;
        }
        send(msg);
        refreshStatus();
    }

    @FXML
    private void onAsk() {
        int courseId = resolveCourseId();
        Message msg = session.requestAsk(courseId, questionField.getText());
        if (msg == null) {
            alert(session.getLastError());
            return;
        }
        send(msg);
        refreshStatus();
    }

    @Subscribe
    public void onServerMessage(ServerMessageEvent event) {
        session.onServerMessage(event.getMessage());
        if (event.getMessage().getCommand() == Message.Command.ERROR) {
            alert(session.getLastError());
        }
        courseCombo.getItems().setAll(session.getCourses());
        historyList.getItems().setAll(session.getHistory());
        StudyBotView bot = session.getBotView();
        if (bot != null) {
            botStatusLabel.setText(bot.getName() + " — "
                    + (bot.isAvailable() ? "available" : "unavailable")
                    + " · " + bot.getSourceCount() + " sources");
        }
        StudyBotAnswer last = session.getLastAnswer();
        if (last != null) {
            answerArea.setText(last.getAnswer() != null ? last.getAnswer() : "(no answer)");
        }
        refreshStatus();
    }

    private int resolveCourseId() {
        Course selected = courseCombo.getSelectionModel().getSelectedItem();
        if (selected != null) return selected.getId();
        try {
            return Integer.parseInt(courseIdField.getText().trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void refreshStatus() {
        if (statusLabel != null) statusLabel.setText(session.getStatusText());
    }

    private void send(Message m) {
        if (m == null) return;
        try {
            client().send(m);
        } catch (IOException e) {
            statusLabel.setText("Send failed: " + e.getMessage());
        }
    }

    private void goHome() {
        User user = ScreenManager.getInstance().getCurrentUser();
        ScreenManager.getInstance().setScreen(user != null ? new HomeView(user) : new LoginView());
    }

    private void alert(String text) {
        if (text == null || text.isBlank()) return;
        Alert a = new Alert(Alert.AlertType.WARNING, text);
        a.setHeaderText(null);
        a.showAndWait();
    }

    private static final class HistoryCell extends ListCell<StudyBotAnswer> {
        @Override
        protected void updateItem(StudyBotAnswer a, boolean empty) {
            super.updateItem(a, empty);
            setText(empty || a == null ? null
                    : a.getQuestion() + " → " + (a.getAnswer() != null ? a.getAnswer() : a.getStatus()));
        }
    }
}
