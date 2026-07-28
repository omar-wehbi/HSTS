package common.entities;

import jakarta.persistence.*;
import java.io.Serializable;

/** A student's selected option for one question in an exam session. */
@Entity
@Table(name = "StudentAnswers")
public class StudentAnswer implements Serializable {
    private static final long serialVersionUID = 1L;
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private int id;
    @Column(name = "session_id", nullable = false) private int sessionId;
    @Column(name = "question_id", nullable = false) private int questionId;
    @Column(name = "selected_answer", nullable = false) private int selectedAnswer;
    public StudentAnswer() { }
    public StudentAnswer(int questionId, int selectedAnswer) { this.questionId = questionId; this.selectedAnswer = selectedAnswer; }
    public int getId() { return id; } public void setId(int id) { this.id = id; }
    public int getSessionId() { return sessionId; } public void setSessionId(int sessionId) { this.sessionId = sessionId; }
    public int getQuestionId() { return questionId; } public void setQuestionId(int questionId) { this.questionId = questionId; }
    public int getSelectedAnswer() { return selectedAnswer; } public void setSelectedAnswer(int selectedAnswer) { this.selectedAnswer = selectedAnswer; }
}
