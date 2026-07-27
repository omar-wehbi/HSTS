package common.network;

import java.io.Serializable;

public class StudyBotSourceRequest implements Serializable {
    private static final long serialVersionUID = 1L;
    private int courseId;
    private String title;
    private String content;

    public StudyBotSourceRequest() { }
    public StudyBotSourceRequest(int courseId, String title, String content) {
        this.courseId = courseId; this.title = title; this.content = content;
    }
    public int getCourseId() { return courseId; }
    public void setCourseId(int courseId) { this.courseId = courseId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
