package common.network;

import common.entities.ExamSessionStatus;
import common.entities.GradeStatus;

import java.io.Serializable;
import java.time.LocalDateTime;

/** One student attempt in a teacher's results table. */
public class TeacherResultRow implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int gradeId;
    private final int sessionId;
    private final int studentId;
    private final int automaticScore;
    private final Integer finalScore;
    private final GradeStatus gradeStatus;
    private final ExamSessionStatus sessionStatus;
    private final LocalDateTime submittedAt;

    public TeacherResultRow(int gradeId, int sessionId, int studentId,
                            int automaticScore, Integer finalScore,
                            GradeStatus gradeStatus,
                            ExamSessionStatus sessionStatus,
                            LocalDateTime submittedAt) {
        this.gradeId = gradeId;
        this.sessionId = sessionId;
        this.studentId = studentId;
        this.automaticScore = automaticScore;
        this.finalScore = finalScore;
        this.gradeStatus = gradeStatus;
        this.sessionStatus = sessionStatus;
        this.submittedAt = submittedAt;
    }

    public int getGradeId() { return gradeId; }
    public int getSessionId() { return sessionId; }
    public int getStudentId() { return studentId; }
    public int getAutomaticScore() { return automaticScore; }
    public Integer getFinalScore() { return finalScore; }
    public GradeStatus getGradeStatus() { return gradeStatus; }
    public ExamSessionStatus getSessionStatus() { return sessionStatus; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public int getEffectiveScore() { return finalScore == null ? automaticScore : finalScore; }
}
