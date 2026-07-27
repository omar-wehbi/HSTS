package common.network;

import common.entities.GradeStatus;

import java.io.Serializable;
import java.time.LocalDateTime;

/** One approved result shown in the student's grades list. */
public class StudentResultSummary implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int gradeId;
    private final int sessionId;
    private final int examId;
    private final String examTitle;
    private final int score;
    private final GradeStatus gradeStatus;
    private final LocalDateTime submittedAt;

    public StudentResultSummary(int gradeId, int sessionId, int examId,
                                String examTitle, int score,
                                GradeStatus gradeStatus,
                                LocalDateTime submittedAt) {
        this.gradeId = gradeId;
        this.sessionId = sessionId;
        this.examId = examId;
        this.examTitle = examTitle;
        this.score = score;
        this.gradeStatus = gradeStatus;
        this.submittedAt = submittedAt;
    }

    public int getGradeId() { return gradeId; }
    public int getSessionId() { return sessionId; }
    public int getExamId() { return examId; }
    public String getExamTitle() { return examTitle; }
    public int getScore() { return score; }
    public GradeStatus getGradeStatus() { return gradeStatus; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
}
