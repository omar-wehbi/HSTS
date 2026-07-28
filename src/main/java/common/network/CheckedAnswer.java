package common.network;

import java.io.Serializable;

/** One row in the checked exam returned after teacher approval. */
public class CheckedAnswer implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int questionId;
    private final String questionText;
    private final String answer1;
    private final String answer2;
    private final String answer3;
    private final String answer4;
    private final Integer selectedAnswer;
    private final int correctAnswer;
    private final int points;
    private final boolean correct;

    public CheckedAnswer(int questionId, String questionText,
                         String answer1, String answer2, String answer3, String answer4,
                         Integer selectedAnswer, int correctAnswer,
                         int points, boolean correct) {
        this.questionId = questionId;
        this.questionText = questionText;
        this.answer1 = answer1;
        this.answer2 = answer2;
        this.answer3 = answer3;
        this.answer4 = answer4;
        this.selectedAnswer = selectedAnswer;
        this.correctAnswer = correctAnswer;
        this.points = points;
        this.correct = correct;
    }

    public int getQuestionId() { return questionId; }
    public String getQuestionText() { return questionText; }
    public String getAnswer1() { return answer1; }
    public String getAnswer2() { return answer2; }
    public String getAnswer3() { return answer3; }
    public String getAnswer4() { return answer4; }
    public Integer getSelectedAnswer() { return selectedAnswer; }
    public int getCorrectAnswer() { return correctAnswer; }
    public int getPoints() { return points; }
    public boolean isCorrect() { return correct; }
}
