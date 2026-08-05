package common.network;

import java.io.Serializable;

/**
 * Filter for {@code GET_PENDING_EXAMS}. Both fields null = all subjects the
 * coordinator manages; {@code subjectId} alone = all courses under that subject;
 * both set = one course (must belong to the subject).
 */
public class PendingExamFilter implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer subjectId;
    private Integer courseId;

    public PendingExamFilter() { }

    public PendingExamFilter(Integer subjectId, Integer courseId) {
        this.subjectId = subjectId;
        this.courseId = courseId;
    }

    public Integer getSubjectId() { return subjectId; }
    public void setSubjectId(Integer subjectId) { this.subjectId = subjectId; }

    public Integer getCourseId() { return courseId; }
    public void setCourseId(Integer courseId) { this.courseId = courseId; }

    @Override
    public String toString() {
        return "PendingExamFilter{subjectId=" + subjectId + ", courseId=" + courseId + '}';
    }
}
