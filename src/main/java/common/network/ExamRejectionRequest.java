package common.network;

import java.io.Serializable;

/**
 * Request object used when a subject coordinator rejects an exam.
 *
 * <p>The request contains the exam identifier and the mandatory
 * rejection reason that will be stored and shown to the teacher.</p>
 */
public class ExamRejectionRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private int examId;
    private int coordinatorId;
    private String reason;

    public ExamRejectionRequest() {
    }

    public ExamRejectionRequest(int examId, int coordinatorId, String reason) {
        this.examId = examId;
        this.coordinatorId = coordinatorId;
        this.reason = reason;
    }

    // ===== getters / setters =============================================

    public int getExamId() {
        return examId;
    }

    public void setExamId(int examId) {
        this.examId = examId;
    }

    public int getCoordinatorId() {
        return coordinatorId;
    }

    public void setCoordinatorId(int coordinatorId) {
        this.coordinatorId = coordinatorId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    /**
     * @return true if a non-empty rejection reason exists.
     */
    public boolean hasReason() {
        return reason != null && !reason.trim().isEmpty();
    }

    @Override
    public String toString() {
        return "ExamRejectionRequest{"
                + "examId=" + examId
                + ", coordinatorId=" + coordinatorId
                + ", reason='" + reason + '\''
                + '}';
    }
}
