package common.network;
import java.io.Serializable;

public class StudyBotDocumentSourceRequest implements Serializable {
    private static final long serialVersionUID=1L;
    private int courseId; private String title; private String fileName; private String mimeType; private byte[] fileData;
    public StudyBotDocumentSourceRequest(){}
    public StudyBotDocumentSourceRequest(int courseId, String title, String fileName, String mimeType, byte[] fileData){this.courseId=courseId;this.title=title;this.fileName=fileName;this.mimeType=mimeType;this.fileData=fileData;}
    public int getCourseId(){return courseId;} public void setCourseId(int v){courseId=v;} public String getTitle(){return title;} public void setTitle(String v){title=v;}
    public String getFileName(){return fileName;} public void setFileName(String v){fileName=v;} public String getMimeType(){return mimeType;} public void setMimeType(String v){mimeType=v;}
    public byte[] getFileData(){return fileData;} public void setFileData(byte[] v){fileData=v;}
}
