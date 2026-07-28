package common.network;

import java.io.Serializable;
import java.time.LocalDateTime;

/** Client request for releasing an approved exam. */
public class ExamReleaseRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private int examId;
    private String executionCode;
    private LocalDateTime openTime;
    private LocalDateTime closeTime;

    public ExamReleaseRequest() { }

    public ExamReleaseRequest(int examId,
                              String executionCode,
                              LocalDateTime openTime,
                              LocalDateTime closeTime) {
        this.examId = examId;
        this.executionCode = executionCode;
        this.openTime = openTime;
        this.closeTime = closeTime;
    }

    public int getExamId() { return examId; }
    public void setExamId(int examId) { this.examId = examId; }

    public String getExecutionCode() { return executionCode; }
    public void setExecutionCode(String executionCode) { this.executionCode = executionCode; }

    public LocalDateTime getOpenTime() { return openTime; }
    public void setOpenTime(LocalDateTime openTime) { this.openTime = openTime; }

    public LocalDateTime getCloseTime() { return closeTime; }
    public void setCloseTime(LocalDateTime closeTime) { this.closeTime = closeTime; }
}
