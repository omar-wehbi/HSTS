package common.network;

import java.io.Serializable;
import java.time.LocalDateTime;

public class StudyBotView implements Serializable {
    private static final long serialVersionUID = 1L;
    private final int courseId; private final String name; private final boolean available;
    private final boolean includeQuestionBank; private final int createdBy; private final LocalDateTime createdAt; private final long sourceCount;
    public StudyBotView(int courseId, String name, boolean available, boolean includeQuestionBank, int createdBy, LocalDateTime createdAt, long sourceCount){
        this.courseId=courseId;this.name=name;this.available=available;this.includeQuestionBank=includeQuestionBank;this.createdBy=createdBy;this.createdAt=createdAt;this.sourceCount=sourceCount;
    }
    public int getCourseId(){return courseId;} public String getName(){return name;} public boolean isAvailable(){return available;}
    public boolean isIncludeQuestionBank(){return includeQuestionBank;} public int getCreatedBy(){return createdBy;} public LocalDateTime getCreatedAt(){return createdAt;} public long getSourceCount(){return sourceCount;}
}
