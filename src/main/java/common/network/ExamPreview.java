package common.network;

import java.io.Serializable;

/**
 * Read-only preview of an open exam release (before the student enters their ID
 * and starts the timed session). Answers are not included.
 */
public class ExamPreview implements Serializable {

    private static final long serialVersionUID = 1L;

    private int releaseId;
    private int examId;
    private String examTitle;
    private String studentInstructions;
    private int durationMinutes;
    private int questionCount;
    private String executionCode;

    public ExamPreview() { }

    public ExamPreview(int releaseId, int examId, String examTitle,
                       String studentInstructions, int durationMinutes,
                       int questionCount, String executionCode) {
        this.releaseId = releaseId;
        this.examId = examId;
        this.examTitle = examTitle;
        this.studentInstructions = studentInstructions;
        this.durationMinutes = durationMinutes;
        this.questionCount = questionCount;
        this.executionCode = executionCode;
    }

    public int getReleaseId() { return releaseId; }
    public void setReleaseId(int releaseId) { this.releaseId = releaseId; }

    public int getExamId() { return examId; }
    public void setExamId(int examId) { this.examId = examId; }

    public String getExamTitle() { return examTitle; }
    public void setExamTitle(String examTitle) { this.examTitle = examTitle; }

    public String getStudentInstructions() { return studentInstructions; }
    public void setStudentInstructions(String studentInstructions) {
        this.studentInstructions = studentInstructions;
    }

    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public int getQuestionCount() { return questionCount; }
    public void setQuestionCount(int questionCount) { this.questionCount = questionCount; }

    public String getExecutionCode() { return executionCode; }
    public void setExecutionCode(String executionCode) { this.executionCode = executionCode; }
}
