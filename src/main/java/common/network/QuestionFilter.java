package common.network;

import java.io.Serializable;

/**
 * Filter criteria for querying the question bank (Person 2, Phase 3).
 *
 * <p><b>Pattern: DTO</b> — dumb serializable data travelling inside a
 * {@link Message} with command {@code GET_QUESTIONS_FILTERED}. The same shape
 * serves everyone who needs a question pool: the teacher browsing the bank,
 * Person 3's automatic exam builder (count per topic/difficulty, scenario 3.4),
 * and the study bot selecting course source material (scenarios 13–14).
 *
 * <p>{@code topic} and {@code difficulty} may be {@code null} (or blank) to mean
 * "no filter on that axis"; {@code courseId} is always required because every
 * question belongs to exactly one course.
 */
public class QuestionFilter implements Serializable {

    /** Keep stable so client and server stay wire-compatible. */
    private static final long serialVersionUID = 1L;

    private int    courseId;
    private String topic;        // null/blank = any topic
    private String difficulty;   // null/blank = any difficulty ("EASY"|"MEDIUM"|"HARD")

    public QuestionFilter() { }

    public QuestionFilter(int courseId, String topic, String difficulty) {
        this.courseId = courseId;
        this.topic = topic;
        this.difficulty = difficulty;
    }

    public int getCourseId() { return courseId; }
    public void setCourseId(int courseId) { this.courseId = courseId; }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }

    @Override
    public String toString() {
        return "QuestionFilter{course=" + courseId
                + ", topic=" + topic + ", difficulty=" + difficulty + '}';
    }
}
