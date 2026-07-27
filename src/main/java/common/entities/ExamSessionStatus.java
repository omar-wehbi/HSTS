package common.entities;

import java.io.Serializable;

/** Lifecycle of one student's attempt at a released exam. */
public enum ExamSessionStatus implements Serializable {
    IN_PROGRESS,
    SUBMITTED,
    TIMED_OUT
}
