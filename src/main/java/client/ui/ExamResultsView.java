package client.ui;

import client.events.ServerMessageEvent;
import client.ui.exam.ExamResultsSession;
import common.entities.ExamRelease;
import common.entities.User;
import common.network.Message;
import common.network.TeacherExamResults;
import common.network.TeacherResultRow;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import org.greenrobot.eventbus.Subscribe;

import java.io.IOException;
import java.io.UncheckedIOException;

/** Teacher exam results (scenarios 9–10). */
public class ExamResultsView extends AbstractScreenUI {

    private static final String FXML_PATH = "/fxml/ExamResultsView.fxml";
    private final ExamResultsSession session = new ExamResultsSession();

    @FXML private ListView<ExamRelease> releaseList;
    @FXML private Label titleLabel, statsLabel, statusLabel;
    @FXML private TextArea rowsArea, histogramArea;

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
        releaseList.setCellFactory(lv -> new ReleaseCell());
        releaseList.getSelectionModel().selectedItemProperty()
                .addListener((o, w, n) -> onReleaseSelected(n));
    }

    @Override
    protected void onShown() {
        send(session.requestReleasedExams());
        refreshStatus();
    }

    @FXML private void onBackToMenu() { goHome(); }
    @FXML private void onRefresh() { send(session.requestReleasedExams()); refreshStatus(); }

    private void onReleaseSelected(ExamRelease release) {
        session.setSelectedRelease(release);
        if (release == null) {
            titleLabel.setText("Select a release");
            statsLabel.setText("");
            rowsArea.clear();
            histogramArea.clear();
            return;
        }
        Message msg = session.requestStatistics(release.getId());
        if (msg != null) send(msg);
    }

    @Subscribe
    public void onServerMessage(ServerMessageEvent event) {
        session.onServerMessage(event.getMessage());
        if (event.getMessage().getCommand() == Message.Command.ERROR) {
            alert(session.getLastError());
        }
        releaseList.getItems().setAll(session.getReleases());
        TeacherExamResults results = session.getResults();
        if (results != null) {
            titleLabel.setText(results.getExamTitle());
            statsLabel.setText(String.format(
                    "n=%d  mean=%.1f  median=%.1f  min=%s  max=%s",
                    results.getResultCount(), results.getMean(), results.getMedian(),
                    results.getMinimum(), results.getMaximum()));
            rowsArea.setText(formatRows(results));
            histogramArea.setText(ExamResultsSession.formatHistogram(results.getHistogram()));
        }
        refreshStatus();
    }

    private static String formatRows(TeacherExamResults results) {
        StringBuilder sb = new StringBuilder();
        for (TeacherResultRow row : results.getRows()) {
            sb.append("Student ").append(row.getStudentId())
                    .append(" · score ").append(row.getEffectiveScore())
                    .append(" · ").append(row.getGradeStatus())
                    .append("\n");
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

    private static final class ReleaseCell extends ListCell<ExamRelease> {
        @Override
        protected void updateItem(ExamRelease r, boolean empty) {
            super.updateItem(r, empty);
            setText(empty || r == null ? null : "Release " + r.getId() + " · " + r.getExecutionCode());
        }
    }
}
