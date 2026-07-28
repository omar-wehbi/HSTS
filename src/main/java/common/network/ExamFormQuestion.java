package common.network;
import java.io.Serializable;

/** Safe question DTO: deliberately excludes the correct answer. */
public class ExamFormQuestion implements Serializable {
    private static final long serialVersionUID = 1L;
    private int questionId, points, position;
    private String text, answer1, answer2, answer3, answer4, imagePath;
    public ExamFormQuestion() { }
    public ExamFormQuestion(int questionId, int points, int position, String text, String answer1, String answer2, String answer3, String answer4, String imagePath) {
        this.questionId=questionId; this.points=points; this.position=position; this.text=text; this.answer1=answer1; this.answer2=answer2; this.answer3=answer3; this.answer4=answer4; this.imagePath=imagePath;
    }
    public int getQuestionId(){return questionId;} public void setQuestionId(int v){questionId=v;}
    public int getPoints(){return points;} public void setPoints(int v){points=v;}
    public int getPosition(){return position;} public void setPosition(int v){position=v;}
    public String getText(){return text;} public void setText(String v){text=v;}
    public String getAnswer1(){return answer1;} public void setAnswer1(String v){answer1=v;}
    public String getAnswer2(){return answer2;} public void setAnswer2(String v){answer2=v;}
    public String getAnswer3(){return answer3;} public void setAnswer3(String v){answer3=v;}
    public String getAnswer4(){return answer4;} public void setAnswer4(String v){answer4=v;}
    public String getImagePath(){return imagePath;} public void setImagePath(String v){imagePath=v;}
}
