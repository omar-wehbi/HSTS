package common.network;
import common.entities.StudentAnswer;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class SubmitAnswersRequest implements Serializable {
    private static final long serialVersionUID = 1L;
    private int sessionId;
    private List<StudentAnswer> answers = new ArrayList<>();
    public SubmitAnswersRequest() { }
    public SubmitAnswersRequest(int sessionId, List<StudentAnswer> answers) { this.sessionId = sessionId; setAnswers(answers); }
    public int getSessionId() { return sessionId; } public void setSessionId(int sessionId) { this.sessionId = sessionId; }
    public List<StudentAnswer> getAnswers() { return answers; } public void setAnswers(List<StudentAnswer> answers) { this.answers = answers == null ? new ArrayList<>() : new ArrayList<>(answers); }
}
