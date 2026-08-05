package common.util;

/**
 * Six-digit exam display id from the semester document:
 * four digits of exam sequence ({@code base_id}) + one course-code digit
 * + one subject-code digit, e.g. {@code 000171} = sequence 1, course 7, subject 1.
 *
 * <p>Presentation only — the database keeps surrogate keys. Course/subject codes
 * are single digits (0–9); seed courses use course id as the course code.
 */
public final class ExamDisplayId {

    public record Parts(int sequence, int courseCode, int subjectCode) { }

    private ExamDisplayId() { }

    public static String format(int sequence, int courseCode, int subjectCode) {
        if (sequence < 1 || sequence > 9999) {
            throw new IllegalArgumentException("sequence must be 1..9999, got: " + sequence);
        }
        if (courseCode < 0 || courseCode > 9) {
            throw new IllegalArgumentException("course code must be 0..9, got: " + courseCode);
        }
        if (subjectCode < 0 || subjectCode > 9) {
            throw new IllegalArgumentException("subject code must be 0..9, got: " + subjectCode);
        }
        return String.format("%04d%d%d", sequence, courseCode, subjectCode);
    }

    public static Parts parse(String displayId) {
        if (displayId == null || !displayId.matches("\\d{6}")) {
            throw new IllegalArgumentException("display id must be exactly 6 digits, got: " + displayId);
        }
        return new Parts(
                Integer.parseInt(displayId.substring(0, 4)),
                Integer.parseInt(displayId.substring(4, 5)),
                Integer.parseInt(displayId.substring(5, 6)));
    }
}
