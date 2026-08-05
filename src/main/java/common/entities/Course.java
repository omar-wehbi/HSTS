package common.entities;

import java.io.Serializable;

/**
 * Domain entity for a course (Common tier). A question belongs to one course.
 */
public class Course implements Serializable {

    private static final long serialVersionUID = 1L;

    private int    id;
    private String name;
    private Integer subjectId;
    private Integer subjectCode;

    public Course() { }

    public Course(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public Course(int id, String name, Integer subjectId, Integer subjectCode) {
        this.id = id;
        this.name = name;
        this.subjectId = subjectId;
        this.subjectCode = subjectCode;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getSubjectId() { return subjectId; }
    public void setSubjectId(Integer subjectId) { this.subjectId = subjectId; }

    public Integer getSubjectCode() { return subjectCode; }
    public void setSubjectCode(Integer subjectCode) { this.subjectCode = subjectCode; }

    /** Shown in list cells / combo boxes. */
    @Override
    public String toString() {
        return name;
    }
}
