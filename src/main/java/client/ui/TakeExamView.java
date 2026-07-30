package client.ui;

import client.events.ServerMessageEvent;
import client.ui.exam.TakeExamSession;
import common.entities.User;
import common.network.ExamForm;
import common.network.ExamFormQuestion;
import common.network.Message;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.greenrobot.eventbus.Subscribe;
import javafx.util.Duration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/** Student take-exam screen (scenario 6). */
public class TakeExamView extends AbstractScreenUI {

    private static final String FXML_PATH = "/fxml/TakeExamView.fxml";
    private final TakeExamSession session = new TakeExamSession();
    private final Map<Integer, ToggleGroup> questionGroups = new HashMap<>();
    private Timeline timer;

    @FXML private TextField codeField, idField;
    @FXML private Label examTitleLabel, instructionsLabel, timerLabel;
    @FXML private Label confirmationLabel, statusLabel;
    @FXML private VBox questionsBox;
    @FXML private HBox startBox, actionBox;

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

    @Override
    protected void onShown() {
        refreshStatus();
    }

    @FXML private void onBackToMenu() {
        stopTimer();
        User user = ScreenManager.getInstance().getCurrentUser();
        ScreenManager.getInstance().setScreen(user != null ? new HomeView(user) : new LoginView());
    }

    @FXML
    private void onStart() {
        Message msg = session.requestStart(codeField.getText(), idField.getText());
        if (msg == null) {
            alert(session.getLastError());
            return;
        }
        send(msg);
        refreshStatus();
    }

    @FXML
    private void onSave() {
        Message msg = session.requestSave();
        if (msg == null) {
            alert(session.getLastError());
            return;
        }
        send(msg);
        refreshStatus();
    }

    @FXML
    private void onSubmit() {
        Message msg = session.requestSubmit();
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
        ExamForm form = session.getExamForm();
        if (form != null && questionsBox.getChildren().isEmpty()) {
            showExamForm(form);
        }
        if (session.isSubmitted()) {
            showSubmitted();
        }
        refreshStatus();
    }

    private void showExamForm(ExamForm form) {
        startBox.setVisible(false);
        startBox.setManaged(false);
        actionBox.setVisible(true);
        actionBox.setManaged(true);
        examTitleLabel.setText(form.getTitle());
        instructionsLabel.setText(form.getInstructions() != null ? form.getInstructions() : "");
        questionsBox.getChildren().clear();
        questionGroups.clear();
        for (ExamFormQuestion q : session.getQuestions()) {
            questionsBox.getChildren().add(buildQuestionBlock(q));
        }
        startTimer();
    }

    private VBox buildQuestionBlock(ExamFormQuestion q) {
        Label title = new Label(q.getPosition() + ". " + q.getText()
                + " (" + q.getPoints() + " pts)");
        title.getStyleClass().add("q-title");
        title.setWrapText(true);
        ToggleGroup group = new ToggleGroup();
        questionGroups.put(q.getQuestionId(), group);
        Integer saved = session.getAnswers().get(q.getQuestionId());
        VBox box = new VBox(6, title);
        for (int i = 1; i <= 4; i++) {
            final int option = i;
            RadioButton rb = new RadioButton(optionText(q, option));
            rb.setToggleGroup(group);
            rb.setUserData(option);
            if (saved != null && saved == option) rb.setSelected(true);
            rb.selectedProperty().addListener((o, w, now) -> {
                if (now) session.recordAnswer(q.getQuestionId(), option);
            });
            box.getChildren().add(rb);
        }
        return box;
    }

    private static String optionText(ExamFormQuestion q, int i) {
        return switch (i) {
            case 1 -> "1. " + q.getAnswer1();
            case 2 -> "2. " + q.getAnswer2();
            case 3 -> "3. " + q.getAnswer3();
            case 4 -> "4. " + q.getAnswer4();
            default -> "";
        };
    }

    private void showSubmitted() {
        stopTimer();
        actionBox.setVisible(false);
        actionBox.setManaged(false);
        setQuestionsDisabled(true);
        confirmationLabel.setText("Your exam has been submitted. No further edits are allowed.");
        timerLabel.setText("Submitted");
    }

    private void setQuestionsDisabled(boolean disabled) {
        for (var node : questionsBox.getChildren()) {
            if (node instanceof VBox vb) {
                vb.setDisable(disabled);
            }
        }
    }

    private void startTimer() {
        stopTimer();
        timer = new Timeline(new KeyFrame(Duration.seconds(1), e -> updateTimer()));
        timer.setCycleCount(Timeline.INDEFINITE);
        timer.play();
        updateTimer();
    }

    private void updateTimer() {
        long secs = session.remainingSeconds(LocalDateTime.now());
        timerLabel.setText(formatRemaining(secs));
    }

    private static String formatRemaining(long secs) {
        long m = secs / 60;
        long s = secs % 60;
        return String.format("Time left: %d:%02d", m, s);
    }

    private void stopTimer() {
        if (timer != null) {
            timer.stop();
            timer = null;
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

    private void alert(String text) {
        if (text == null || text.isBlank()) return;
        Alert a = new Alert(Alert.AlertType.WARNING, text);
        a.setHeaderText(null);
        a.showAndWait();
    }
}
