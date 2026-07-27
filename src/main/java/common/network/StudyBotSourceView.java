package common.network;
import java.io.Serializable;
import java.time.LocalDateTime;

public class StudyBotSourceView implements Serializable {
    private static final long serialVersionUID=1L;
    private final int id,courseId,updatedBy; private final String title,content,originalFileName; private final StudyBotSourceType sourceType; private final LocalDateTime updatedAt;
    public StudyBotSourceView(int id, int courseId, String title, String content, int updatedBy, LocalDateTime updatedAt){this(id,courseId,title,content,StudyBotSourceType.TEXT,null,updatedBy,updatedAt);}
    public StudyBotSourceView(int id, int courseId, String title, String content, StudyBotSourceType sourceType, String originalFileName, int updatedBy, LocalDateTime updatedAt){this.id=id;this.courseId=courseId;this.title=title;this.content=content;this.sourceType=sourceType;this.originalFileName=originalFileName;this.updatedBy=updatedBy;this.updatedAt=updatedAt;}
    public int getId(){return id;} public int getCourseId(){return courseId;} public String getTitle(){return title;} public String getContent(){return content;}
    public StudyBotSourceType getSourceType(){return sourceType;} public String getOriginalFileName(){return originalFileName;} public int getUpdatedBy(){return updatedBy;} public LocalDateTime getUpdatedAt(){return updatedAt;}
}
