package common.network;
import java.io.Serializable;
import java.time.LocalDateTime;

public class StudyBotAnswer implements Serializable {
    private static final long serialVersionUID=1L;
    private final int historyId,courseId; private final String question,answer,status; private final LocalDateTime askedAt;
    public StudyBotAnswer(int historyId, int courseId, String question, String answer, LocalDateTime askedAt){this(historyId,courseId,question,answer,"ANSWERED",askedAt);}
    public StudyBotAnswer(int historyId, int courseId, String question, String answer, String status, LocalDateTime askedAt){this.historyId=historyId;this.courseId=courseId;this.question=question;this.answer=answer;this.status=status;this.askedAt=askedAt;}
    public int getHistoryId(){return historyId;} public int getCourseId(){return courseId;} public String getQuestion(){return question;} public String getAnswer(){return answer;} public String getStatus(){return status;} public LocalDateTime getAskedAt(){return askedAt;}
}
