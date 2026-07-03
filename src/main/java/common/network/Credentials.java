package common.network;

import java.io.Serializable;

/**
 * Login payload sent from client to server with a {@code LOGIN} message.
 * Carries only what the server needs to authenticate.
 */
public class Credentials implements Serializable {

    private static final long serialVersionUID = 1L;

    private String username;
    private String password;

    public Credentials() { }

    public Credentials(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
