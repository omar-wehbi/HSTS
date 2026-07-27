package common.entities;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** One student's server-controlled attempt at a released exam. */
@Entity
@Table(name = "ExamSessions")
public class ExamSession implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    @Column(name = "release_id", nullable = false) private int releaseId;
    @Column(name = "exam_id", nullable = false) private int examId;
    @Column(name = "student_id", nullable = false) private int studentId;
    @Column(name = "started_at", nullable = false) private LocalDateTime startedAt;
    @Column(name = "deadline", nullable = false) private LocalDateTime deadline;
    @Column(name = "submitted_at") private LocalDateTime submittedAt;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false) private ExamSessionStatus status;
    @Column(name = "extension_minutes", nullable = false) private int extensionMinutes;
    @Column(name = "actual_duration_minutes") private Integer actualDurationMinutes;
    @Transient private List<StudentAnswer> answers = new ArrayList<>();

    public ExamSession() { }
    public ExamSession(int releaseId, int examId, int studentId,
                       LocalDateTime startedAt, LocalDateTime deadline) {
        this.releaseId = releaseId; this.examId = examId; this.studentId = studentId;
        this.startedAt = startedAt; this.deadline = deadline;
        this.status = ExamSessionStatus.IN_PROGRESS;
    }
    public int getId() { return id; } public void setId(int id) { this.id = id; }
    public int getReleaseId() { return releaseId; } public void setReleaseId(int releaseId) { this.releaseId = releaseId; }
    public int getExamId() { return examId; } public void setExamId(int examId) { this.examId = examId; }
    public int getStudentId() { return studentId; } public void setStudentId(int studentId) { this.studentId = studentId; }
    public LocalDateTime getStartedAt() { return startedAt; } public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getDeadline() { return deadline; } public void setDeadline(LocalDateTime deadline) { this.deadline = deadline; }
    public LocalDateTime getSubmittedAt() { return submittedAt; } public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public ExamSessionStatus getStatus() { return status; } public void setStatus(ExamSessionStatus status) { this.status = status; }
    public int getExtensionMinutes() { return extensionMinutes; } public void setExtensionMinutes(int extensionMinutes) { this.extensionMinutes = extensionMinutes; }
    public Integer getActualDurationMinutes() { return actualDurationMinutes; } public void setActualDurationMinutes(Integer v) { actualDurationMinutes=v; }
    public List<StudentAnswer> getAnswers() { if (answers == null) answers = new ArrayList<>(); return answers; }
    public void setAnswers(List<StudentAnswer> answers) { this.answers = answers == null ? new ArrayList<>() : new ArrayList<>(answers); }
    public boolean isActiveAt(LocalDateTime time) { return status == ExamSessionStatus.IN_PROGRESS && time != null && time.isBefore(deadline); }
}
