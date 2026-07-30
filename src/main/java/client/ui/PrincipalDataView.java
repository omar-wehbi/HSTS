package client.ui;

import client.events.ServerMessageEvent;
import client.ui.exam.PrincipalDataSession;
import common.entities.User;
import common.network.Message;
import common.network.PrincipalData;
import common.network.PrincipalDataItem;
import common.network.PrincipalReadOnlyData;
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
/** Principal data screen (scenario 11). */
public class PrincipalDataView extends AbstractScreenUI {

    private static final String FXML_PATH = "/fxml/PrincipalDataView.fxml";
    private final PrincipalDataSession session = new PrincipalDataSession();

    @FXML private ListView<PrincipalDataItem> teachersList, coursesList, studentsList;
    @FXML private Label gradedLabel, statusLabel;
    @FXML private TextArea countsArea;

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
        var factory = (javafx.util.Callback<ListView<PrincipalDataItem>, ListCell<PrincipalDataItem>>)
                lv -> new ItemCell();
        teachersList.setCellFactory(factory);
        coursesList.setCellFactory(factory);
        studentsList.setCellFactory(factory);
    }

    @Override
    protected void onShown() {
        send(session.requestPrincipalData());
        send(session.requestReadOnlyData());
        refreshStatus();
    }

    @FXML private void onBackToMenu() { goHome(); }
    @FXML private void onRefresh() {
        send(session.requestPrincipalData());
        send(session.requestReadOnlyData());
        refreshStatus();
    }

    @Subscribe
    public void onServerMessage(ServerMessageEvent event) {
        session.onServerMessage(event.getMessage());
        if (event.getMessage().getCommand() == Message.Command.ERROR) {
            alert(session.getLastError());
        }
        PrincipalData data = session.getPrincipalData();
        if (data != null) {
            teachersList.getItems().setAll(data.getTeachers());
            coursesList.getItems().setAll(data.getCourses());
            studentsList.getItems().setAll(data.getStudents());
            gradedLabel.setText("Graded attempts: " + data.getGradedAttempts());
        }
        PrincipalReadOnlyData ro = session.getReadOnlyData();
        if (ro != null) {
            countsArea.setText(formatCounts(ro));
        }
        refreshStatus();
    }

    private static String formatCounts(PrincipalReadOnlyData ro) {
        StringBuilder sb = new StringBuilder();
        sb.append("Questions: ").append(ro.getQuestions().size()).append("\n");
        sb.append("Exams: ").append(ro.getExams().size()).append("\n");
        sb.append("Releases: ").append(ro.getReleases().size()).append("\n");
        sb.append("Sessions: ").append(ro.getSessions().size()).append("\n");
        sb.append("Results: ").append(ro.getResults().size()).append("\n");
        return sb.toString();
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

    private static final class ItemCell extends ListCell<PrincipalDataItem> {
        @Override
        protected void updateItem(PrincipalDataItem item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? null : item.getId() + " · " + item.getName());
        }
    }
}
