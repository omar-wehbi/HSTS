package server.ui;

import server.config.DbSettings;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.sql.Connection;
import java.sql.DriverManager;

/**
 * Startup window where the operator points the server at a MySQL database
 * (Person 2, Phase 0.5 — satisfies NFR 15's "GUI to initialize the connection").
 *
 * <p>Prefilled from the resolved {@link DbSettings} (system properties /
 * {@code server.properties} / defaults), so on a correctly configured machine
 * the operator just clicks <b>Start Server</b>. On any other machine — a
 * teammate's laptop, the lab PC at the defense — they edit the fields, press
 * <b>Test Connection</b> for immediate feedback (NFR 21: progress + clear
 * success/error), optionally tick "save as defaults", and start.
 *
 * <p>Deliberately <b>Swing</b>, not JavaFX: the server tier must not depend on
 * the client's UI stack (JavaFX is excluded from {@code G12_Server.jar} builds
 * of the future; Swing ships with every JDK).
 *
 * <p>The dialog only <em>collects</em> values — persisting them and configuring
 * {@link server.db.DatabaseConfig} are the caller's job ({@code ServerMain}),
 * keeping this class pure UI.
 */
public final class ServerConnectionDialog {

    /** What the operator chose: DB profile, OCSF port, and whether to persist. */
    public record Result(DbSettings settings, int serverPort, boolean saveRequested) { }

    /** Seconds before a "Test Connection" attempt gives up. */
    private static final int LOGIN_TIMEOUT_SECONDS = 5;

    private final JDialog dialog;
    private final JTextField hostField;
    private final JTextField portField;
    private final JTextField databaseField;
    private final JTextField userField;
    private final JPasswordField passwordField;
    private final JTextField serverPortField;
    private final JCheckBox saveCheckBox;
    private final JLabel statusLabel;
    private final JButton testButton;
    private final JButton startButton;

    private Result result;   // null until Start is pressed

    /**
     * Shows the modal dialog and blocks until the operator starts or exits.
     * Safe to call from any thread (work is marshalled onto the Swing EDT).
     *
     * @param initial           prefill values (current resolved settings)
     * @param initialServerPort prefill for the OCSF listening port
     * @return the operator's choice, or {@code null} if the window was closed
     */
    public static Result showAndGet(DbSettings initial, int initialServerPort) {
        final Result[] out = new Result[1];
        Runnable job = () -> {
            try {
                // Native look (Windows/macOS/GTK) instead of Swing's dated default.
                javax.swing.UIManager.setLookAndFeel(
                        javax.swing.UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // Cosmetic only — the default theme still works.
            }
            ServerConnectionDialog d = new ServerConnectionDialog(initial, initialServerPort);
            d.dialog.setVisible(true);   // modal: returns when disposed
            out[0] = d.result;
        };
        try {
            if (SwingUtilities.isEventDispatchThread()) {
                job.run();
            } else {
                SwingUtilities.invokeAndWait(job);
            }
        } catch (Exception e) {
            System.err.println("[ServerConnectionDialog] could not show dialog: " + e.getMessage());
            return null;
        }
        return out[0];
    }

    private ServerConnectionDialog(DbSettings initial, int initialServerPort) {
        dialog = new JDialog((java.awt.Frame) null, "HSTS Server — Database Connection", true);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        hostField = new JTextField(initial.host(), 18);
        portField = new JTextField(String.valueOf(initial.port()), 6);
        databaseField = new JTextField(initial.database(), 18);
        userField = new JTextField(initial.user(), 18);
        passwordField = new JPasswordField(initial.password(), 18);
        serverPortField = new JTextField(String.valueOf(initialServerPort), 6);
        saveCheckBox = new JCheckBox("Save as defaults (server.properties)");
        statusLabel = new JLabel(" ");
        testButton = new JButton("Test Connection");
        startButton = new JButton("Start Server");

        dialog.setContentPane(buildContent());
        testButton.addActionListener(e -> testConnection());
        startButton.addActionListener(e -> start());

        dialog.pack();
        dialog.setLocationRelativeTo(null);   // centre of screen
    }

    // ===== layout =========================================================

    private JPanel buildContent() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(14, 16, 12, 16));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 4, 4, 4);
        gc.anchor = GridBagConstraints.WEST;
        gc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        JLabel title = new JLabel("Connect the HSTS server to a MySQL database");
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        gc.gridx = 0; gc.gridy = row++; gc.gridwidth = 2;
        panel.add(title, gc);
        gc.gridwidth = 1;

        row = addField(panel, gc, row, "MySQL host:", hostField);
        row = addField(panel, gc, row, "MySQL port:", portField);
        row = addField(panel, gc, row, "Database name:", databaseField);
        row = addField(panel, gc, row, "User:", userField);
        row = addField(panel, gc, row, "Password:", passwordField);
        row = addField(panel, gc, row, "Server (OCSF) port:", serverPortField);

        gc.gridx = 0; gc.gridy = row++; gc.gridwidth = 2;
        panel.add(saveCheckBox, gc);

        gc.gridy = row++;
        panel.add(statusLabel, gc);

        JPanel buttons = new JPanel();
        buttons.add(testButton);
        buttons.add(startButton);
        gc.gridy = row;
        panel.add(buttons, gc);

        return panel;
    }

    private int addField(JPanel panel, GridBagConstraints gc, int row, String label, JTextField field) {
        gc.gridx = 0; gc.gridy = row;
        panel.add(new JLabel(label), gc);
        gc.gridx = 1;
        panel.add(field, gc);
        return row + 1;
    }

    // ===== actions ========================================================

    /** Reads the form into a validated {@link DbSettings}; shows the reason on failure. */
    private DbSettings readSettings() {
        try {
            int port = Integer.parseInt(portField.getText().trim());
            return new DbSettings(
                    hostField.getText(),
                    port,
                    databaseField.getText(),
                    userField.getText(),
                    new String(passwordField.getPassword()));
        } catch (NumberFormatException e) {
            showError("MySQL port must be a number.");
            return null;
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
            return null;
        }
    }

    private Integer readServerPort() {
        try {
            int p = Integer.parseInt(serverPortField.getText().trim());
            if (p < 1 || p > 65535) throw new NumberFormatException();
            return p;
        } catch (NumberFormatException e) {
            showError("Server port must be a number between 1 and 65535.");
            return null;
        }
    }

    /** Tries one real connection on a background thread; the UI stays responsive. */
    private void testConnection() {
        DbSettings settings = readSettings();
        if (settings == null) return;

        setBusy(true, "Testing connection to " + settings.host() + ":" + settings.port() + "…");
        Thread worker = new Thread(() -> {
            String error = tryConnect(settings);
            SwingUtilities.invokeLater(() -> {
                setBusy(false, null);
                if (error == null) {
                    showSuccess("Connected — database '" + settings.database() + "' is reachable.");
                } else {
                    showError(error);
                }
            });
        }, "db-connection-test");
        worker.setDaemon(true);
        worker.start();
    }

    /** @return null on success, otherwise a human-readable failure reason. */
    private static String tryConnect(DbSettings settings) {
        DriverManager.setLoginTimeout(LOGIN_TIMEOUT_SECONDS);
        try (Connection ignored = DriverManager.getConnection(
                settings.jdbcUrl(), settings.user(), settings.password())) {
            return null;
        } catch (Exception e) {
            return "Connection failed: " + e.getMessage();
        }
    }

    private void start() {
        DbSettings settings = readSettings();
        Integer serverPort = readServerPort();
        if (settings == null || serverPort == null) return;
        result = new Result(settings, serverPort, saveCheckBox.isSelected());
        dialog.dispose();
    }

    // ===== feedback helpers (NFR 21: progress + success/error) ===========

    private void setBusy(boolean busy, String message) {
        testButton.setEnabled(!busy);
        startButton.setEnabled(!busy);
        if (message != null) {
            statusLabel.setForeground(Color.DARK_GRAY);
            statusLabel.setText(message);
        }
    }

    private void showError(String message) {
        statusLabel.setForeground(new Color(0xB00020));
        statusLabel.setText(message);
    }

    private void showSuccess(String message) {
        statusLabel.setForeground(new Color(0x1B7A2F));
        statusLabel.setText(message);
    }
}
