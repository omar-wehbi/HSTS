package client.ui;

import common.entities.Question;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Read-only viewer for one question's version history (Person 2, Phase 6 —
 * scenario 2.2: the previous version STAYS in the bank, so teachers can see it).
 *
 * <p>Left: every version of the family, oldest first, current marked. Right:
 * the selected version in full — text, the four answers with the correct one
 * ticked, topic/difficulty, and its own illustration (each version keeps the
 * image it had). Illustrations load lazily: the dialog only <em>asks</em> for
 * bytes through the {@code imageRequester} callback and renders whatever
 * {@link #onImage(byte[])} delivers — it owns no networking, so
 * {@link QuestionsView} stays the single place that talks to the server.
 */
public final class QuestionHistoryDialog {

    private final Stage stage;
    private final ImageView preview = new ImageView();
    private final Label imageStatus = new Label();
    /** Asks the owner to fetch the illustration of one question row id. */
    private final IntConsumer imageRequester;

    public QuestionHistoryDialog(Window owner, String displayId,
                                 List<Question> history, IntConsumer imageRequester) {
        this.imageRequester = imageRequester;

        stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("History of question " + displayId
                + " — " + history.size() + (history.size() == 1 ? " version" : " versions"));

        VBox details = new VBox(6);
        details.setPadding(new Insets(4));

        ListView<Question> versions = new ListView<>();
        versions.getItems().setAll(history);
        versions.setPrefWidth(180);
        versions.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Question q, boolean empty) {
                super.updateItem(q, empty);
                setText(empty || q == null ? null : versionSummary(q));
            }
        });
        versions.getSelectionModel().selectedItemProperty()
                .addListener((o, was, now) -> showVersion(details, now));
        versions.getSelectionModel().selectLast();   // open on the current version

        preview.setFitWidth(240);
        preview.setPreserveRatio(true);

        ScrollPane detailScroll = new ScrollPane(details);
        detailScroll.setFitToWidth(true);
        detailScroll.setPrefSize(420, 380);

        HBox root = new HBox(12, versions, detailScroll);
        HBox.setHgrow(detailScroll, Priority.ALWAYS);
        root.setPadding(new Insets(12));

        stage.setScene(new javafx.scene.Scene(root));
    }

    /** Shows the dialog without blocking the bank screen underneath. */
    public void show() {
        stage.show();
    }

    /** Delivery point for lazily fetched illustration bytes (may be null = none). */
    public void onImage(byte[] bytes) {
        if (bytes != null) {
            preview.setImage(new Image(new ByteArrayInputStream(bytes)));
            imageStatus.setText("");
        } else {
            preview.setImage(null);
            imageStatus.setText("(illustration unavailable)");
        }
    }

    /** @return true while the dialog is on screen (the owner routes image bytes here). */
    public boolean isShowing() {
        return stage.isShowing();
    }

    // ===== rendering ======================================================

    private void showVersion(VBox details, Question q) {
        details.getChildren().clear();
        preview.setImage(null);
        if (q == null) return;

        Label title = new Label(versionSummary(q));
        title.setStyle("-fx-font-weight: bold;");
        Label text = new Label(q.getQuestionText());
        text.setWrapText(true);

        details.getChildren().addAll(title, text, new Label(""));
        for (int i = 1; i <= 4; i++) {
            Label answer = new Label(answerLine(q, i));
            answer.setWrapText(true);
            details.getChildren().add(answer);
        }
        details.getChildren().add(new Label(metaLine(q)));

        if (q.getImagePath() != null) {
            imageStatus.setText("Loading illustration…");
            details.getChildren().addAll(new Label("Illustration: " + q.getImagePath()),
                    imageStatus, preview);
            imageRequester.accept(q.getId());   // lazy fetch (NFR 18)
        }
    }

    // ===== pure label helpers (unit-tested without JavaFX running) ========

    /** e.g. {@code "v2 · current"} / {@code "v1 · retired"}. */
    static String versionSummary(Question q) {
        return "v" + q.getVersion() + (q.isCurrent() ? " · current" : " · retired");
    }

    /** One answer row, the correct one ticked: {@code "✔ 2. Queue"}. */
    static String answerLine(Question q, int index) {
        String text = switch (index) {
            case 1 -> q.getAnswer1();
            case 2 -> q.getAnswer2();
            case 3 -> q.getAnswer3();
            default -> q.getAnswer4();
        };
        return (index == q.getCorrectAnswer() ? "✔ " : "    ") + index + ". " + text;
    }

    /** e.g. {@code "Topic: Sorting · Difficulty: EASY"} (omits missing parts). */
    static String metaLine(Question q) {
        StringBuilder sb = new StringBuilder();
        if (q.getTopic() != null) sb.append("Topic: ").append(q.getTopic());
        if (q.getDifficulty() != null) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append("Difficulty: ").append(q.getDifficulty());
        }
        return sb.toString();
    }
}
