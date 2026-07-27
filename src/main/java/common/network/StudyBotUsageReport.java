package common.network;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

public class StudyBotUsageReport implements Serializable {
    private static final long serialVersionUID=1L;
    public static class Entry implements Serializable {private static final long serialVersionUID=1L;private final String question;private final LocalDateTime askedAt;public Entry(String q,LocalDateTime a){question=q;askedAt=a;}public String getQuestion(){return question;}public LocalDateTime getAskedAt(){return askedAt;}}
    private final int courseId; private final long totalQuestions,uniqueStudents; private final List<Entry> recentQuestions; private final List<CommonQuestion> commonQuestions;
    public StudyBotUsageReport(int c, long t, long u, List<Entry> r){this(c,t,u,r,List.of());}
    public StudyBotUsageReport(int c, long t, long u, List<Entry> r, List<CommonQuestion> common){courseId=c;totalQuestions=t;uniqueStudents=u;recentQuestions=r;commonQuestions=common;}
    public int getCourseId(){return courseId;} public long getTotalQuestions(){return totalQuestions;} public long getUniqueStudents(){return uniqueStudents;} public List<Entry> getRecentQuestions(){return recentQuestions;} public List<CommonQuestion> getCommonQuestions(){return commonQuestions;}
}
