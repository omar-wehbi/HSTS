package client.ui;

import client.events.ServerMessageEvent;
import client.ui.exam.StudentGradesSession;
import common.entities.User;
import common.network.CheckedAnswer;
import common.network.CheckedExamResult;
import common.network.Message;
import common.network.StudentResultSummary;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.stage.FileChooser;
import org.greenrobot.eventbus.Subscribe;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;

/** Student grades screen (scenario 9). */
public class StudentGradesView extends AbstractScreenUI {

    private static final String FXML_PATH = "/fxml/StudentGradesView.fxml";
    private final StudentGradesSession session = new StudentGradesSession();

    @FXML private ListView<StudentResultSummary> resultsList;
    @FXML private Label detailTitle, statusLabel;
    @FXML private TextArea detailArea;

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
        resultsList.setCellFactory(lv -> new ResultCell());
        resultsList.getSelectionModel().selectedItemProperty()
                .addListener((o, w, n) -> onResultSelected(n));
    }

    @Override
    protected void onShown() {
        send(session.requestResults());
        refreshStatus();
    }

    @FXML private void onBackToMenu() { goHome(); }
    @FXML private void onRefresh() { send(session.requestResults()); refreshStatus(); }

    @FXML
    private void onSaveCopy() {
        byte[] pdf = session.exportCheckedExamPdf();
        if (pdf == null) {
            alert(session.getLastError() != null ? session.getLastError() : "Nothing to export.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save checked exam copy");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        chooser.setInitialFileName("checked-exam.pdf");
        var file = chooser.showSaveDialog(detailArea.getScene().getWindow());
        if (file == null) return;
        try {
            Files.write(file.toPath(), pdf);
            statusLabel.setText("Saved copy to " + file.getName());
        } catch (IOException e) {
            alert("Could not save file: " + e.getMessage());
        }
    }

    private void onResultSelected(StudentResultSummary summary) {
        session.setSelected(summary);
        if (summary == null) {
            detailTitle.setText("Select a result");
            detailArea.clear();
            return;
        }
        detailTitle.setText(summary.getExamTitle() + " — " + summary.getScore() + " pts");
        Message msg = session.requestCheckedExam(summary.getGradeId());
        if (msg != null) send(msg);
    }

    @Subscribe
    public void onServerMessage(ServerMessageEvent event) {
        session.onServerMessage(event.getMessage());
        if (event.getMessage().getCommand() == Message.Command.ERROR) {
            alert(session.getLastError());
        }
        resultsList.getItems().setAll(session.getResults());
        CheckedExamResult checked = session.getCheckedExam();
        if (checked != null) {
            detailArea.setText(formatChecked(checked));
        }
        refreshStatus();
    }

    private static String formatChecked(CheckedExamResult r) {
        StringBuilder sb = new StringBuilder();
        sb.append("Score: ").append(r.getScore())
                .append(" (").append(r.getGradeStatus()).append(")\n");
        if (r.getTeacherComment() != null && !r.getTeacherComment().isBlank()) {
            sb.append("Teacher comment: ").append(r.getTeacherComment()).append("\n");
        }
        if (r.getOverrideJustification() != null && !r.getOverrideJustification().isBlank()) {
            sb.append("Override note: ").append(r.getOverrideJustification()).append("\n");
        }
        sb.append("\n");
        for (CheckedAnswer a : r.getAnswers()) {
            sb.append("Q").append(a.getQuestionId()).append(": ")
                    .append(a.getQuestionText()).append("\n");
            sb.append("  Your answer: ").append(a.getSelectedAnswer())
                    .append("  Correct: ").append(a.getCorrectAnswer())
                    .append(a.isCorrect() ? " ✓" : " ✗")
                    .append(" (").append(a.getPoints()).append(" pts)\n\n");
        }
        return sb.toString().trim();
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

    private static final class ResultCell extends ListCell<StudentResultSummary> {
        @Override
        protected void updateItem(StudentResultSummary s, boolean empty) {
            super.updateItem(s, empty);
            setText(empty || s == null ? null
                    : s.getExamTitle() + " · " + s.getScore() + " pts");
        }
    }
}
