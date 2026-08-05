package common.entities;

import java.io.Serializable;

/** A subject area that groups courses (semester PDF domain model). */
public class Subject implements Serializable {

    private static final long serialVersionUID = 1L;

    private int id;
    private String name;
    /** Single-digit subject code used in 6-digit exam display ids (0–9). */
    private int code;
    private Integer coordinatorId;

    public Subject() { }

    public Subject(int id, String name, int code, Integer coordinatorId) {
        this.id = id;
        this.name = name;
        this.code = code;
        this.coordinatorId = coordinatorId;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }

    public Integer getCoordinatorId() { return coordinatorId; }
    public void setCoordinatorId(Integer coordinatorId) { this.coordinatorId = coordinatorId; }

    @Override
    public String toString() {
        return name;
    }
}
