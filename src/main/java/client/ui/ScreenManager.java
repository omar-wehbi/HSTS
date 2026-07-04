package client.ui;

import client.events.ClientEventBus;
import client.network.IClientConnection;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.Objects;

/**
 * Centralized navigation controller (Singleton Pattern) — the single source of
 * truth for routing between screens (Presentation tier).
 *
 * <p>Owns the primary {@link Stage} and the shared {@link IClientConnection} so
 * any screen can reach the network adapter without holding its own copy. New
 * screens inherit from {@link AbstractScreenUI} and are shown via
 * {@link #setScreen(AbstractScreenUI)}.
 */
public final class ScreenManager {

    /** Shared stylesheet applied to every screen's Scene. */
    private static final String STYLESHEET = "/css/app.css";

    /**
     * One stable window for the whole app (Person 2, UX fix): the stage opens
     * once at this size, centered, and then NEVER resizes or re-centers on
     * navigation — small screens (login/connect) simply center their card
     * inside it (their roots are StackPanes). Previously every setScreen()
     * called sizeToScene() + centerOnScreen(), so the window shrank to each
     * screen's pref size and teleported — the "jumping tiny windows" effect.
     */
    private static final double WINDOW_WIDTH  = 1100;
    private static final double WINDOW_HEIGHT = 700;
    private static final double MIN_WIDTH     = 860;
    private static final double MIN_HEIGHT    = 560;

    private static ScreenManager instance;

    private Stage primaryStage;
    private IClientConnection client;
    /** The screen currently shown (for EventBus unregistration on navigation). */
    private AbstractScreenUI currentScreen;
    /** The logged-in user (set on login, cleared on logout) so any screen can
     *  navigate back to the role-based home menu. */
    private common.entities.User currentUser;

    private ScreenManager() {
    }

    /** @return the lazily-created singleton instance. */
    public static synchronized ScreenManager getInstance() {
        if (instance == null) {
            instance = new ScreenManager();
        }
        return instance;
    }

    /** Wires the JavaFX primary stage (called once from ClientApp). */
    public void init(Stage primaryStage) {
        this.primaryStage = primaryStage;
        this.primaryStage.setTitle("HSTS — High School Test System");
        this.primaryStage.setMinWidth(MIN_WIDTH);
        this.primaryStage.setMinHeight(MIN_HEIGHT);
        try {
            primaryStage.getIcons().add(Logo.snapshotImage(128));
        } catch (RuntimeException ignored) {
            // A missing window icon is non-fatal; never block startup over branding.
        }
    }

    public Stage getPrimaryStage() {
        return primaryStage;
    }

    public void setClient(IClientConnection client) {
        this.client = client;
    }

    public IClientConnection getClient() {
        return client;
    }

    /** Remembers who is logged in (LoginView sets it; logout clears it). */
    public void setCurrentUser(common.entities.User user) {
        this.currentUser = user;
    }

    /** @return the logged-in user, or null when no one is signed in. */
    public common.entities.User getCurrentUser() {
        return currentUser;
    }

    /**
     * Renders the given screen and shows it on the primary stage. Uses the
     * screen's template {@code load()} so each screen's post-render hook fires.
     * Applies the shared stylesheet. The window keeps its size and position
     * across navigations (sized and centered exactly once, on first show) —
     * no jumping, no shrinking to a screen's pref size.
     */
    public void setScreen(AbstractScreenUI screen) {
        // Pub/Sub lifecycle: the outgoing screen stops receiving events, the new
        // screen (if it declares @Subscribe methods) starts. Also clear the
        // legacy direct handler so a stale screen never receives messages.
        if (currentScreen != null) {
            ClientEventBus.unregister(currentScreen);
        }
        if (client != null) {
            client.setServerMessageHandler(null);
        }
        currentScreen = screen;

        Parent root = screen.load();
        ClientEventBus.register(screen);
        Scene current = primaryStage.getScene();
        if (current == null) {
            // First screen: create the one Scene at the app's standard size.
            Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT);
            applyStylesheet(scene);
            primaryStage.setScene(scene);
        } else {
            // Navigation: swap the root only — size and position are untouched.
            current.setRoot(root);
            applyStylesheet(current);
        }
        if (!primaryStage.isShowing()) {
            primaryStage.show();
            primaryStage.centerOnScreen();   // once, on first show
        }
    }

    private void applyStylesheet(Scene scene) {
        String css = Objects.requireNonNull(
                getClass().getResource(STYLESHEET), "Missing stylesheet: " + STYLESHEET)
                .toExternalForm();
        if (!scene.getStylesheets().contains(css)) {
            scene.getStylesheets().add(css);
        }
    }
}
