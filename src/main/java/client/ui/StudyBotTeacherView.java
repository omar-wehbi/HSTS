package client.ui;

import client.events.ServerMessageEvent;
import client.ui.exam.StudyBotTeacherSession;
import common.entities.User;
import common.network.Message;
import common.network.StudyBotSourceView;
import common.network.StudyBotUsageReport;
import common.network.StudyBotView;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import org.greenrobot.eventbus.Subscribe;

import java.io.IOException;
import java.io.UncheckedIOException;

/** Teacher study bot management screen. */
public class StudyBotTeacherView extends AbstractScreenUI {

    private static final String FXML_PATH = "/fxml/StudyBotTeacherView.fxml";
    private final StudyBotTeacherSession session = new StudyBotTeacherSession();

    @FXML private TextField courseIdField, botNameField, sourceTitleField;
    @FXML private CheckBox includeBankCheck;
    @FXML private ListView<StudyBotSourceView> sourcesList;
    @FXML private TextArea sourceContentField, usageArea;
    @FXML private Label botInfoLabel, statusLabel;

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
        sourcesList.setCellFactory(lv -> new SourceCell());
    }

    @FXML private void onBackToMenu() { goHome(); }

    @FXML
    private void onCreate() {
        int courseId = parseCourseId();
        Message msg = session.requestCreate(courseId, botNameField.getText(),
                includeBankCheck.isSelected());
        if (msg == null) {
            alert(session.getLastError());
            return;
        }
        send(msg);
        refreshStatus();
    }

    @FXML
    private void onActivate() {
        sendAvailability(true);
    }

    @FXML
    private void onDeactivate() {
        sendAvailability(false);
    }

    @FXML
    private void onLoadBot() {
        int courseId = parseCourseId();
        Message msg = session.requestStudyBot(courseId);
        if (msg == null) {
            alert(session.getLastError());
            return;
        }
        send(msg);
        refreshStatus();
    }

    @FXML
    private void onAddSource() {
        int courseId = parseCourseId();
        Message msg = session.requestAddSource(courseId,
                sourceTitleField.getText(), sourceContentField.getText());
        if (msg == null) {
            alert(session.getLastError());
            return;
        }
        send(msg);
        refreshStatus();
    }

    @FXML
    private void onRefreshSources() {
        int courseId = parseCourseId();
        Message msg = session.requestSources(courseId);
        if (msg == null) {
            alert(session.getLastError());
            return;
        }
        send(msg);
        refreshStatus();
    }

    @FXML
    private void onDeleteSource() {
        StudyBotSourceView selected = sourcesList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            alert("Select a source.");
            return;
        }
        Message msg = session.requestDeleteSource(selected.getId());
        if (msg == null) {
            alert(session.getLastError());
            return;
        }
        send(msg);
        refreshStatus();
    }

    @FXML
    private void onLoadUsage() {
        int courseId = parseCourseId();
        Message msg = session.requestUsage(courseId);
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
        sourcesList.getItems().setAll(session.getSources());
        StudyBotView bot = session.getBotView();
        if (bot != null) {
            botInfoLabel.setText(bot.getName() + " · "
                    + (bot.isAvailable() ? "active" : "inactive")
                    + " · " + bot.getSourceCount() + " sources"
                    + (bot.isIncludeQuestionBank() ? " · includes bank" : ""));
        }
        StudyBotUsageReport usage = session.getUsageReport();
        if (usage != null) {
            usageArea.setText(formatUsage(usage));
        }
        refreshStatus();
    }

    private static String formatUsage(StudyBotUsageReport u) {
        StringBuilder sb = new StringBuilder();
        sb.append("Total questions: ").append(u.getTotalQuestions()).append("\n");
        sb.append("Unique students: ").append(u.getUniqueStudents()).append("\n\n");
        sb.append("Recent questions:\n");
        for (StudyBotUsageReport.Entry e : u.getRecentQuestions()) {
            sb.append("  · ").append(e.getQuestion())
                    .append(" (").append(e.getAskedAt()).append(")\n");
        }
        return sb.toString().trim();
    }

    private void sendAvailability(boolean available) {
        int courseId = parseCourseId();
        Message msg = session.requestSetAvailability(courseId, available);
        if (msg == null) {
            alert(session.getLastError());
            return;
        }
        send(msg);
        refreshStatus();
    }

    private int parseCourseId() {
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

    private static final class SourceCell extends ListCell<StudyBotSourceView> {
        @Override
        protected void updateItem(StudyBotSourceView s, boolean empty) {
            super.updateItem(s, empty);
            setText(empty || s == null ? null : s.getId() + " · " + s.getTitle());
        }
    }
}
