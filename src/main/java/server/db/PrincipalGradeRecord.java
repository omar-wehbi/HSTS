package server.db;
/** Final grade projection with exam and sitting identifiers for comparison reports. */
public class PrincipalGradeRecord { private final int teacherId,courseId,studentId,examId,releaseId,score; private final String teacherName,courseName,studentName,examTitle;
 public PrincipalGradeRecord(int t, String tn, int c, String cn, int s, String sn, int score){this(t,tn,c,cn,s,sn,0,0,"",score);} public PrincipalGradeRecord(int t, String tn, int c, String cn, int s, String sn, int e, int r, String et, int score){teacherId=t;teacherName=tn;courseId=c;courseName=cn;studentId=s;studentName=sn;examId=e;releaseId=r;examTitle=et;this.score=score;}
 public int getTeacherId(){return teacherId;}public String getTeacherName(){return teacherName;}public int getCourseId(){return courseId;}public String getCourseName(){return courseName;}public int getStudentId(){return studentId;}public String getStudentName(){return studentName;}public int getExamId(){return examId;}public int getReleaseId(){return releaseId;}public String getExamTitle(){return examTitle;}public int getScore(){return score;}
}
