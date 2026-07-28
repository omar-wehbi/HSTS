package client.ui.exam;

import common.network.AutoExamRequest;
import common.network.AutoExamRequirement;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds an {@link AutoExamRequest} from form fields (Person 5).
 */
public final class AutoExamRequestBuilder {

    private int courseId;
    private int teacherId;
    private String title;
    private int durationMinutes;
    private String studentInstructions;
    private String teacherNotes;
    private final List<AutoExamRequirement> requirements = new ArrayList<>();

    public AutoExamRequestBuilder courseId(int courseId) {
        this.courseId = courseId;
        return this;
    }

    public AutoExamRequestBuilder teacherId(int teacherId) {
        this.teacherId = teacherId;
        return this;
    }

    public AutoExamRequestBuilder title(String title) {
        this.title = title;
        return this;
    }

    public AutoExamRequestBuilder durationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
        return this;
    }

    public AutoExamRequestBuilder studentInstructions(String studentInstructions) {
        this.studentInstructions = studentInstructions;
        return this;
    }

    public AutoExamRequestBuilder teacherNotes(String teacherNotes) {
        this.teacherNotes = teacherNotes;
        return this;
    }

    public AutoExamRequestBuilder addRequirement(String topic,
                                                 String difficulty,
                                                 int questionCount,
                                                 int pointsPerQuestion) {
        requirements.add(new AutoExamRequirement(
                topic, difficulty, questionCount, pointsPerQuestion));
        return this;
    }

    public AutoExamRequestBuilder addRequirement(AutoExamRequirement requirement) {
        if (requirement != null) {
            requirements.add(requirement);
        }
        return this;
    }

    public AutoExamRequest build() {
        return new AutoExamRequest(
                courseId,
                teacherId,
                title,
                durationMinutes,
                studentInstructions,
                teacherNotes,
                new ArrayList<>(requirements));
    }
}
