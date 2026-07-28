package common.entities;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/** Computerized score plus the teacher's approval/override audit trail. */
@Entity
@Table(name = "Grades")
public class Grade implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "session_id", nullable = false, unique = true)
    private int sessionId;

    @Column(name = "exam_id", nullable = false)
    private int examId;

    @Column(name = "student_id", nullable = false)
    private int studentId;

    @Column(name = "auto_score", nullable = false)
    private int autoScore;

    @Column(name = "final_score")
    private Integer finalScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private GradeStatus status;

    @Column(name = "auto_graded_at", nullable = false)
    private LocalDateTime autoGradedAt;

    @Column(name = "approved_by")
    private Integer approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "override_justification", length = 1000)
    private String overrideJustification;

    @Column(name = "teacher_comment", length = 2000)
    private String teacherComment;

    public Grade() { }

    public Grade(int sessionId, int examId, int studentId,
                 int autoScore, LocalDateTime autoGradedAt) {
        this.sessionId = sessionId;
        this.examId = examId;
        this.studentId = studentId;
        this.autoScore = autoScore;
        this.status = GradeStatus.AUTO_GRADED;
        this.autoGradedAt = autoGradedAt;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getSessionId() { return sessionId; }
    public void setSessionId(int sessionId) { this.sessionId = sessionId; }
    public int getExamId() { return examId; }
    public void setExamId(int examId) { this.examId = examId; }
    public int getStudentId() { return studentId; }
    public void setStudentId(int studentId) { this.studentId = studentId; }
    public int getAutoScore() { return autoScore; }
    public void setAutoScore(int autoScore) { this.autoScore = autoScore; }
    public Integer getFinalScore() { return finalScore; }
    public void setFinalScore(Integer finalScore) { this.finalScore = finalScore; }
    public GradeStatus getStatus() { return status; }
    public void setStatus(GradeStatus status) { this.status = status; }
    public LocalDateTime getAutoGradedAt() { return autoGradedAt; }
    public void setAutoGradedAt(LocalDateTime autoGradedAt) { this.autoGradedAt = autoGradedAt; }
    public Integer getApprovedBy() { return approvedBy; }
    public void setApprovedBy(Integer approvedBy) { this.approvedBy = approvedBy; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }
    public String getOverrideJustification() { return overrideJustification; }
    public void setOverrideJustification(String overrideJustification) { this.overrideJustification = overrideJustification; }
    public String getTeacherComment() { return teacherComment; }
    public void setTeacherComment(String teacherComment) { this.teacherComment = teacherComment; }

    /** Score that should be used after the teacher completes the flow. */
    public int getEffectiveScore() {
        return finalScore == null ? autoScore : finalScore;
    }

    /** Students may see a score only after teacher approval or override. */
    public boolean isVisibleToStudent() {
        return status == GradeStatus.APPROVED || status == GradeStatus.OVERRIDDEN;
    }
}
