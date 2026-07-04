package common.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.io.Serializable;

/**
 * A user account (Common tier). Returned by the server after a successful login
 * so the client can build a role-appropriate menu.
 *
 * <p>Mapped with JPA annotations for the Hibernate ORM data tier (server-side).
 * Note: the password column is deliberately NOT mapped — this object travels to
 * the client, so the password never leaves the database layer at all
 * (authentication queries the column directly, see {@code UserDAO}).
 */
@Entity
@Table(name = "Users")
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int    id;

    @Column(name = "username", nullable = false, unique = true)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role   role;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "id_number")
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
