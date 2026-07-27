package server.db;
/** Immutable question snapshot captured when an approved exam is released. */
public class ExamSnapshotQuestion { private final int questionId,points,position,correctAnswer; private final String text,a1,a2,a3,a4,imagePath;
 public ExamSnapshotQuestion(int q, int p, int pos, String t, String a1, String a2, String a3, String a4, int c, String img){questionId=q;points=p;position=pos;text=t;this.a1=a1;this.a2=a2;this.a3=a3;this.a4=a4;correctAnswer=c;imagePath=img;}
 public int getQuestionId(){return questionId;} public int getPoints(){return points;} public int getPosition(){return position;} public String getText(){return text;} public String getA1(){return a1;} public String getA2(){return a2;} public String getA3(){return a3;} public String getA4(){return a4;} public int getCorrectAnswer(){return correctAnswer;} public String getImagePath(){return imagePath;}
}