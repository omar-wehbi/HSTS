package common.util;

/**
 * The 5-digit question display id from the semester document (Person 2, Phase 3):
 * four digits of question sequence followed by one course-code digit, e.g.
 * {@code 10017} = sequence {@code 1001} in the course with code {@code 7} —
 * exactly the scheme the Assignment 1 acceptance fixtures use.
 *
 * <p>This is a <em>presentation</em> id: the database keeps its own surrogate
 * keys ({@code id}/{@code base_id}); screens format them for display and parse
 * user input back. Keeping the codec here in {@code common} lets both tiers use
 * it (client tables, server reports) without duplicating the rule.
 *
 * <p>Demo convention until the team adds a {@code course_code} column: the
 * course <b>id</b> doubles as the course code (seed courses are 1–3, single
 * digit). Documented in {@code schema/README.md}; revisit if a real coding
 * scheme arrives from the course-management side (R-008: course numbers come
 * from an external system).
 */
public final class DisplayId {

    /** Parsed halves of a display id. */
    public record Parts(int sequence, int courseCode) { }

    private DisplayId() { }

    /**
     * @param sequence   question sequence, 1..9999 (four digits, zero-padded)
     * @param courseCode single-digit course code, 0..9
     * @return the 5-character display id
     */
    public static String format(int sequence, int courseCode) {
        if (sequence < 1 || sequence > 9999) {
            throw new IllegalArgumentException("sequence must be 1..9999, got: " + sequence);
        }
        if (courseCode < 0 || courseCode > 9) {
            throw new IllegalArgumentException("course code must be 0..9, got: " + courseCode);
        }
        return String.format("%04d%d", sequence, courseCode);
    }

    /**
     * @param displayId exactly five digits
     * @return the sequence and course code encoded in it
     * @throws IllegalArgumentException if the input is not a 5-digit string
     */
    public static Parts parse(String displayId) {
        if (displayId == null || !displayId.matches("\\d{5}")) {
            throw new IllegalArgumentException("display id must be exactly 5 digits, got: " + displayId);
        }
        return new Parts(Integer.parseInt(displayId.substring(0, 4)),
                         Integer.parseInt(displayId.substring(4)));
    }
}
