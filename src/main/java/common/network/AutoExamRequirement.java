package common.network;

import java.io.Serializable;

/**
 * Represents one requirement for automatic exam generation.
 *
 * <p>Each requirement defines how many questions should be selected
 * for a specific topic and difficulty level, and how many points
 * each selected question is worth.</p>
 */
public class AutoExamRequirement implements Serializable {

    private static final long serialVersionUID = 1L;

    private String topic;
    private String difficulty;
    private int questionCount;
    private int pointsPerQuestion;

    public AutoExamRequirement() {
    }

    public AutoExamRequirement(String topic,
                               String difficulty,
                               int questionCount,
                               int pointsPerQuestion) {
        this.topic = topic;
        this.difficulty = difficulty;
        this.questionCount = questionCount;
        this.pointsPerQuestion = pointsPerQuestion;
    }

    // ===== getters / setters =============================================

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = difficulty;
    }

    public int getQuestionCount() {
        return questionCount;
    }

    public void setQuestionCount(int questionCount) {
        this.questionCount = questionCount;
    }

    public int getPointsPerQuestion() {
        return pointsPerQuestion;
    }

    public void setPointsPerQuestion(int pointsPerQuestion) {
        this.pointsPerQuestion = pointsPerQuestion;
    }

    // ===== convenience ===================================================

    /**
     * Calculates the total number of points contributed by this requirement.
     */
    public int getTotalPoints() {
        return questionCount * pointsPerQuestion;
    }

    @Override
    public String toString() {
        return "AutoExamRequirement{"
                + "topic='" + topic + '\''
                + ", difficulty='" + difficulty + '\''
                + ", questionCount=" + questionCount
                + ", pointsPerQuestion=" + pointsPerQuestion
                + ", totalPoints=" + getTotalPoints()
                + '}';
    }
}