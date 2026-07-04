package client.ui;

import client.events.ServerMessageEvent;
import common.entities.User;
import common.network.Credentials;
import common.network.Message;
import common.network.Message.Command;
import javafx.fxml.FXML;
import org.greenrobot.eventbus.Subscribe;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Sign-in screen (Presentation tier, scenario 1). Sends {@code LOGIN} with the
 * typed credentials; on {@code SUCCESS} (a {@link User}) navigates to the
 * role-based {@link HomeView}; on {@code ERROR} shows the reason inline.
 */
public class LoginView extends AbstractScreenUI {

    private static final String FXML_PATH = "/fxml/LoginView.fxml";

    @FXML private TextField     usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Button        loginButton;
    @FXML private Label         errorLabel;

    @Override
    public Parent render() {
        // No setServerMessageHandler here: this screen receives server responses
        // via the EventBus (see onServerMessage below) — ScreenManager registers
        // and unregisters it automatically on navigation.
        FXMLLoader loader = new FXMLLoader(getClass().getResource(FXML_PATH));
        loader.setController(this);
        try {
            return loader.load();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load " + FXML_PATH, e);
        }
    }

    @FXML
    private void onLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        if (username.isEmpty() || password.isEmpty()) {
            showError("Enter a username and password.");
            return;
        }
        setError(false, null);
        loginButton.setDisable(true);
        loginButton.setText("Signing in…");
        try {
            client().send(new Message(Command.LOGIN, new Credentials(username, password)));
        } catch (IOException e) {
            resetButton();
            showError("Could not reach the server: " + e.getMessage());
        }
    }

    /**
     * Pub/Sub subscription (Lab 5 pattern): the network adapter publishes every
     * server {@link Message} as a {@link ServerMessageEvent}; this screen is
     * registered by {@code ScreenManager} while it is shown. Events arrive
     * already on the JavaFX Application Thread.
     */
    @Subscribe
    public void onServerMessage(ServerMessageEvent event) {
        Message msg = event.getMessage();
        switch (msg.getCommand()) {
            case SUCCESS:
                if (msg.getPayload() instanceof User) {
                    User user = (User) msg.getPayload();
                    ScreenManager.getInstance().setCurrentUser(user);
                    ScreenManager.getInstance().setScreen(new HomeView(user));
                }
                break;
            case ERROR:
                resetButton();
                showError(String.valueOf(msg.getPayload()));
                break;
            default:
                resetButton();
                showError("Unexpected response.");
        }
    }

    private void resetButton() {
        loginButton.setDisable(false);
        loginButton.setText("Log in");
    }

    private void showError(String text) {
        errorLabel.setText(text);
        setError(true, text);
    }

    private void setError(boolean shown, String text) {
        setNodeShown(errorLabel, shown);
    }

    private static void setNodeShown(Node node, boolean shown) {
        node.setVisible(shown);
        node.setManaged(shown);
    }
}
