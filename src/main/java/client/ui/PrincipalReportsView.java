package client.ui;

import client.events.ServerMessageEvent;
import client.ui.exam.PrincipalReportsSession;
import common.entities.User;
import common.network.Message;
import common.network.PrincipalReport;
import common.network.ReportDimension;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import org.greenrobot.eventbus.Subscribe;

import java.io.IOException;
import java.io.UncheckedIOException;

/** Principal reports screen (scenario 12). */
public class PrincipalReportsView extends AbstractScreenUI {

    private static final String FXML_PATH = "/fxml/PrincipalReportsView.fxml";
    private final PrincipalReportsSession session = new PrincipalReportsSession();

    @FXML private ComboBox<ReportDimension> dimensionCombo;
    @FXML private TextField entityIdsField;
    @FXML private TextArea reportArea;
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
        dimensionCombo.getItems().setAll(ReportDimension.values());
        dimensionCombo.getSelectionModel().select(ReportDimension.TEACHER);
    }

    @FXML private void onBackToMenu() { goHome(); }

    @FXML
    private void onGenerate() {
        ReportDimension dim = dimensionCombo.getSelectionModel().getSelectedItem();
        Message msg = session.requestReport(dim, entityIdsField.getText());
        if (msg == null) {
            alert(session.getLastError());
            return;
        }
        send(msg);
        refreshStatus();
    }

    @FXML
    private void onCopyReport() {
        String text = session.fullReportText();
        if (text == null || text.isBlank()) {
            alert("Generate a report first.");
            return;
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
        statusLabel.setText("Report copied to clipboard.");
    }

    @Subscribe
    public void onServerMessage(ServerMessageEvent event) {
        session.onServerMessage(event.getMessage());
        if (event.getMessage().getCommand() == Message.Command.ERROR) {
            alert(session.getLastError());
        }
        PrincipalReport report = session.getReport();
        if (report != null) {
            reportArea.setText(session.fullReportText());
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
}
