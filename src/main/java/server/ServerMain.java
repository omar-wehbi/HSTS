package server;

import server.config.DbSettings;
import server.config.ServerConfig;
import server.db.DatabaseConfig;
import server.ui.ServerConnectionDialog;

import java.awt.GraphicsEnvironment;
import java.io.IOException;

/**
 * Entry point for the HSTS Fat Server (Logic tier).
 *
 * <p>Startup flow (Phase 0.5, Person 2):
 * <ol>
 *   <li>Resolve the database profile ({@code hsts.db.*} system properties →
 *       {@code server.properties} → defaults).</li>
 *   <li>Unless {@code --no-gui} was passed (or the machine is headless), show
 *       the {@link ServerConnectionDialog} so the operator can adjust and
 *       test the connection — the server can run against <em>any</em> MySQL,
 *       not just the machine it was configured on.</li>
 *   <li>Pin the chosen settings via {@link DatabaseConfig#configure(DbSettings)}
 *       <b>before</b> constructing {@link HSTSServer}, whose constructor
 *       fail-fast boots the Hibernate SessionFactory.</li>
 *   <li>Listen for OCSF clients.</li>
 * </ol>
 *
 * <p>Usage: {@code java -jar G12_Server.jar [port] [--no-gui]}
 */
public class ServerMain {

    /** Default OCSF listening port for the prototype. */
    private static final int DEFAULT_PORT = 5555;

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        boolean noGui = false;

        for (String arg : args) {
            if ("--no-gui".equalsIgnoreCase(arg)) {
                noGui = true;
            } else {
                try {
                    port = Integer.parseInt(arg);
                } catch (NumberFormatException e) {
                    System.err.println("Invalid argument '" + arg + "' — expected a port number or --no-gui.");
                }
            }
        }

        if (!noGui && !GraphicsEnvironment.isHeadless()) {
            ServerConnectionDialog.Result choice =
                    ServerConnectionDialog.showAndGet(DatabaseConfig.getSettings(), port);
            if (choice == null) {
                System.out.println("[ServerMain] Connection dialog closed — server not started.");
                return;
            }
            DatabaseConfig.configure(choice.settings());
            port = choice.serverPort();
            if (choice.saveRequested()) {
                saveDefaults(choice.settings());
            }
        }
        // Headless / --no-gui: settings resolve exactly as before the dialog
        // existed (system properties -> server.properties -> defaults), so
        // scripts, tests and CI behave unchanged.

        HSTSServer server = new HSTSServer(port);
        try {
            server.listen();
            System.out.println("==================================================");
            System.out.println(" HSTS Fat Server is UP on port " + port);
            System.out.println(" Database: " + DatabaseConfig.getSettings());
            System.out.println(" Acting as the SECURE GATEKEEPER for all DB access.");
            System.out.println(" Clients never touch MySQL directly — every request");
            System.out.println(" is routed and validated here.");
            System.out.println("==================================================");
        } catch (IOException e) {
            System.err.println("Could not listen on port " + port + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** Persists the dialog's settings beside the JAR; a failure only warns — the server still starts. */
    private static void saveDefaults(DbSettings settings) {
        try {
            ServerConfig.save(settings, ServerConfig.externalConfigPath());
            System.out.println("[ServerMain] Saved settings to " + ServerConfig.externalConfigPath());
        } catch (IOException e) {
            System.err.println("[ServerMain] Could not save server.properties: " + e.getMessage());
        }
    }
}
