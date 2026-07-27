package common.network;
import java.io.Serializable;

public class CommonQuestion implements Serializable {
    private static final long serialVersionUID=1L; private final String question; private final long count;
    public CommonQuestion(String question, long count){this.question=question;this.count=count;} public String getQuestion(){return question;} public long getCount(){return count;}
}
