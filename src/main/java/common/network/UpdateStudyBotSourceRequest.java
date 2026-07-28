package common.network;

import java.io.Serializable;

public class UpdateStudyBotSourceRequest implements Serializable {
    private static final long serialVersionUID = 1L;
    private int sourceId;
    private String title;
    private String content;
    public UpdateStudyBotSourceRequest() { }
    public UpdateStudyBotSourceRequest(int sourceId, String title, String content) {
        this.sourceId=sourceId; this.title=title; this.content=content;
    }
    public int getSourceId(){return sourceId;} public void setSourceId(int v){sourceId=v;}
    public String getTitle(){return title;} public void setTitle(String v){title=v;}
    public String getContent(){return content;} public void setContent(String v){content=v;}
}
