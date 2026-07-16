package common.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.io.Serializable;

/**
 * Represents one question that belongs to an exam.
 *
 * <p>This entity connects an exam to a specific question version.
 * It stores the points assigned to the question and its position
 * inside the exam.</p>
 *
 * <p>The same question may appear in more than one exam.</p>
 */
@Entity
@Table(name = "ExamQuestions")
public class ExamQuestion implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "exam_id", nullable = false)
    private int examId;

    /**
     * The exact ID of the selected question version.
     */
    @Column(name = "question_id", nullable = false)
    private int questionId;

    @Column(name = "points", nullable = false)
    private int points;

    @Column(name = "position", nullable = false)
    private int position;

    public ExamQuestion() {
    }

    /**
     * Constructor used before the exam is saved.
     */
    public ExamQuestion(int questionId, int points, int position) {
        this.questionId = questionId;
        this.points = points;
        this.position = position;
    }

    /**
     * Full constructor used when the exam ID is already known.
     */
    public ExamQuestion(int examId, int questionId, int points, int position) {
        this.examId = examId;
        this.questionId = questionId;
        this.points = points;
        this.position = position;
    }

    // ===== getters / setters =============================================

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getExamId() {
        return examId;
    }

    public void setExamId(int examId) {
        this.examId = examId;
    }

    public int getQuestionId() {
        return questionId;
    }

    public void setQuestionId(int questionId) {
        this.questionId = questionId;
    }

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    @Override
    public String toString() {
        return "ExamQuestion{id=" + id
                + ", examId=" + examId
                + ", questionId=" + questionId
                + ", points=" + points
                + ", position=" + position
                + '}';
    }
}