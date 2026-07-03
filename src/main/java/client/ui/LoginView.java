package client.ui;

import common.entities.User;
import common.network.Credentials;
import common.network.Message;
import common.network.Message.Command;
import javafx.fxml.FXML;
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

    /** Server responses (already on the FX thread). */
    private void onServerMessage(Message msg) {
        switch (msg.getCommand()) {
            case SUCCESS:
                if (msg.getPayload() instanceof User) {
                    ScreenManager.getInstance().setScreen(new HomeView((User) msg.getPayload()));
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
