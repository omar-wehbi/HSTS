package client.ui;

import common.entities.Course;
import common.entities.Question;
import common.network.Message;
import common.network.Message.Command;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Question Bank manager (Presentation tier), defined in FXML.
 *
 * <p>Master-detail: the current bank on the left, a full editor on the right
 * (course, text, 4 answers, correct answer, topic, difficulty). Supports the four
 * bank operations from the assignment: view, add, edit (versioned — the old
 * version is kept) and delete. This class is the FXML controller (set via
 * {@code loader.setController(this)} so the Template Method / Singleton / Adapter
 * wiring stays intact). All persistence happens on the server via the DAO.
 */
public class QuestionsView extends AbstractScreenUI {

    private static final String FXML_PATH = "/fxml/QuestionsView.fxml";

    @FXML private ListView<Question> listView;
    @FXML private ComboBox<Course>   courseBox;
    @FXML private TextArea           questionField;
    @FXML private TextField          a1, a2, a3, a4;
    @FXML private ComboBox<Integer>  correctBox;
    @FXML private ComboBox<String>   difficultyBox;
    @FXML private TextField          topicField;
    @FXML private Button             newButton, deleteButton, saveButton;
    @FXML private Label              countBadge, idBadge, editorTitle, hintLabel, statusLabel, savedLabel;
    @FXML private StackPane          logoBox;

    /** The question currently being edited (null = adding a new one). */
    private Question selected;
    /** True while a save/add/delete we sent is awaiting its server confirmation. */
    private boolean awaitingSave = false;
    /** True when the pending operation was an Add (so we reset the form on success). */
    private boolean pendingWasAdd = false;

    @Override
    public Parent render() {
        client().setServerMessageHandler(this::onServerMessage);
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
        logoBox.getChildren().add(Logo.create(34));
        correctBox.setItems(FXCollections.observableArrayList(1, 2, 3, 4));
        difficultyBox.setItems(FXCollections.observableArrayList("EASY", "MEDIUM", "HARD"));

        listView.setCellFactory(lv -> new QuestionCell());
        listView.getSelectionModel().selectedItemProperty().addListener((o, was, now) -> fillForm(now));

        startNew();
    }

    @Override
    protected void onShown() {
        send(new Message(Command.GET_COURSES));
        send(new Message(Command.GET_QUESTIONS));
    }

    // ===== actions ========================================================

    /** Navigates back to the role-based home menu (or to login if signed out). */
    @FXML
    private void onBackToMenu() {
        common.entities.User user = ScreenManager.getInstance().getCurrentUser();
        if (user != null) {
            ScreenManager.getInstance().setScreen(new HomeView(user));
        } else {
            ScreenManager.getInstance().setScreen(new LoginView());
        }
    }

    @FXML
    private void onNew() {
        startNew();
    }

    @FXML
    private void onSave() {
        String error = validate();
        if (error != null) { alert(error); return; }

        Question q = buildFromForm();
        clearSavedBadge();
        awaitingSave = true;
        pendingWasAdd = (selected == null);
        if (selected == null) {
            statusLabel.setText("Adding…");
            send(new Message(Command.ADD_QUESTION, q));
        } else {
            q.setBaseId(selected.getBaseId());
            statusLabel.setText("Saving new version…");
            send(new Message(Command.UPDATE_QUESTION, q));
        }
    }

    @FXML
    private void onDelete() {
        if (selected == null) return;
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete this question and all its versions?", ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText(null);
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            awaitingSave = true;
            statusLabel.setText("Deleting…");
            send(new Message(Command.DELETE_QUESTION, selected.getBaseId()));
        }
    }

    // ===== server responses (on the FX thread) ============================

    @SuppressWarnings("unchecked")
    public void onServerMessage(Message msg) {
        switch (msg.getCommand()) {
            case SUCCESS:
                Object payload = msg.getPayload();
                if (payload instanceof List) {
                    List<?> li = (List<?>) payload;
                    if (!li.isEmpty() && li.get(0) instanceof Course) {
                        courseBox.setItems(FXCollections.observableArrayList((List<Course>) li));
                        if (courseBox.getValue() == null && !courseBox.getItems().isEmpty())
                            courseBox.setValue(courseBox.getItems().get(0));
                    } else {
                        boolean wasAdd = pendingWasAdd;
                        updateBank((List<Question>) li);
                        if (awaitingSave) {
                            awaitingSave = false;
                            pendingWasAdd = false;
                            if (wasAdd) startNew();   // clear the form so Add doesn't duplicate
                            showSavedBadge();
                            statusLabel.setText("Saved to the database.");
                        } else {
                            statusLabel.setText("Bank: " + li.size() + " questions.");
                        }
                    }
                }
                break;
            case ERROR:
                awaitingSave = false;
                Alert a = new Alert(Alert.AlertType.ERROR, String.valueOf(msg.getPayload()));
                a.setHeaderText("Server returned an error");
                a.showAndWait();
                statusLabel.setText("Server error.");
                break;
            default:
                statusLabel.setText("Unexpected: " + msg.getCommand());
        }
    }

    private void updateBank(List<Question> bank) {
        int keepBaseId = (selected == null) ? -1 : selected.getBaseId();
        listView.getItems().setAll(bank);
        countBadge.setText(bank.size() + (bank.size() == 1 ? " question" : " questions"));
        if (keepBaseId != -1) {
            for (Question q : bank) {
                if (q.getBaseId() == keepBaseId) { listView.getSelectionModel().select(q); return; }
            }
            startNew(); // the edited/deleted item is gone
        }
    }

    // ===== form state =====================================================

    private void startNew() {
        listView.getSelectionModel().clearSelection();
        selected = null;
        clearForm();
        deleteButton.setDisable(true);
        saveButton.setText("Add");
        editorTitle.setText("New question");
        setNodeShown(idBadge, false);
        setNodeShown(hintLabel, false);
        clearSavedBadge();
        if (statusLabel != null) statusLabel.setText("Adding a new question.");
    }

    private void fillForm(Question q) {
        if (q == null) return;
        selected = q;
        selectCourse(q.getCourseId());
        questionField.setText(q.getQuestionText());
        a1.setText(q.getAnswer1());
        a2.setText(q.getAnswer2());
        a3.setText(q.getAnswer3());
        a4.setText(q.getAnswer4());
        correctBox.setValue(q.getCorrectAnswer());
        topicField.setText(q.getTopic());
        difficultyBox.setValue(q.getDifficulty());

        deleteButton.setDisable(false);
        saveButton.setText("Save (new version)");
        editorTitle.setText("Edit question");
        int pos = listView.getItems().indexOf(q) + 1;
        idBadge.setText("#" + pos + " · v" + q.getVersion());
        setNodeShown(idBadge, true);
        setNodeShown(hintLabel, true);
        clearSavedBadge();
        statusLabel.setText("Editing question #" + pos + " (saving keeps the old version).");
    }

    private Question buildFromForm() {
        Course c = courseBox.getValue();
        return new Question(
                c.getId(),
                questionField.getText().trim(),
                a1.getText().trim(), a2.getText().trim(), a3.getText().trim(), a4.getText().trim(),
                correctBox.getValue(),
                null,
                emptyToNull(topicField.getText()),
                difficultyBox.getValue());
    }

    private String validate() {
        if (courseBox.getValue() == null) return "Please choose a course.";
        if (questionField.getText().trim().isEmpty()) return "Question text is required.";
        if (a1.getText().trim().isEmpty() || a2.getText().trim().isEmpty()
                || a3.getText().trim().isEmpty() || a4.getText().trim().isEmpty())
            return "All 4 answers are required.";
        if (correctBox.getValue() == null) return "Choose which answer (1-4) is correct.";
        return null;
    }

    private void clearForm() {
        questionField.clear(); a1.clear(); a2.clear(); a3.clear(); a4.clear(); topicField.clear();
        correctBox.setValue(null);
        difficultyBox.setValue(null);
        if (courseBox != null && !courseBox.getItems().isEmpty())
            courseBox.setValue(courseBox.getItems().get(0));
    }

    private void selectCourse(int courseId) {
        for (Course c : courseBox.getItems())
            if (c.getId() == courseId) { courseBox.setValue(c); return; }
    }

    private void showSavedBadge() { setNodeShown(savedLabel, true); }
    private void clearSavedBadge() { setNodeShown(savedLabel, false); }

    private void send(Message m) {
        try { client().send(m); }
        catch (IOException e) { statusLabel.setText("Send failed: " + e.getMessage()); }
    }

    private static void setNodeShown(Node node, boolean shown) {
        node.setVisible(shown);
        node.setManaged(shown);
    }

    private static String emptyToNull(String s) {
        return (s == null || s.trim().isEmpty()) ? null : s.trim();
    }

    private void alert(String text) {
        Alert a = new Alert(Alert.AlertType.WARNING, text);
        a.setHeaderText(null);
        a.showAndWait();
    }

    /** List cell: wrapped question + a small "course · vN · correct" sub-line. */
    private final class QuestionCell extends ListCell<Question> {
        private final Label title = new Label();
        private final Label sub = new Label();
        private final VBox box = new VBox(3, title, sub);

        QuestionCell() {
            title.setWrapText(true);
            title.getStyleClass().add("q-title");
            sub.getStyleClass().add("q-sub");
            box.setFillWidth(true);
            box.maxWidthProperty().bind(listView.widthProperty().subtract(60));
            title.maxWidthProperty().bind(box.maxWidthProperty());
            setText(null);
        }

        @Override
        protected void updateItem(Question q, boolean empty) {
            super.updateItem(q, empty);
            if (empty || q == null) { setGraphic(null); return; }
            title.setText("#" + (getIndex() + 1) + "   " + q.getQuestionText());
            String diff = q.getDifficulty() == null ? "" : " · " + q.getDifficulty();
            sub.setText("v" + q.getVersion() + " · correct: answer " + q.getCorrectAnswer() + diff);
            setGraphic(box);
        }
    }
}
