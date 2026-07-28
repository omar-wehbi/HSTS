package client.ui;

import client.events.ServerMessageEvent;
import client.ui.exam.ExamApprovalSession;
import client.ui.exam.ExamStatusLabel;
import common.entities.Exam;
import common.entities.User;
import common.network.Message;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import org.greenrobot.eventbus.Subscribe;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Coordinator approval screen (scenario 4).
 */
public class ExamApprovalView extends AbstractScreenUI {

    private static final String FXML_PATH = "/fxml/ExamApprovalView.fxml";

    private final ExamApprovalSession session = new ExamApprovalSession();

    @FXML private ListView<Exam> listView;
    @FXML private Label countBadge, detailTitle, idBadge, statusLabel;
    @FXML private TextArea detailArea, reasonField;
    @FXML private Button approveButton, rejectButton;

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
        send(session.requestPending());
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
        send(session.requestPending());
        refreshStatus();
    }

    @FXML
    private void onApprove() {
        Message msg = session.requestApprove();
        if (msg == null) {
            alert(session.getLastError());
            return;
        }
        send(msg);
        refreshStatus();
    }

    @FXML
    private void onReject() {
        Message msg = session.requestReject(reasonField.getText());
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
        listView.getItems().setAll(session.getPending());
        countBadge.setText(session.getPending().size() + " pending");
        showDetail(listView.getSelectionModel().getSelectedItem());
        refreshStatus();
    }

    private void showDetail(Exam exam) {
        session.setSelected(exam);
        if (exam == null) {
            detailTitle.setText("Select an exam");
            idBadge.setText("");
            detailArea.clear();
            approveButton.setDisable(true);
            rejectButton.setDisable(true);
            return;
        }
        detailTitle.setText(exam.getTitle());
        idBadge.setText(ExamStatusLabel.displayId(exam) + " · v" + exam.getVersion());
        StringBuilder sb = new StringBuilder();
        sb.append("Duration: ").append(exam.getDurationMinutes()).append(" min\n");
        sb.append("Points: ").append(exam.getTotalPoints()).append(" / 100\n");
        sb.append("Questions: ").append(exam.getQuestions().size()).append("\n\n");
        if (exam.getStudentInstructions() != null) {
            sb.append("Student instructions:\n").append(exam.getStudentInstructions()).append("\n\n");
        }
        if (exam.getTeacherNotes() != null) {
            sb.append("Teacher notes:\n").append(exam.getTeacherNotes()).append("\n");
        }
        detailArea.setText(sb.toString());
        approveButton.setDisable(false);
        rejectButton.setDisable(false);
    }

    private void refreshStatus() {
        statusLabel.setText(session.getStatusText());
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
        if (text == null || text.isBlank()) return;
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
            sub.setText(exam.getDurationMinutes() + " min · " + exam.getTotalPoints() + " pts");
            setGraphic(box);
        }
    }
}
