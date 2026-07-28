package common.network;

import java.io.Serializable;

public class CreateStudyBotRequest implements Serializable {
    private static final long serialVersionUID = 1L;
    private int courseId;
    private String name;
    private boolean includeQuestionBank;
    public CreateStudyBotRequest() { }
    public CreateStudyBotRequest(int courseId, String name, boolean includeQuestionBank) {
        this.courseId=courseId; this.name=name; this.includeQuestionBank=includeQuestionBank;
    }
    public int getCourseId(){return courseId;} public void setCourseId(int v){courseId=v;}
    public String getName(){return name;} public void setName(String v){name=v;}
    public boolean isIncludeQuestionBank(){return includeQuestionBank;} public void setIncludeQuestionBank(boolean v){includeQuestionBank=v;}
}
