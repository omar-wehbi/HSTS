package common.network;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Request object used for automatic exam generation.
 *
 * <p>The request contains the exam details and a list of requirements.
 * Each requirement defines how many questions should be selected
 * for a specific topic and difficulty.</p>
 */
public class AutoExamRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private int courseId;
    private int teacherId;
    private String title;
    private int durationMinutes;
    private String studentInstructions;
    private String teacherNotes;
    private List<AutoExamRequirement> requirements;

    public AutoExamRequest() {
        this.requirements = new ArrayList<>();
    }

    public AutoExamRequest(int courseId,
                           int teacherId,
                           String title,
                           int durationMinutes,
                           String studentInstructions,
                           String teacherNotes,
                           List<AutoExamRequirement> requirements) {

        this.courseId = courseId;
        this.teacherId = teacherId;
        this.title = title;
        this.durationMinutes = durationMinutes;
        this.studentInstructions = studentInstructions;
        this.teacherNotes = teacherNotes;
        this.requirements = requirements != null
                ? new ArrayList<>(requirements)
                : new ArrayList<>();
    }

    // ===== getters / setters =============================================

    public int getCourseId() {
        return courseId;
    }

    public void setCourseId(int courseId) {
        this.courseId = courseId;
    }

    public int getTeacherId() {
        return teacherId;
    }

    public void setTeacherId(int teacherId) {
        this.teacherId = teacherId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public String getStudentInstructions() {
        return studentInstructions;
    }

    public void setStudentInstructions(String studentInstructions) {
        this.studentInstructions = studentInstructions;
    }

    public String getTeacherNotes() {
        return teacherNotes;
    }

    public void setTeacherNotes(String teacherNotes) {
        this.teacherNotes = teacherNotes;
    }

    public List<AutoExamRequirement> getRequirements() {
        if (requirements == null) {
            requirements = new ArrayList<>();
        }

        return requirements;
    }

    public void setRequirements(List<AutoExamRequirement> requirements) {
        this.requirements = requirements != null
                ? new ArrayList<>(requirements)
                : new ArrayList<>();
    }

    // ===== convenience ===================================================

    /**
     * Adds one automatic-generation requirement.
     */
    public void addRequirement(AutoExamRequirement requirement) {
        if (requirement != null) {
            getRequirements().add(requirement);
        }
    }

    /**
     * Calculates the total number of questions requested.
     */
    public int getTotalQuestionCount() {
        int total = 0;

        for (AutoExamRequirement requirement : getRequirements()) {
            if (requirement != null) {
                total += requirement.getQuestionCount();
            }
        }

        return total;
    }

    /**
     * Calculates the total number of points requested.
     */
    public int getTotalPoints() {
        int total = 0;

        for (AutoExamRequirement requirement : getRequirements()) {
            if (requirement != null) {
                total += requirement.getTotalPoints();
            }
        }

        return total;
    }

    @Override
    public String toString() {
        return "AutoExamRequest{"
                + "courseId=" + courseId
                + ", teacherId=" + teacherId
                + ", title='" + title + '\''
                + ", durationMinutes=" + durationMinutes
                + ", requirements=" + getRequirements().size()
                + ", totalQuestionCount=" + getTotalQuestionCount()
                + ", totalPoints=" + getTotalPoints()
                + '}';
    }
}
