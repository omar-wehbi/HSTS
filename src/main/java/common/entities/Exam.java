package common.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Domain entity representing an exam in the exam drawer.
 *
 * <p>An exam is created by a teacher, contains a list of questions,
 * has a duration and instructions, and must be approved by a subject
 * coordinator before it can later be released for execution.</p>
 *
 * <p>The entity supports versioning. Editing an existing exam creates
 * a new version while the previous version may remain stored.</p>
 *
 * <p>Implements {@link Serializable} so it can be transferred between
 * the JavaFX client and the server inside network messages.</p>
 */
@Entity
@Table(name = "Exams")
public class Exam implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    /**
     * Family identifier shared by all versions of the same exam.
     */
    @Column(name = "base_id")
    private int baseId;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "is_current", nullable = false)
    private boolean current;

    @Column(name = "course_id", nullable = false)
    private int courseId;

    @Column(name = "teacher_id", nullable = false)
    private int teacherId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "student_instructions")
    private String studentInstructions;

    @Column(name = "teacher_notes")
    private String teacherNotes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ExamStatus status;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "coordinator_id")
    private Integer coordinatorId;

    /**
     * Questions belonging to this exam.
     *
     * <p>This field is deliberately transient because the DAO stores and loads
     * exam questions through the ExamQuestions table separately.</p>
     */
    @Transient
    private List<ExamQuestion> questions;

    public Exam() {
        this.questions = new ArrayList<>();
        this.version = 1;
        this.current = true;
        this.status = ExamStatus.DRAFT;
    }

    /**
     * Convenience constructor for creating a new exam.
     */
    public Exam(int courseId,
                int teacherId,
                String title,
                int durationMinutes,
                String studentInstructions,
                String teacherNotes,
                List<ExamQuestion> questions) {

        this.courseId = courseId;
        this.teacherId = teacherId;
        this.title = title;
        this.durationMinutes = durationMinutes;
        this.studentInstructions = studentInstructions;
        this.teacherNotes = teacherNotes;
        this.questions = questions != null
                ? new ArrayList<>(questions)
                : new ArrayList<>();

        this.version = 1;
        this.current = true;
        this.status = ExamStatus.DRAFT;
    }

    // ===== getters / setters =============================================

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getBaseId() {
        return baseId;
    }

    public void setBaseId(int baseId) {
        this.baseId = baseId;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public boolean isCurrent() {
        return current;
    }

    public void setCurrent(boolean current) {
        this.current = current;
    }

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

    public ExamStatus getStatus() {
        return status;
    }

    public void setStatus(ExamStatus status) {
        this.status = status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public Integer getCoordinatorId() {
        return coordinatorId;
    }

    public void setCoordinatorId(Integer coordinatorId) {
        this.coordinatorId = coordinatorId;
    }

    public List<ExamQuestion> getQuestions() {
        if (questions == null) {
            questions = new ArrayList<>();
        }
        return questions;
    }

    public void setQuestions(List<ExamQuestion> questions) {
        this.questions = questions != null
                ? new ArrayList<>(questions)
                : new ArrayList<>();
    }

    // ===== convenience ===================================================

    /**
     * Calculates the total number of points in the exam.
     *
     * @return sum of the points of all exam questions
     */
    public int getTotalPoints() {
        int total = 0;

        for (ExamQuestion examQuestion : getQuestions()) {
            if (examQuestion != null) {
                total += examQuestion.getPoints();
            }
        }

        return total;
    }

    /**
     * Adds one question to the exam.
     */
    public void addQuestion(ExamQuestion examQuestion) {
        if (examQuestion != null) {
            getQuestions().add(examQuestion);
        }
    }

    /**
     * Removes all questions from the exam.
     */
    public void clearQuestions() {
        getQuestions().clear();
    }

    /**
     * @return true when the exam is still editable by its author
     */
    public boolean isEditable() {
        return status == ExamStatus.DRAFT
                || status == ExamStatus.REJECTED;
    }

    /**
     * @return true when the exam is waiting for coordinator approval
     */
    public boolean isPendingApproval() {
        return status == ExamStatus.PENDING_APPROVAL;
    }

    /**
     * @return true when the exam was approved
     */
    public boolean isApproved() {
        return status == ExamStatus.APPROVED;
    }

    @Override
    public String toString() {
        return "Exam{id=" + id
                + ", baseId=" + baseId
                + ", version=" + version
                + (current ? "*" : "")
                + ", courseId=" + courseId
                + ", teacherId=" + teacherId
                + ", title='" + title + '\''
                + ", durationMinutes=" + durationMinutes
                + ", status=" + status
                + ", totalPoints=" + getTotalPoints()
                + '}';
    }
}