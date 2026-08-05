package client.ui.exam;

import common.entities.Exam;
import common.entities.ExamStatus;

/**
 * Human-readable exam status labels for list / detail panes (Person 5).
 */
public final class ExamStatusLabel {

    private ExamStatusLabel() {
    }

    /** Short badge text for an exam's status. */
    public static String badge(ExamStatus status) {
        if (status == null) return "Unknown";
        return switch (status) {
            case DRAFT -> "Draft";
            case PENDING_APPROVAL -> "Pending approval";
            case APPROVED -> "Approved";
            case REJECTED -> "Rejected";
        };
    }

    /**
     * Detail line under the badge. For {@link ExamStatus#REJECTED}, includes
     * the stored rejection reason when present.
     */
    public static String detail(Exam exam) {
        if (exam == null || exam.getStatus() == null) {
            return "";
        }
        if (exam.getStatus() == ExamStatus.REJECTED) {
            String reason = exam.getRejectionReason();
            if (reason != null && !reason.trim().isEmpty()) {
                return "Rejected: " + reason.trim();
            }
            return "Rejected (no reason recorded).";
        }
        if (exam.getStatus() == ExamStatus.PENDING_APPROVAL) {
            return "Waiting for the subject coordinator.";
        }
        if (exam.getStatus() == ExamStatus.APPROVED) {
            return "Approved — ready for release.";
        }
        return "Editable draft.";
    }

    /**
     * Six-digit semester display id: sequence + course code + subject code.
     * Course id doubles as course code when codes are 1–9 (seed convention).
     */
    public static String displayId(Exam exam) {
        if (exam == null) return "000000";
        int family = exam.getBaseId() > 0 ? exam.getBaseId() : Math.max(exam.getId(), 1);
        int courseCode = Math.floorMod(exam.getCourseId(), 10);
        int subjectCode = exam.getSubjectCode() > 0
                ? Math.floorMod(exam.getSubjectCode(), 10)
                : courseCode;
        if (family > 9999) family = family % 10000;
        if (family < 1) family = 1;
        return common.util.ExamDisplayId.format(family, courseCode, subjectCode);
    }
}
