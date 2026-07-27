package common.entities;

import jakarta.persistence.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * A scheduled execution of one approved exam version.
 *
 * <p>The execution code is deliberately stored as text so codes such as
 * {@code 0042} keep their leading zeroes.</p>
 */
@Entity
@Table(name = "ExamReleases")
public class ExamRelease implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "exam_id", nullable = false)
    private int examId;

    @Column(name = "released_by", nullable = false)
    private int releasedBy;

    @Column(name = "execution_code", nullable = false, unique = true, length = 4)
    private String executionCode;

    @Column(name = "open_time", nullable = false)
    private LocalDateTime openTime;

    @Column(name = "close_time", nullable = false)
    private LocalDateTime closeTime;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public ExamRelease() { }

    public ExamRelease(int examId,
                       int releasedBy,
                       String executionCode,
                       LocalDateTime openTime,
                       LocalDateTime closeTime) {
        this.examId = examId;
        this.releasedBy = releasedBy;
        this.executionCode = executionCode;
        this.openTime = openTime;
        this.closeTime = closeTime;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getExamId() { return examId; }
    public void setExamId(int examId) { this.examId = examId; }

    public int getReleasedBy() { return releasedBy; }
    public void setReleasedBy(int releasedBy) { this.releasedBy = releasedBy; }

    public String getExecutionCode() { return executionCode; }
    public void setExecutionCode(String executionCode) { this.executionCode = executionCode; }

    public LocalDateTime getOpenTime() { return openTime; }
    public void setOpenTime(LocalDateTime openTime) { this.openTime = openTime; }

    public LocalDateTime getCloseTime() { return closeTime; }
    public void setCloseTime(LocalDateTime closeTime) { this.closeTime = closeTime; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public boolean isOpenAt(LocalDateTime time) {
        return time != null
                && !time.isBefore(openTime)
                && time.isBefore(closeTime);
    }

    @Override
    public String toString() {
        return "ExamRelease{id=" + id
                + ", examId=" + examId
                + ", releasedBy=" + releasedBy
                + ", executionCode='" + executionCode + '\''
                + ", openTime=" + openTime
                + ", closeTime=" + closeTime
                + '}';
    }
}
