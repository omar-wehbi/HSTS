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
            return "Approved — ready for release (Person 4).";
        }
        return "Editable draft.";
    }

    /**
     * Display id until Person 3 ships a 6-digit codec: {@code #} + {@code baseId}
     * (falls back to row {@code id} when baseId is unset).
     */
    public static String displayId(Exam exam) {
        if (exam == null) return "#?";
        int family = exam.getBaseId() > 0 ? exam.getBaseId() : exam.getId();
        return "#" + family;
    }
}
