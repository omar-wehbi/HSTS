package client.ui;

import client.events.ServerMessageEvent;
import client.ui.exam.AutoExamRequestBuilder;
import client.ui.exam.ExamBuilderSession;
import client.ui.exam.ExamFormValidator;
import common.entities.Course;
import common.entities.Exam;
import common.entities.ExamQuestion;
import common.entities.Question;
import common.entities.User;
import common.network.AutoExamRequest;
import common.network.AutoExamRequirement;
import common.network.Message;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import org.greenrobot.eventbus.Subscribe;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Manual + automatic exam builder (scenario 3).
 */
public class ExamBuilderView extends AbstractScreenUI {

    private static final String FXML_PATH = "/fxml/ExamBuilderView.fxml";

    private final ExamBuilderSession session = new ExamBuilderSession();
    private final Exam existing;
    private final List<AutoExamRequirement> autoRequirements = new ArrayList<>();

    @FXML private ComboBox<Course> courseBox;
    @FXML private ListView<Question> bankList;
    @FXML private ListView<ExamQuestion> selectedList;
    @FXML private TextField titleField, durationField, pointsField;
    @FXML private TextArea studentInstructions, teacherNotes;
    @FXML private Label pointsBadge, statusLabel, autoPointsLabel, autoStatusLabel;
    @FXML private Button saveButton, addButton;
    @FXML private TextField autoTopic, autoCount, autoPts;
    @FXML private ComboBox<String> autoDifficulty;
    @FXML private ListView<AutoExamRequirement> requirementsList;

    public ExamBuilderView(Exam existing) {
        this.existing = existing;
    }

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
        bankList.setCellFactory(lv -> new QuestionCell());
        selectedList.setCellFactory(lv -> new SelectedCell());
        requirementsList.setCellFactory(lv -> new RequirementCell());
        autoDifficulty.setItems(FXCollections.observableArrayList("EASY", "MEDIUM", "HARD"));

        User user = ScreenManager.getInstance().getCurrentUser();
        if (user != null) {
            session.setTeacherId(user.getId());
        }
        if (existing != null) {
            session.loadExam(existing);
            applySessionToForm();
        }
        refreshPointsBadge();
    }

    @Override
    protected void onShown() {
        send(session.requestCourses());
        if (session.getCourseId() > 0) {
            send(session.requestBank());
        }
        refreshStatus();
    }

    @FXML
    private void onBack() {
        ScreenManager.getInstance().setScreen(new ExamListView());
    }

    @FXML
    private void onLoadBank() {
        syncFormToSession();
        send(session.requestBank());
        refreshStatus();
    }

    @FXML
    private void onAddQuestion() {
        Question q = bankList.getSelectionModel().getSelectedItem();
        if (q == null) {
            alert("Select a question from the bank.");
            return;
        }
        int pts = parsePositiveInt(pointsField.getText(), 25);
        session.addQuestion(q, pts);
        if (session.getLastError() != null) {
            alert(session.getLastError());
        }
        selectedList.getItems().setAll(session.getSelectedQuestions());
        refreshPointsBadge();
    }

    @FXML
    private void onRemoveSelected() {
        int idx = selectedList.getSelectionModel().getSelectedIndex();
        session.removeQuestionAt(idx);
        selectedList.getItems().setAll(session.getSelectedQuestions());
        refreshPointsBadge();
    }

    @FXML
    private void onSave() {
        syncFormToSession();
        Message msg = session.requestSave();
        if (msg == null) {
            alert(session.getLastError());
            refreshStatus();
            return;
        }
        send(msg);
        refreshStatus();
    }

    @FXML
    private void onAddRequirement() {
        String topic = autoTopic.getText() == null ? "" : autoTopic.getText().trim();
        String difficulty = autoDifficulty.getValue();
        int count = parsePositiveInt(autoCount.getText(), 0);
        int pts = parsePositiveInt(autoPts.getText(), 0);
        if (topic.isEmpty() || difficulty == null || count <= 0 || pts <= 0) {
            alert("Fill topic, difficulty, count, and points per question.");
            return;
        }
        autoRequirements.add(new AutoExamRequirement(topic, difficulty, count, pts));
        requirementsList.getItems().setAll(autoRequirements);
        refreshAutoPoints();
    }

    @FXML
    private void onRemoveRequirement() {
        int idx = requirementsList.getSelectionModel().getSelectedIndex();
        if (idx >= 0 && idx < autoRequirements.size()) {
            autoRequirements.remove(idx);
            requirementsList.getItems().setAll(autoRequirements);
            refreshAutoPoints();
        }
    }

    @FXML
    private void onGenerateAuto() {
        syncFormToSession();
        AutoExamRequestBuilder builder = new AutoExamRequestBuilder()
                .courseId(session.getCourseId())
                .teacherId(session.getTeacherId())
                .title(titleField.getText())
                .durationMinutes(parsePositiveInt(durationField.getText(), 0))
                .studentInstructions(studentInstructions.getText())
                .teacherNotes(teacherNotes.getText());
        for (AutoExamRequirement r : autoRequirements) {
            builder.addRequirement(r);
        }
        AutoExamRequest request = builder.build();
        Message msg = session.requestAutoGenerate(request);
        if (msg == null) {
            alert(session.getLastError());
            autoStatusLabel.setText(session.getLastError());
            return;
        }
        send(msg);
        autoStatusLabel.setText(session.getStatusText());
    }

    @Subscribe
    public void onServerMessage(ServerMessageEvent event) {
        Message msg = event.getMessage();
        session.onServerMessage(msg);
        if (msg.getCommand() == Message.Command.ERROR) {
            alert(session.getLastError());
            autoStatusLabel.setText(session.getLastError() == null
                    ? session.getStatusText() : session.getLastError());
        }
        if (!session.getCourses().isEmpty()) {
            courseBox.setItems(FXCollections.observableArrayList(session.getCourses()));
            selectCourse(session.getCourseId());
        }
        bankList.getItems().setAll(session.getBank());
        selectedList.getItems().setAll(session.getSelectedQuestions());
        if (session.getLastSaved() != null) {
            applySessionToForm();
        }
        refreshPointsBadge();
        refreshStatus();
        autoStatusLabel.setText(session.getStatusText());
    }

    private void syncFormToSession() {
        Course c = courseBox.getValue();
        if (c != null) session.setCourseId(c.getId());
        session.setTitle(titleField.getText());
        session.setDurationMinutes(parsePositiveInt(durationField.getText(), 0));
        session.setStudentInstructions(studentInstructions.getText());
        session.setTeacherNotes(teacherNotes.getText());
    }

    private void applySessionToForm() {
        titleField.setText(session.getTitle());
        durationField.setText(String.valueOf(session.getDurationMinutes()));
        studentInstructions.setText(session.getStudentInstructions());
        teacherNotes.setText(session.getTeacherNotes());
        selectedList.getItems().setAll(session.getSelectedQuestions());
        selectCourse(session.getCourseId());
        saveButton.setText(session.isEditing() ? "Save (new version)" : "Save exam");
    }

    private void selectCourse(int courseId) {
        for (Course c : courseBox.getItems()) {
            if (c.getId() == courseId) {
                courseBox.setValue(c);
                return;
            }
        }
    }

    private void refreshPointsBadge() {
        pointsBadge.setText(session.pointsBadgeText());
        if (session.getPointsTotal() == ExamFormValidator.REQUIRED_TOTAL_POINTS) {
            pointsBadge.setStyle("");
        }
    }

    private void refreshAutoPoints() {
        int total = 0;
        for (AutoExamRequirement r : autoRequirements) {
            total += r.getTotalPoints();
        }
        autoPointsLabel.setText(total + " / " + ExamFormValidator.REQUIRED_TOTAL_POINTS);
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

    private static int parsePositiveInt(String text, int fallback) {
        try {
            return Integer.parseInt(text == null ? "" : text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private final class QuestionCell extends ListCell<Question> {
        private final Label title = new Label();
        private final Label sub = new Label();
        private final VBox box = new VBox(2, title, sub);

        QuestionCell() {
            title.getStyleClass().add("q-title");
            sub.getStyleClass().add("q-sub");
            title.setWrapText(true);
        }

        @Override
        protected void updateItem(Question q, boolean empty) {
            super.updateItem(q, empty);
            if (empty || q == null) {
                setGraphic(null);
                return;
            }
            title.setText(q.getQuestionText());
            String topic = q.getTopic() == null ? "?" : q.getTopic();
            String diff = q.getDifficulty() == null ? "?" : q.getDifficulty();
            sub.setText(topic + " · " + diff);
            setGraphic(box);
        }
    }

    private final class SelectedCell extends ListCell<ExamQuestion> {
        @Override
        protected void updateItem(ExamQuestion eq, boolean empty) {
            super.updateItem(eq, empty);
            if (empty || eq == null) {
                setText(null);
                return;
            }
            String text = "#" + eq.getQuestionId();
            for (Question q : session.getBank()) {
                if (q.getId() == eq.getQuestionId()) {
                    text = q.getQuestionText();
                    break;
                }
            }
            setText(eq.getPosition() + ". " + text + "  (" + eq.getPoints() + " pts)");
        }
    }

    private static final class RequirementCell extends ListCell<AutoExamRequirement> {
        @Override
        protected void updateItem(AutoExamRequirement r, boolean empty) {
            super.updateItem(r, empty);
            if (empty || r == null) {
                setText(null);
                return;
            }
            setText(r.getQuestionCount() + " × " + r.getTopic() + " / " + r.getDifficulty()
                    + " @ " + r.getPointsPerQuestion() + " pts (= " + r.getTotalPoints() + ")");
        }
    }
}
