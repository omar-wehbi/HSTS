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

    /** Illustration uploads larger than this are rejected client-side (NFR 18). */
    private static final int MAX_IMAGE_BYTES = 2 * 1024 * 1024;

    @FXML private ListView<Question> listView;
    @FXML private ComboBox<Course>   courseBox;
    @FXML private TextArea           questionField;
    @FXML private TextField          a1, a2, a3, a4;
    @FXML private ComboBox<Integer>  correctBox;
    @FXML private ComboBox<String>   difficultyBox;
    @FXML private TextField          topicField;
    @FXML private Button             newButton, deleteButton, saveButton, imageButton, imageClearButton;
    @FXML private Label              countBadge, idBadge, editorTitle, hintLabel, statusLabel, savedLabel,
                                     imageNameLabel;
    @FXML private StackPane          logoBox;
    @FXML private javafx.scene.image.ImageView imagePreview;

    /** The question currently being edited (null = adding a new one). */
    private Question selected;
    /** True while a save/add/delete we sent is awaiting its server confirmation. */
    private boolean awaitingSave = false;
    /** True when the pending operation was an Add (so we reset the form on success). */
    private boolean pendingWasAdd = false;

    // ----- illustration state (scenario 2: a question includes an illustration) -----
    /** Newly chosen image bytes; null = nothing chosen in this edit session. */
    private byte[] pendingImageBytes;
    /** File name of the newly chosen image; null = nothing chosen. */
    private String pendingImageName;
    /** True while editing a question whose stored image should be kept as-is. */
    private boolean keepExistingImage = false;
    /** True while a GET_QUESTION_IMAGE we sent is awaiting its reply. */
    private boolean awaitingImage = false;

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

    /** Picks an illustration file, size-checked, and previews it immediately. */
    @FXML
    private void onChooseImage() {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Choose an illustration");
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter(
                "Images (png, jpg, gif)", "*.png", "*.jpg", "*.jpeg", "*.gif"));
        java.io.File file = chooser.showOpenDialog(imageButton.getScene().getWindow());
        if (file == null) return;

        if (file.length() > MAX_IMAGE_BYTES) {
            alert("Illustration too large: " + (file.length() / 1024) + " KB (max "
                    + (MAX_IMAGE_BYTES / 1024) + " KB).");
            return;
        }
        try {
            pendingImageBytes = java.nio.file.Files.readAllBytes(file.toPath());
        } catch (IOException e) {
            alert("Could not read the file: " + e.getMessage());
            return;
        }
        pendingImageName = file.getName();
        keepExistingImage = false;
        imageNameLabel.setText(pendingImageName);
        showPreview(pendingImageBytes);
        setNodeShown(imageClearButton, true);
        statusLabel.setText("Illustration selected — will be saved with the question.");
    }

    /** Removes the illustration (both a fresh pick and a stored one). */
    @FXML
    private void onClearImage() {
        pendingImageBytes = null;
        pendingImageName = null;
        keepExistingImage = false;
        imageNameLabel.setText("No illustration");
        hidePreview();
        setNodeShown(imageClearButton, false);
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
                if (awaitingImage && (payload instanceof byte[] || payload == null)) {
                    // Reply to our lazy GET_QUESTION_IMAGE (NFR 18).
                    awaitingImage = false;
                    if (payload != null) {
                        showPreview((byte[]) payload);
                        statusLabel.setText("Illustration loaded.");
                    } else {
                        hidePreview();
                    }
                } else if (payload instanceof List) {
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

        // Illustration: lists carry only the name; fetch the bytes lazily.
        pendingImageBytes = null;
        pendingImageName = null;
        hidePreview();
        if (q.getImagePath() != null) {
            keepExistingImage = true;
            imageNameLabel.setText(q.getImagePath() + " (stored)");
            setNodeShown(imageClearButton, true);
            statusLabel.setText("Loading illustration…");
            awaitingImage = true;
            send(new Message(Command.GET_QUESTION_IMAGE, q.getId()));
        } else {
            keepExistingImage = false;
            imageNameLabel.setText("No illustration");
            setNodeShown(imageClearButton, false);
        }

        deleteButton.setDisable(false);
        saveButton.setText("Save (new version)");
        editorTitle.setText("Edit question");
        String displayId = displayIdOf(q);
        idBadge.setText(displayId + " · v" + q.getVersion());
        setNodeShown(idBadge, true);
        setNodeShown(hintLabel, true);
        clearSavedBadge();
        statusLabel.setText("Editing question " + displayId + " (saving keeps the old version).");
    }

    /**
     * The official 5-digit display id (semester doc): 4-digit sequence from the
     * version-family id + one course-code digit. Demo convention: course id
     * doubles as course code (see schema/README.md). Falls back to the raw id
     * if a value ever outgrows the scheme, so the UI never breaks.
     */
    private static String displayIdOf(Question q) {
        try {
            return common.util.DisplayId.format(q.getBaseId(), q.getCourseId());
        } catch (IllegalArgumentException outOfScheme) {
            return "#" + q.getBaseId();
        }
    }

    private Question buildFromForm() {
        Course c = courseBox.getValue();
        Question q = new Question(
                c.getId(),
                questionField.getText().trim(),
                a1.getText().trim(), a2.getText().trim(), a3.getText().trim(), a4.getText().trim(),
                correctBox.getValue(),
                null,
                emptyToNull(topicField.getText()),
                difficultyBox.getValue());
        // Illustration intent (understood by QuestionDAO.update):
        //   new pick  -> path + bytes        (replace / attach)
        //   untouched -> stored path, no bytes (server keeps the old image)
        //   removed   -> no path              (no image)
        if (pendingImageName != null) {
            q.setImagePath(pendingImageName);
            q.setImageData(pendingImageBytes);
        } else if (keepExistingImage && selected != null) {
            q.setImagePath(selected.getImagePath());
        }
        return q;
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
        onClearImage();
    }

    // ----- illustration preview helpers ----------------------------------

    private void showPreview(byte[] bytes) {
        imagePreview.setImage(new javafx.scene.image.Image(new java.io.ByteArrayInputStream(bytes)));
        setNodeShown(imagePreview, true);
    }

    private void hidePreview() {
        imagePreview.setImage(null);
        setNodeShown(imagePreview, false);
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
            title.setText(displayIdOf(q) + "   " + q.getQuestionText());
            String diff = q.getDifficulty() == null ? "" : " · " + q.getDifficulty();
            String img = q.getImagePath() == null ? "" : " · 🖼 illustrated";
            sub.setText("v" + q.getVersion() + " · correct: answer " + q.getCorrectAnswer() + diff + img);
            setGraphic(box);
        }
    }
}
