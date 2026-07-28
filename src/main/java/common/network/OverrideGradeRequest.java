package common.network;

import java.io.Serializable;

/** Teacher request to replace a computerized score with a justified score. */
public class OverrideGradeRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private int gradeId;
    private int newScore;
    private String justification;

    public OverrideGradeRequest() { }

    public OverrideGradeRequest(int gradeId, int newScore, String justification) {
        this.gradeId = gradeId;
        this.newScore = newScore;
        this.justification = justification;
    }

    public int getGradeId() { return gradeId; }
    public void setGradeId(int gradeId) { this.gradeId = gradeId; }
    public int getNewScore() { return newScore; }
    public void setNewScore(int newScore) { this.newScore = newScore; }
    public String getJustification() { return justification; }
    public void setJustification(String justification) { this.justification = justification; }
}
