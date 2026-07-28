package common.entities;

import java.io.Serializable;

/** Lifecycle of a computerized exam grade. */
public enum GradeStatus implements Serializable {
    AUTO_GRADED,
    APPROVED,
    OVERRIDDEN
}
