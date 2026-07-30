package client.ui;

import client.events.ServerMessageEvent;
import client.ui.exam.GradeExamsSession;
import common.entities.ExamRelease;
import common.entities.ExamSession;
import common.entities.Grade;
import common.entities.User;
import common.network.ExamExecutionSummary;
import common.network.Message;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import org.greenrobot.eventbus.Subscribe;

import java.io.IOException;
import java.io.UncheckedIOException;

/** Teacher grading screen (scenario 8). */
public class GradeExamsView extends AbstractScreenUI {

    private static final String FXML_PATH = "/fxml/GradeExamsView.fxml";
    private final GradeExamsSession session = new GradeExamsSession();

    /** Suppresses selection listeners while ListViews are rebound programmatically. */
    private boolean syncingLists;

    @FXML private ListView<ExamRelease> releaseList;
    @FXML private ListView<ExamSession> sessionsList;
    @FXML private ListView<Grade> gradesList;
    @FXML private TextArea summaryArea, commentField, justificationField;
    @FXML private TextField overrideScoreField;
    @FXML private Label statusLabel;

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
        sessionsList.setCellFactory(lv -> new SessionCell());
        gradesList.setCellFactory(lv -> new GradeCell());
        releaseList.getSelectionModel().selectedItemProperty()
                .addListener((o, w, n) -> {
                    if (syncingLists) return;
                    onReleaseSelected(n);
                });
        sessionsList.getSelectionModel().selectedItemProperty()
                .addListener((o, w, n) -> {
                    if (syncingLists) return;
                    session.setSelectedSession(n);
                });
        gradesList.getSelectionModel().selectedItemProperty()
                .addListener((o, w, n) -> {
                    if (syncingLists) return;
                    session.setSelectedGrade(n);
                });
    }

    @Override
    protected void onShown() {
        send(session.requestReleasedExams());
        refreshStatus();
    }

    @FXML private void onBackToMenu() { goHome(); }
    @FXML private void onRefreshReleases() { send(session.requestReleasedExams()); refreshStatus(); }

    @FXML
    private void onGradeAuto() {
        ExamSession selected = session.getSelectedSession();
        if (selected == null) {
            alert("Select a session from the list.");
            return;
        }
        Message msg = session.requestGradeAuto(selected.getId());
        if (msg == null) {
            alert(session.getLastError());
            return;
        }
        send(msg);
        refreshStatus();
    }

    @FXML
    private void onApprove() {
        Grade g = session.getSelectedGrade();
        if (g == null) {
            alert("Grade a session or select a grade first.");
            return;
        }
        Message msg = session.requestApprove(g.getId(), commentField.getText());
        if (msg == null) {
            alert(session.getLastError());
            return;
        }
        send(msg);
        refreshStatus();
    }

    @FXML
    private void onOverride() {
        Grade g = session.getSelectedGrade();
        if (g == null) {
            alert("Grade a session or select a grade first.");
            return;
        }
        int score;
        try {
            score = Integer.parseInt(overrideScoreField.getText().trim());
        } catch (NumberFormatException e) {
            alert("Enter override score 0–100.");
            return;
        }
        Message msg = session.requestOverride(g.getId(), score, justificationField.getText());
        if (msg == null) {
            alert(session.getLastError());
            return;
        }
        send(msg);
        refreshStatus();
    }

    private void onReleaseSelected(ExamRelease release) {
        ExamRelease previous = session.getSelectedRelease();
        if (release != null && previous != null && previous.getId() == release.getId()) {
            return;
        }
        session.setSelectedRelease(release);
        if (release == null) {
            summaryArea.clear();
            sessionsList.getItems().clear();
            gradesList.getItems().clear();
            return;
        }
        Message summary = session.requestExecutionSummary(release.getId());
        if (summary != null) send(summary);
        Message sessions = session.requestSessions(release.getId());
        if (sessions != null) send(sessions);
    }

    @Subscribe
    public void onServerMessage(ServerMessageEvent event) {
        Integer keepReleaseId = session.getSelectedRelease() != null
                ? session.getSelectedRelease().getId() : null;
        Integer keepSessionId = session.getSelectedSession() != null
                ? session.getSelectedSession().getId() : null;
        Integer keepGradeId = session.getSelectedGrade() != null
                ? session.getSelectedGrade().getId() : null;
        boolean wasAwaitingSessions = session.isAwaitingSessions();

        session.onServerMessage(event.getMessage());
        if (event.getMessage().getCommand() == Message.Command.ERROR) {
            alert(session.getLastError());
        }

        // After sessions load, fetch grades for this release (avoids List payload ambiguity).
        if (wasAwaitingSessions && !session.isAwaitingSessions()
                && keepReleaseId != null
                && event.getMessage().getCommand() == Message.Command.SUCCESS) {
            Message grades = session.requestGrades(keepReleaseId);
            if (grades != null) send(grades);
        }

        syncingLists = true;
        try {
            releaseList.getItems().setAll(session.getReleases());
            sessionsList.getItems().setAll(session.getSessions());
            gradesList.getItems().setAll(session.getGrades());

            if (keepReleaseId != null) {
                for (ExamRelease r : releaseList.getItems()) {
                    if (r.getId() == keepReleaseId) {
                        releaseList.getSelectionModel().select(r);
                        break;
                    }
                }
            }
            if (keepSessionId != null) {
                for (ExamSession s : sessionsList.getItems()) {
                    if (s.getId() == keepSessionId) {
                        sessionsList.getSelectionModel().select(s);
                        break;
                    }
                }
            }
            if (keepGradeId != null) {
                for (Grade g : gradesList.getItems()) {
                    if (g.getId() == keepGradeId) {
                        gradesList.getSelectionModel().select(g);
                        break;
                    }
                }
            } else if (session.getSelectedGrade() != null) {
                Grade g = session.getSelectedGrade();
                for (Grade item : gradesList.getItems()) {
                    if (item.getId() == g.getId()) {
                        gradesList.getSelectionModel().select(item);
                        break;
                    }
                }
            }
        } finally {
            syncingLists = false;
        }

        ExamExecutionSummary summary = session.getExecutionSummary();
        if (summary != null) {
            String title = summary.getExamTitle() == null ? "" : summary.getExamTitle() + "\n";
            summaryArea.setText(title
                    + "Started: " + summary.getStartedCount()
                    + "  Submitted: " + summary.getSubmittedCount()
                    + "  Timed out: " + summary.getTimedOutCount());
        }
        refreshStatus();
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
            if (empty || r == null) {
                setText(null);
                return;
            }
            String title = r.getExamTitle() == null || r.getExamTitle().isBlank()
                    ? "Exam #" + r.getExamId()
                    : r.getExamTitle();
            setText(title + " · code " + r.getExecutionCode());
        }
    }

    private static final class SessionCell extends ListCell<ExamSession> {
        @Override
        protected void updateItem(ExamSession s, boolean empty) {
            super.updateItem(s, empty);
            setText(empty || s == null ? null
                    : "Session " + s.getId()
                    + " · student " + s.getStudentId()
                    + " · " + s.getStatus());
        }
    }

    private static final class GradeCell extends ListCell<Grade> {
        @Override
        protected void updateItem(Grade g, boolean empty) {
            super.updateItem(g, empty);
            setText(empty || g == null ? null
                    : "Grade " + g.getId() + " · session " + g.getSessionId()
                    + " · " + g.getAutoScore() + " pts (" + g.getStatus() + ")");
        }
    }
}
