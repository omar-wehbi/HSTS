package client.ui;

import client.events.ServerMessageEvent;
import client.ui.exam.ExamListSession;
import client.ui.exam.ExamStatusLabel;
import common.entities.Exam;
import common.entities.User;
import common.network.Message;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import org.greenrobot.eventbus.Subscribe;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Teacher exam list (scenarios 3–4): browse {@code GET_MY_EXAMS}, edit drafts,
 * submit for coordinator approval, show rejection reasons.
 */
public class ExamListView extends AbstractScreenUI {

    private static final String FXML_PATH = "/fxml/ExamListView.fxml";

    private final ExamListSession session = new ExamListSession();

    @FXML private ListView<Exam> listView;
    @FXML private Label countBadge, detailTitle, idBadge, statusBadge, statusLabel;
    @FXML private TextArea detailArea;
    @FXML private Button editButton, deleteButton, submitButton, newButton;

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
        listView.setCellFactory(lv -> new ExamCell());
        listView.getSelectionModel().selectedItemProperty()
                .addListener((o, was, now) -> showDetail(now));
    }

    @Override
    protected void onShown() {
        send(session.requestMyExams());
        refreshStatus();
    }

    @FXML
    private void onBackToMenu() {
        User user = ScreenManager.getInstance().getCurrentUser();
        if (user != null) {
            ScreenManager.getInstance().setScreen(new HomeView(user));
        } else {
            ScreenManager.getInstance().setScreen(new LoginView());
        }
    }

    @FXML
    private void onRefresh() {
        send(session.requestMyExams());
        refreshStatus();
    }

    @FXML
    private void onNew() {
        ScreenManager.getInstance().setScreen(new ExamBuilderView(null));
    }

    @FXML
    private void onEdit() {
        Exam selected = session.getSelected();
        if (selected == null || !session.canEditSelected()) return;
        ScreenManager.getInstance().setScreen(new ExamBuilderView(selected));
    }

    @FXML
    private void onSubmit() {
        Message msg = session.requestSubmit();
        if (msg == null) {
            alert(session.getLastError());
            refreshStatus();
            return;
        }
        send(msg);
        refreshStatus();
    }

    @FXML
    private void onDelete() {
        Exam selected = session.getSelected();
        if (selected == null || !session.canDeleteSelected()) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete exam \"" + selected.getTitle() + "\"?\nThis cannot be undone.",
                ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.YES) {
                Message msg = session.requestDelete();
                if (msg == null) {
                    alert(session.getLastError());
                    return;
                }
                send(msg);
                refreshStatus();
            }
        });
    }

    @Subscribe
    public void onServerMessage(ServerMessageEvent event) {
        session.onServerMessage(event.getMessage());
        if (session.getLastError() != null
                && event.getMessage().getCommand() == Message.Command.ERROR) {
            alert(session.getLastError());
        }
        syncListFromSession();
        refreshStatus();
    }

    private void syncListFromSession() {
        Exam keep = session.getSelected();
        listView.getItems().setAll(session.getExams());
        countBadge.setText(session.getExams().size()
                + (session.getExams().size() == 1 ? " exam" : " exams"));
        if (keep != null) {
            for (Exam e : listView.getItems()) {
                if (e.getId() == keep.getId()) {
                    listView.getSelectionModel().select(e);
                    return;
                }
            }
        }
        showDetail(listView.getSelectionModel().getSelectedItem());
    }

    private void showDetail(Exam exam) {
        session.setSelected(exam);
        if (exam == null) {
            detailTitle.setText("Select an exam");
            idBadge.setText("");
            statusBadge.setText("");
            detailArea.clear();
            editButton.setDisable(true);
            deleteButton.setDisable(true);
            submitButton.setDisable(true);
            return;
        }
        detailTitle.setText(exam.getTitle());
        idBadge.setText(ExamStatusLabel.displayId(exam) + " · v" + exam.getVersion());
        statusBadge.setText(ExamStatusLabel.badge(exam.getStatus()));
        detailArea.setText(buildDetailText(exam));
        editButton.setDisable(!session.canEditSelected());
        deleteButton.setDisable(!session.canDeleteSelected());
        submitButton.setDisable(!session.canSubmitSelected());
    }

    private static String buildDetailText(Exam exam) {
        StringBuilder sb = new StringBuilder();
        sb.append(ExamStatusLabel.detail(exam)).append("\n\n");
        sb.append("Duration: ").append(exam.getDurationMinutes()).append(" minutes\n");
        sb.append("Points: ").append(exam.getTotalPoints()).append(" / 100\n");
        sb.append("Questions: ").append(exam.getQuestions().size()).append("\n\n");
        if (exam.getStudentInstructions() != null && !exam.getStudentInstructions().isBlank()) {
            sb.append("Student instructions:\n").append(exam.getStudentInstructions()).append("\n\n");
        }
        if (exam.getTeacherNotes() != null && !exam.getTeacherNotes().isBlank()) {
            sb.append("Teacher notes:\n").append(exam.getTeacherNotes()).append("\n");
        }
        return sb.toString();
    }

    private void refreshStatus() {
        if (statusLabel != null) {
            statusLabel.setText(session.getStatusText());
        }
    }

    private void send(Message m) {
        if (m == null) return;
        try {
            client().send(m);
        } catch (IOException e) {
            statusLabel.setText("Send failed: " + e.getMessage());
        }
    }

    private void alert(String text) {
        if (text == null) return;
        Alert a = new Alert(Alert.AlertType.WARNING, text);
        a.setHeaderText(null);
        a.showAndWait();
    }

    private final class ExamCell extends ListCell<Exam> {
        private final Label title = new Label();
        private final Label sub = new Label();
        private final VBox box = new VBox(3, title, sub);

        ExamCell() {
            title.getStyleClass().add("q-title");
            sub.getStyleClass().add("q-sub");
            title.setWrapText(true);
        }

        @Override
        protected void updateItem(Exam exam, boolean empty) {
            super.updateItem(exam, empty);
            if (empty || exam == null) {
                setGraphic(null);
                return;
            }
            title.setText(ExamStatusLabel.displayId(exam) + "  " + exam.getTitle());
            sub.setText(ExamStatusLabel.badge(exam.getStatus())
                    + " · " + exam.getDurationMinutes() + " min"
                    + " · " + exam.getTotalPoints() + " pts");
            setGraphic(box);
        }
    }
}
