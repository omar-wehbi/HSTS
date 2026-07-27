package common.network;

import common.entities.GradeStatus;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Student-safe checked exam form, available only after grade approval. */
public class CheckedExamResult implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int gradeId;
    private final int sessionId;
    private final int examId;
    private final String examTitle;
    private final int score;
    private final GradeStatus gradeStatus;
    private final String overrideJustification;
    private final String teacherComment;
    private final List<CheckedAnswer> answers;

    public CheckedExamResult(int gradeId, int sessionId, int examId,
                             String examTitle, int score,
                             GradeStatus gradeStatus,
                             String overrideJustification, String teacherComment,
                             List<CheckedAnswer> answers) {
        this.gradeId = gradeId;
        this.sessionId = sessionId;
        this.examId = examId;
        this.examTitle = examTitle;
        this.score = score;
        this.gradeStatus = gradeStatus;
        this.overrideJustification = overrideJustification;
        this.teacherComment = teacherComment;
        this.answers = answers == null ? new ArrayList<>() : new ArrayList<>(answers);
    }

    public int getGradeId() { return gradeId; }
    public int getSessionId() { return sessionId; }
    public int getExamId() { return examId; }
    public String getExamTitle() { return examTitle; }
    public int getScore() { return score; }
    public GradeStatus getGradeStatus() { return gradeStatus; }
    public String getOverrideJustification() { return overrideJustification; }
    public String getTeacherComment() { return teacherComment; }
    public List<CheckedAnswer> getAnswers() { return new ArrayList<>(answers); }
}
