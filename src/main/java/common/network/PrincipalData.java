package common.network;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Read-only catalog available to a principal for building reports. */
public class PrincipalData implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<PrincipalDataItem> teachers;
    private List<PrincipalDataItem> courses;
    private List<PrincipalDataItem> students;
    private int gradedAttempts;

    public PrincipalData() {
        teachers = new ArrayList<>(); courses = new ArrayList<>(); students = new ArrayList<>();
    }

    public PrincipalData(List<PrincipalDataItem> teachers, List<PrincipalDataItem> courses,
                         List<PrincipalDataItem> students, int gradedAttempts) {
        this.teachers = copy(teachers); this.courses = copy(courses); this.students = copy(students);
        this.gradedAttempts = gradedAttempts;
    }

    private static List<PrincipalDataItem> copy(List<PrincipalDataItem> value) {
        return value == null ? new ArrayList<>() : new ArrayList<>(value);
    }
    public List<PrincipalDataItem> getTeachers() { return copy(teachers); }
    public void setTeachers(List<PrincipalDataItem> teachers) { this.teachers = copy(teachers); }
    public List<PrincipalDataItem> getCourses() { return copy(courses); }
    public void setCourses(List<PrincipalDataItem> courses) { this.courses = copy(courses); }
    public List<PrincipalDataItem> getStudents() { return copy(students); }
    public void setStudents(List<PrincipalDataItem> students) { this.students = copy(students); }
    public int getGradedAttempts() { return gradedAttempts; }
    public void setGradedAttempts(int gradedAttempts) { this.gradedAttempts = gradedAttempts; }
}
