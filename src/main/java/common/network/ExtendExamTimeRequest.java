package common.network;
import java.io.Serializable;

/** Extends every currently-running session of one released exam. */
public class ExtendExamTimeRequest implements Serializable {
    private static final long serialVersionUID = 1L;
    private int releaseId;
    private int extraMinutes;
    public ExtendExamTimeRequest() { }
    public ExtendExamTimeRequest(int releaseId, int extraMinutes) { this.releaseId = releaseId; this.extraMinutes = extraMinutes; }
    public int getReleaseId() { return releaseId; } public void setReleaseId(int releaseId) { this.releaseId = releaseId; }
    public int getExtraMinutes() { return extraMinutes; } public void setExtraMinutes(int extraMinutes) { this.extraMinutes = extraMinutes; }
}
