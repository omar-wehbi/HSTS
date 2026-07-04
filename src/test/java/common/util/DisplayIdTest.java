package common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Tests for the 5-digit question display id (Person 2, Phase 3).
 *
 * <p>Semester-doc scheme, matching the Assignment 1 acceptance fixtures:
 * {@code 10017} = sequence {@code 1001} + course code {@code 7} — four digits
 * of question sequence followed by one course-code digit.
 */
class DisplayIdTest {

    // ===== formatting =====================================================

    @Test
    void matchesTheAssignmentFixtureScheme() {
        // From the Assignment 1 acceptance-test data: question 10017 is
        // sequence 1001 in the course with code 7 (mathematics).
        assertThat(DisplayId.format(1001, 7)).isEqualTo("10017");
    }

    @Test
    void smallSequencesArePaddedToFourDigits() {
        assertThat(DisplayId.format(1, 3)).isEqualTo("00013");
        assertThat(DisplayId.format(42, 1)).isEqualTo("00421");
    }

    @Test
    void resultIsAlwaysExactlyFiveCharacters() {
        assertThat(DisplayId.format(1, 0)).hasSize(5);
        assertThat(DisplayId.format(9999, 9)).hasSize(5);
    }

    // ===== parsing ========================================================

    @Test
    void parseIsTheInverseOfFormat() {
        DisplayId.Parts p = DisplayId.parse("10017");
        assertThat(p.sequence()).isEqualTo(1001);
        assertThat(p.courseCode()).isEqualTo(7);
        assertThat(DisplayId.format(p.sequence(), p.courseCode())).isEqualTo("10017");
    }

    @Test
    void parseKeepsLeadingZeroSequences() {
        assertThat(DisplayId.parse("00013").sequence()).isEqualTo(1);
    }

    @Test
    void parseRejectsWrongLengthOrNonDigits() {
        assertThatIllegalArgumentException().isThrownBy(() -> DisplayId.parse("1234"));
        assertThatIllegalArgumentException().isThrownBy(() -> DisplayId.parse("123456"));
        assertThatIllegalArgumentException().isThrownBy(() -> DisplayId.parse("12a45"));
        assertThatIllegalArgumentException().isThrownBy(() -> DisplayId.parse(null));
    }

    // ===== validation =====================================================

    @Test
    void sequenceMustFitFourDigits() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> DisplayId.format(10000, 1))
                .withMessageContaining("sequence");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> DisplayId.format(0, 1))
                .withMessageContaining("sequence");
    }

    @Test
    void courseCodeMustBeOneDigit() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> DisplayId.format(1, 10))
                .withMessageContaining("course");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> DisplayId.format(1, -1))
                .withMessageContaining("course");
    }
}
