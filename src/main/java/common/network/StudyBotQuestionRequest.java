package common.network;

import java.io.Serializable;

public class StudyBotQuestionRequest implements Serializable {
    private static final long serialVersionUID = 1L;
    private int courseId;
    private String question;
    public StudyBotQuestionRequest() { }
    public StudyBotQuestionRequest(int courseId, String question){this.courseId=courseId;this.question=question;}
    public int getCourseId(){return courseId;} public void setCourseId(int v){courseId=v;}
    public String getQuestion(){return question;} public void setQuestion(String v){question=v;}
}
