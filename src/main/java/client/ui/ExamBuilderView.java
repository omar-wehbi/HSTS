package client.ui;

import client.events.ClientConnectionEvent;
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
import javafx.scene.control.ButtonType;
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
import java.util.TreeSet;

/**
 * Manual + automatic exam builder (scenario 3).
 */
public class ExamBuilderView extends AbstractScreenUI {

    private static final String FXML_PATH = "/fxml/ExamBuilderView.fxml";

    private final ExamBuilderSession session = new ExamBuilderSession();
    private final Exam existing;
    private final List<AutoExamRequirement> autoRequirements = new ArrayList<>();
    /** Suppresses course listeners while ComboBoxes are rebound programmatically. */
    private boolean syncingCourses;

    @FXML private ComboBox<Course> courseBox, autoCourseBox;
    @FXML private ComboBox<String> topicBox, autoTopic, autoDifficulty;
    @FXML private ListView<Question> bankList;
    @FXML private ListView<ExamQuestion> selectedList;
    @FXML private TextField titleField, durationField, pointsField;
    @FXML private TextArea studentInstructions, teacherNotes;
    @FXML private Label pointsBadge, statusLabel, autoPointsLabel, autoStatusLabel;
    @FXML private Button saveButton, addButton;
    @FXML private TextField autoCount, autoPts, autoTitleField, autoDurationField;
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
        topicBox.getSelectionModel().selectedItemProperty()
                .addListener((o, was, now) -> refreshBankList());
        courseBox.getSelectionModel().selectedItemProperty()
                .addListener((o, was, now) -> {
                    if (syncingCourses) return;
                    if (now != null && (was == null || now.getId() != was.getId())) {
                        session.setCourseId(now.getId());
                        selectCourse(now.getId());
                        send(session.requestBank());
                    }
                });
        autoCourseBox.getSelectionModel().selectedItemProperty()
                .addListener((o, was, now) -> {
                    if (syncingCourses) return;
                    if (now != null && (was == null || now.getId() != was.getId())) {
                        session.setCourseId(now.getId());
                        selectCourse(now.getId());
                        send(session.requestBank());
                    }
                });
        User user = ScreenManager.getInstance().getCurrentUser();
        if (user != null) {
            session.setTeacherId(user.getId());
        }
        if (existing != null) {
            session.loadExam(existing);
            if (session.getCourseId() > 0) {
                seedCoursePlaceholder(session.getCourseId());
            }
            applySessionToForm();
        }
        refreshPointsBadge();
    }

    @Override
    protected void onShown() {
        if (existing != null && session.getCourseId() > 0) {
            send(session.requestBank());
        } else {
            send(session.requestCourses());
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
        String topic = autoTopic.getValue() == null ? "" : autoTopic.getValue().trim();
        String difficulty = autoDifficulty.getValue();
        int count = parsePositiveInt(autoCount.getText(), 0);
        int pts = parsePositiveInt(autoPts.getText(), 0);
        if (topic.isEmpty() || difficulty == null || count <= 0 || pts <= 0) {
            alert("Choose topic and difficulty, and fill count and points per question.");
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
        syncAutoFormToSession();
        AutoExamRequestBuilder builder = new AutoExamRequestBuilder()
                .courseId(session.getCourseId())
                .teacherId(session.getTeacherId())
                .title(autoTitleField.getText())
                .durationMinutes(parsePositiveInt(autoDurationField.getText(), 0))
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
        boolean wasAwaitingCourses = session.isAwaitingCourses();
        boolean wasAwaitingBank = session.isAwaitingBank();
        boolean wasAwaitingSave = session.isAwaitingSave();
        boolean wasAwaitingAuto = session.isAwaitingAuto();
        session.onServerMessage(msg);
        if (msg.getCommand() == Message.Command.ERROR) {
            alert(session.getLastError());
            autoStatusLabel.setText(session.getLastError() == null
                    ? session.getStatusText() : session.getLastError());
        }
        // Only rebind course boxes when courses just arrived — rebinding on every
        // SUCCESS (e.g. bank load) re-fires selection listeners and loops forever.
        if (wasAwaitingCourses && !session.getCourses().isEmpty()) {
            syncingCourses = true;
            try {
                courseBox.setItems(FXCollections.observableArrayList(session.getCourses()));
                autoCourseBox.setItems(FXCollections.observableArrayList(session.getCourses()));
                selectCourse(session.getCourseId());
            } finally {
                syncingCourses = false;
            }
            if (existing == null && session.getCourseId() > 0) {
                send(session.requestBank());
            }
        }
        if (wasAwaitingBank || wasAwaitingCourses) {
            refreshTopicBoxes();
            refreshBankList();
        }
        selectedList.getItems().setAll(session.getSelectedQuestions());
        if (session.getLastSaved() != null && (wasAwaitingSave || wasAwaitingAuto)) {
            applySessionToForm();
            if (wasAwaitingAuto) {
                showAutoGenerateSuccess(session.getLastSaved());
            } else {
                showSaveSuccess(session.getLastSaved());
            }
        }
        refreshPointsBadge();
        refreshStatus();
        autoStatusLabel.setText(session.getStatusText());
    }

    @Subscribe
    public void onConnectionLost(ClientConnectionEvent event) {
        session.onConnectionLost(event.getReason());
        autoStatusLabel.setText(session.getStatusText());
        statusLabel.setText(session.getStatusText());
        alert(event.getReason()
                + "\n\nIf you recently rebuilt the app, stop and restart the server, then try again.");
    }

    private void seedCoursePlaceholder(int courseId) {
        String name = (existing != null && existing.getCourseName() != null)
                ? existing.getCourseName()
                : "Course #" + courseId;
        Course placeholder = new Course(courseId, name);
        syncingCourses = true;
        try {
            courseBox.setItems(FXCollections.observableArrayList(placeholder));
            autoCourseBox.setItems(FXCollections.observableArrayList(placeholder));
            selectCourse(courseId);
        } finally {
            syncingCourses = false;
        }
    }

    private void showAutoGenerateSuccess(Exam saved) {
        Alert success = new Alert(Alert.AlertType.INFORMATION);
        success.setHeaderText(null);
        success.setTitle("Exam generated");
        success.setContentText("Exam \"" + saved.getTitle() + "\" was created successfully.");
        success.getButtonTypes().setAll(
                ButtonType.OK,
                new ButtonType("View exams"));
        success.showAndWait().ifPresent(button -> {
            if (button != ButtonType.OK) {
                ScreenManager.getInstance().setScreen(new ExamListView());
            }
        });
    }

    private void showSaveSuccess(Exam saved) {
        Alert success = new Alert(Alert.AlertType.INFORMATION);
        success.setHeaderText(null);
        success.setTitle("Exam saved");
        success.setContentText("Exam \"" + saved.getTitle() + "\" was saved successfully.");
        success.showAndWait();
    }

    private void syncFormToSession() {
        Course c = courseBox.getValue();
        if (c != null) session.setCourseId(c.getId());
        session.setTitle(titleField.getText());
        session.setDurationMinutes(parsePositiveInt(durationField.getText(), 0));
        session.setStudentInstructions(studentInstructions.getText());
        session.setTeacherNotes(teacherNotes.getText());
        // Keep Automatic tab fields aligned with Manual edits.
        autoTitleField.setText(titleField.getText());
        autoDurationField.setText(durationField.getText());
        selectCourse(session.getCourseId());
    }

    private void syncAutoFormToSession() {
        Course c = autoCourseBox.getValue();
        if (c != null) session.setCourseId(c.getId());
        session.setTitle(autoTitleField.getText());
        session.setDurationMinutes(parsePositiveInt(autoDurationField.getText(), 0));
        // Keep Manual tab fields in sync so a later manual save uses the same details.
        titleField.setText(autoTitleField.getText());
        durationField.setText(autoDurationField.getText());
        selectCourse(session.getCourseId());
    }

    private void applySessionToForm() {
        titleField.setText(session.getTitle());
        durationField.setText(String.valueOf(session.getDurationMinutes()));
        autoTitleField.setText(session.getTitle());
        autoDurationField.setText(String.valueOf(session.getDurationMinutes()));
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
                break;
            }
        }
        for (Course c : autoCourseBox.getItems()) {
            if (c.getId() == courseId) {
                autoCourseBox.setValue(c);
                return;
            }
        }
    }

    /** Distinct topics from the loaded bank, for Manual + Automatic dropdowns. */
    private void refreshTopicBoxes() {
        String manualSelected = topicBox.getValue();
        String autoSelected = autoTopic.getValue();
        TreeSet<String> topics = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Question q : session.getBank()) {
            if (q.getTopic() != null && !q.getTopic().isBlank()) {
                topics.add(q.getTopic().trim());
            }
        }
        List<String> topicList = new ArrayList<>(topics);
        topicBox.setItems(FXCollections.observableArrayList(topicList));
        autoTopic.setItems(FXCollections.observableArrayList(topicList));
        if (manualSelected != null && topics.contains(manualSelected)) {
            topicBox.setValue(manualSelected);
        } else {
            topicBox.getSelectionModel().clearSelection();
        }
        if (autoSelected != null && topics.contains(autoSelected)) {
            autoTopic.setValue(autoSelected);
        } else {
            autoTopic.getSelectionModel().clearSelection();
        }
    }

    private void refreshBankList() {
        String selectedTopic = topicBox.getValue();
        if (selectedTopic == null || selectedTopic.isBlank()) {
            bankList.getItems().setAll(session.getBank());
            return;
        }
        List<Question> filtered = new ArrayList<>();
        for (Question q : session.getBank()) {
            if (q.getTopic() != null && selectedTopic.equalsIgnoreCase(q.getTopic().trim())) {
                filtered.add(q);
            }
        }
        bankList.getItems().setAll(filtered);
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
