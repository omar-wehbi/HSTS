package common.network;
import common.entities.ExamSession;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Payload returned after START_EXAM_SESSION. */
public class ExamForm implements Serializable {
    private static final long serialVersionUID = 1L;
    private ExamSession session;
    private String title, instructions;
    private List<ExamFormQuestion> questions = new ArrayList<>();
    public ExamForm() { }
    public ExamForm(ExamSession session, String title, String instructions, List<ExamFormQuestion> questions) { this.session=session; this.title=title; this.instructions=instructions; this.questions=questions==null?new ArrayList<>():new ArrayList<>(questions); }
    public ExamSession getSession(){return session;} public void setSession(ExamSession v){session=v;}
    public String getTitle(){return title;} public void setTitle(String v){title=v;}
    public String getInstructions(){return instructions;} public void setInstructions(String v){instructions=v;}
    public List<ExamFormQuestion> getQuestions(){return questions;} public void setQuestions(List<ExamFormQuestion> v){questions=v==null?new ArrayList<>():new ArrayList<>(v);}
}
