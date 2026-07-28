package common.network;
import java.io.Serializable;

public class SetStudyBotAvailabilityRequest implements Serializable {
    private static final long serialVersionUID=1L; private int courseId; private boolean available;
    public SetStudyBotAvailabilityRequest(){} public SetStudyBotAvailabilityRequest(int c, boolean a){courseId=c;available=a;}
    public int getCourseId(){return courseId;} public void setCourseId(int v){courseId=v;} public boolean isAvailable(){return available;} public void setAvailable(boolean v){available=v;}
}
