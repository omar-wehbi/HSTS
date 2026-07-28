package common.network;
import java.io.Serializable; import java.time.LocalDateTime;

/** One administered exam sitting, including the required completion counters. */
public class ExamExecutionSummary implements Serializable { private static final long serialVersionUID=1L;
 private int releaseId,examId,allocatedMinutes,startedCount,submittedCount,timedOutCount; private String examTitle; private LocalDateTime openTime,closeTime;
 public ExamExecutionSummary(){} public ExamExecutionSummary(int r, int e, String t, LocalDateTime o, LocalDateTime c, int m, int st, int su, int to){releaseId=r;examId=e;examTitle=t;openTime=o;closeTime=c;allocatedMinutes=m;startedCount=st;submittedCount=su;timedOutCount=to;}
 public int getReleaseId(){return releaseId;} public int getExamId(){return examId;} public String getExamTitle(){return examTitle;} public LocalDateTime getOpenTime(){return openTime;} public LocalDateTime getCloseTime(){return closeTime;} public int getAllocatedMinutes(){return allocatedMinutes;} public int getStartedCount(){return startedCount;} public int getSubmittedCount(){return submittedCount;} public int getTimedOutCount(){return timedOutCount;}
}