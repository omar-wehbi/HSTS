package common.entities;

/**
 * Represents the current status of an exam
 * while it is stored in the exam drawer.
 */
public enum ExamStatus {

    /**
     * The teacher is still creating or editing the exam.
     */
    DRAFT,

    /**
     * The exam was submitted by the teacher
     * and is waiting for coordinator approval.
     */
    PENDING_APPROVAL,

    /**
     * The exam was approved by the subject coordinator.
     */
    APPROVED,

    /**
     * The exam was rejected by the subject coordinator.
     * A rejection reason must be stored.
     */
    REJECTED
}