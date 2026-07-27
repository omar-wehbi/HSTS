package common.network;

import java.io.Serializable;

/** Read-only selectable item shown in the principal report screen. */
public class PrincipalDataItem implements Serializable {
    private static final long serialVersionUID = 1L;

    private int id;
    private String name;

    public PrincipalDataItem() { }
    public PrincipalDataItem(int id, String name) { this.id = id; this.name = name; }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
