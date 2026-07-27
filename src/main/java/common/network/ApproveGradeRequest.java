package common.network;
import java.io.Serializable;

/** Approves an automatic grade and optionally adds feedback visible to the student. */
public class ApproveGradeRequest implements Serializable {
 private static final long serialVersionUID=1L; private int gradeId; private String teacherComment;
 public ApproveGradeRequest(){} public ApproveGradeRequest(int gradeId, String teacherComment){this.gradeId=gradeId;this.teacherComment=teacherComment;}
 public int getGradeId(){return gradeId;} public void setGradeId(int v){gradeId=v;} public String getTeacherComment(){return teacherComment;} public void setTeacherComment(String v){teacherComment=v;}
}