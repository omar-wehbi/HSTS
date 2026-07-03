package common.entities;

/**
 * The four user roles in the system (Common tier). The role decides which menu
 * and features a user sees after login (scenario 1).
 *
 * <p>Hebrew: STUDENT = תלמידה, TEACHER = מורה, COORDINATOR = רכזת, PRINCIPAL = מנהלת.
 */
public enum Role {
    STUDENT,
    TEACHER,
    COORDINATOR,
    PRINCIPAL
}
