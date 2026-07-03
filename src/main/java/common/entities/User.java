package common.entities;

import java.io.Serializable;

/**
 * A user account (Common tier). Returned by the server after a successful login
 * so the client can build a role-appropriate menu.
 *
 * <p>Note: the password is NEVER placed in this object — it stays server-side.
 */
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    private int    id;
    private String username;
    private Role   role;
    private String displayName;
    private String idNumber;   // national ID (ת"ז); may be null (only students have one)

    public User() { }

    public User(int id, String username, Role role, String displayName, String idNumber) {
        this.id = id;
        this.username = username;
        this.role = role;
        this.displayName = displayName;
        this.idNumber = idNumber;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getIdNumber() { return idNumber; }
    public void setIdNumber(String idNumber) { this.idNumber = idNumber; }

    @Override
    public String toString() {
        return "User{id=" + id + ", username='" + username + "', role=" + role
                + ", name='" + displayName + "'}";
    }
}
