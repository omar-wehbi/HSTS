package common.entities;

import java.io.Serializable;

/**
 * Domain entity for one multiple-choice exam question (Common tier).
 *
 * <p>Mirrors one row of the {@code Questions} table: the question text, four
 * answers, which answer is correct (1..4), an optional image, the owning course,
 * a topic and difficulty (used later for automatic exam generation), plus the
 * versioning fields ({@code baseId}, {@code version}, {@code current}).
 *
 * <p>Implements {@link Serializable} so it can travel inside network messages
 * between the JavaFX client and the server. It is shared, dumb data: the client
 * displays and edits it, but the server decides whether a change is valid and the
 * DAO is the only code that reads/writes it to MySQL.
 */
public class Question implements Serializable {

    /** Keep stable so client and server stay wire-compatible. */
    private static final long serialVersionUID = 1L;

    private int    id;
    private int    courseId;

    private String questionText;
    private String answer1;
    private String answer2;
    private String answer3;
    private String answer4;
    private int    correctAnswer;   // 1..4
    private String imagePath;       // optional; null if none

    private String topic;           // optional
    private String difficulty;      // "EASY" | "MEDIUM" | "HARD" | null

    // ----- versioning -----
    private int     baseId;         // family id shared by all versions of this question
    private int     version;        // 1, 2, 3 ...
    private boolean current;        // true = latest version

    public Question() {
    }

    /** Convenience constructor for creating a NEW question (before it has an id). */
    public Question(int courseId, String questionText,
                    String answer1, String answer2, String answer3, String answer4,
                    int correctAnswer, String imagePath, String topic, String difficulty) {
        this.courseId = courseId;
        this.questionText = questionText;
        this.answer1 = answer1;
        this.answer2 = answer2;
        this.answer3 = answer3;
        this.answer4 = answer4;
        this.correctAnswer = correctAnswer;
        this.imagePath = imagePath;
        this.topic = topic;
        this.difficulty = difficulty;
        this.version = 1;
        this.current = true;
    }

    // ===== getters / setters =============================================

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getCourseId() { return courseId; }
    public void setCourseId(int courseId) { this.courseId = courseId; }

    public String getQuestionText() { return questionText; }
    public void setQuestionText(String questionText) { this.questionText = questionText; }

    public String getAnswer1() { return answer1; }
    public void setAnswer1(String answer1) { this.answer1 = answer1; }

    public String getAnswer2() { return answer2; }
    public void setAnswer2(String answer2) { this.answer2 = answer2; }

    public String getAnswer3() { return answer3; }
    public void setAnswer3(String answer3) { this.answer3 = answer3; }

    public String getAnswer4() { return answer4; }
    public void setAnswer4(String answer4) { this.answer4 = answer4; }

    public int getCorrectAnswer() { return correctAnswer; }
    public void setCorrectAnswer(int correctAnswer) { this.correctAnswer = correctAnswer; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }

    public int getBaseId() { return baseId; }
    public void setBaseId(int baseId) { this.baseId = baseId; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public boolean isCurrent() { return current; }
    public void setCurrent(boolean current) { this.current = current; }

    // ===== convenience ===================================================

    /** @return the text of the correct answer (1..4), or null if out of range. */
    public String getCorrectAnswerText() {
        switch (correctAnswer) {
            case 1:  return answer1;
            case 2:  return answer2;
            case 3:  return answer3;
            case 4:  return answer4;
            default: return null;
        }
    }

    @Override
    public String toString() {
        return "Question{id=" + id
                + ", courseId=" + courseId
                + ", v" + version + (current ? "*" : "")
                + ", text='" + questionText + '\''
                + ", correct=" + correctAnswer + '}';
    }
}
