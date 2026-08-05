package client.ui;

import client.events.ServerMessageEvent;
import client.ui.exam.ExamReleaseSession;
import common.entities.Exam;
import common.entities.ExamRelease;
import common.entities.User;
import common.network.ExamExecutionSummary;
import common.network.Message;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import org.greenrobot.eventbus.Subscribe;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** Teacher release screen (scenarios 5, 7). */
public class ExamReleaseView extends AbstractScreenUI {

    private static final String FXML_PATH = "/fxml/ExamReleaseView.fxml";
    private static final DateTimeFormatter CLOSE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ExamReleaseSession session = new ExamReleaseSession();
    /** Suppresses selection listeners while ListViews are rebound programmatically. */
    private boolean syncingLists;

    @FXML private ListView<Exam> approvedList;
    @FXML private ListView<ExamRelease> releaseList;
    @FXML private TextField codeField, openTimeField, closeTimeField, extendMinutesField;
    @FXML private DatePicker openDatePicker, closeDatePicker;
    @FXML private TextArea summaryArea;
    @FXML private Label statusLabel, selectedExamLabel, closePreviewLabel;
    @FXML private Button releaseButton, suggestCloseButton;

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
        approvedList.setCellFactory(lv -> new ExamCell());
        releaseList.setCellFactory(lv -> new ReleaseCell());
        approvedList.getSelectionModel().selectedItemProperty()
                .addListener((o, w, n) -> {
                    if (syncingLists) return;
                    onExamSelected(n);
                });
        releaseList.getSelectionModel().selectedItemProperty()
                .addListener((o, w, n) -> {
                    if (syncingLists) return;
                    onReleaseSelected(n);
                });
        openDatePicker.valueProperty().addListener((o, w, n) -> refreshCloseHint());
        openTimeField.textProperty().addListener((o, w, n) -> refreshCloseHint());
        refreshSelectedExamLabel(null);
        refreshCloseHint();
    }

    @Override
    protected void onShown() {
        send(session.requestApprovedExams());
        send(session.requestReleasedExams());
        refreshStatus();
    }

    @FXML private void onBackToMenu() { goHome(); }
    @FXML private void onRefreshApproved() { send(session.requestApprovedExams()); refreshStatus(); }
    @FXML private void onRefreshReleases() { send(session.requestReleasedExams()); refreshStatus(); }

    @FXML
    private void onRelease() {
        Exam exam = session.getSelectedExam();
        if (exam == null) {
            alert("Select an approved exam.");
            return;
        }
        LocalDateTime open = parseOpenDateTime();
        LocalDateTime close = parseCloseDateTime();
        if (open == null) {
            alert("Enter a valid open date and time (HH:mm).");
            return;
        }
        if (close == null) {
            alert("Enter a valid close date and time (HH:mm).");
            return;
        }
        Message msg = session.requestRelease(
                exam.getId(), codeField.getText(), open, close);
        if (msg == null) {
            alert(session.getLastError());
            return;
        }
        send(msg);
        refreshStatus();
    }

    @FXML
    private void onSuggestClose() {
        Exam exam = session.getSelectedExam();
        LocalDateTime open = parseOpenDateTime();
        if (exam == null || open == null || exam.getDurationMinutes() <= 0) {
            alert("Select an exam and enter a valid open date/time first.");
            return;
        }
        LocalDateTime suggested = ExamReleaseSession.suggestClose(open, exam.getDurationMinutes());
        if (suggested == null) return;
        if (closeDatePicker != null) closeDatePicker.setValue(suggested.toLocalDate());
        if (closeTimeField != null) {
            closeTimeField.setText(String.format("%02d:%02d",
                    suggested.getHour(), suggested.getMinute()));
        }
        refreshCloseHint();
    }

    @FXML
    private void onExtend() {
        ExamRelease release = session.getSelectedRelease();
        if (release == null) {
            alert("Select a release.");
            return;
        }
        int minutes;
        try {
            minutes = Integer.parseInt(extendMinutesField.getText().trim());
        } catch (NumberFormatException e) {
            alert("Enter extension minutes (1–180).");
            return;
        }
        Message msg = session.requestExtend(release.getId(), minutes);
        if (msg == null) {
            alert(session.getLastError());
            return;
        }
        send(msg);
        refreshStatus();
    }

    private void onExamSelected(Exam exam) {
        session.setSelectedExam(exam);
        refreshSelectedExamLabel(exam);
        releaseButton.setDisable(exam == null);
        if (suggestCloseButton != null) suggestCloseButton.setDisable(exam == null);
        if (exam != null) {
            codeField.clear();
        }
        refreshCloseHint();
    }

    private void onReleaseSelected(ExamRelease release) {
        ExamRelease previous = session.getSelectedRelease();
        if (release != null && previous != null && previous.getId() == release.getId()) {
            return;
        }
        session.setSelectedRelease(release);
        if (release == null) {
            summaryArea.clear();
            return;
        }
        Message msg = session.requestExecutionSummary(release.getId());
        if (msg != null) send(msg);
        refreshStatus();
    }

    @Subscribe
    public void onServerMessage(ServerMessageEvent event) {
        Integer keepReleaseId = session.getSelectedRelease() != null
                ? session.getSelectedRelease().getId() : null;
        Integer keepExamId = session.getSelectedExam() != null
                ? session.getSelectedExam().getId() : null;

        session.onServerMessage(event.getMessage());
        if (event.getMessage().getCommand() == Message.Command.ERROR) {
            alert(session.getLastError());
        }

        syncingLists = true;
        try {
            approvedList.getItems().setAll(session.getApprovedExams());
            releaseList.getItems().setAll(session.getReleases());

            if (keepExamId != null) {
                for (Exam e : approvedList.getItems()) {
                    if (e.getId() == keepExamId) {
                        approvedList.getSelectionModel().select(e);
                        break;
                    }
                }
            }
            if (keepReleaseId != null) {
                for (ExamRelease r : releaseList.getItems()) {
                    if (r.getId() == keepReleaseId) {
                        releaseList.getSelectionModel().select(r);
                        break;
                    }
                }
            }
        } finally {
            syncingLists = false;
        }

        ExamExecutionSummary summary = session.getExecutionSummary();
        if (summary != null) {
            summaryArea.setText(formatSummary(summary));
        }
        refreshSelectedExamLabel(session.getSelectedExam());
        releaseButton.setDisable(session.getSelectedExam() == null);
        if (suggestCloseButton != null) {
            suggestCloseButton.setDisable(session.getSelectedExam() == null);
        }
        refreshCloseHint();
        refreshStatus();
    }

    private void refreshSelectedExamLabel(Exam exam) {
        if (selectedExamLabel == null) return;
        if (exam == null) {
            selectedExamLabel.setText("Selected: (none)");
            return;
        }
        selectedExamLabel.setText("Selected: " + exam.getTitle()
                + " (" + exam.getDurationMinutes() + " min)");
    }

    private void refreshCloseHint() {
        if (closePreviewLabel == null) return;
        Exam exam = session.getSelectedExam();
        LocalDateTime open = parseOpenDateTime();
        if (exam == null || open == null || exam.getDurationMinutes() <= 0) {
            closePreviewLabel.setText("Tip: set close independently of allotted duration");
            return;
        }
        LocalDateTime suggested = ExamReleaseSession.suggestClose(open, exam.getDurationMinutes());
        closePreviewLabel.setText(suggested == null ? "—"
                : "Suggested close: " + CLOSE_FMT.format(suggested)
                + "  (open + " + exam.getDurationMinutes() + " min)");
    }

    private LocalDateTime parseOpenDateTime() {
        return parseDateTime(openDatePicker, openTimeField);
    }

    private LocalDateTime parseCloseDateTime() {
        return parseDateTime(closeDatePicker, closeTimeField);
    }

    private static LocalDateTime parseDateTime(DatePicker datePicker, TextField timeField) {
        LocalDate date = datePicker == null ? null : datePicker.getValue();
        String timeText = timeField == null ? null : timeField.getText();
        if (date == null || timeText == null || timeText.isBlank()) return null;
        try {
            LocalTime time = LocalTime.parse(timeText.trim());
            return LocalDateTime.of(date, time);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String formatSummary(ExamExecutionSummary s) {
        return "Exam: " + s.getExamTitle() + " (id " + s.getExamId() + ")\n"
                + "Window: " + s.getOpenTime() + " → " + s.getCloseTime() + "\n"
                + "Duration: " + s.getAllocatedMinutes() + " min\n"
                + "Started: " + s.getStartedCount()
                + "  Submitted: " + s.getSubmittedCount()
                + "  Timed out: " + s.getTimedOutCount();
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

    private final class ExamCell extends ListCell<Exam> {
        @Override
        protected void updateItem(Exam exam, boolean empty) {
            super.updateItem(exam, empty);
            if (empty || exam == null) {
                setText(null);
                return;
            }
            String text = exam.getId() + " · " + exam.getTitle()
                    + " (" + exam.getDurationMinutes() + " min)";
            if (session.isExamAlreadyReleased(exam.getId())) {
                text += " · already released";
            }
            setText(text);
        }
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
            setText(title + " · code " + r.getExecutionCode() + " · Release " + r.getId());
        }
    }
}
