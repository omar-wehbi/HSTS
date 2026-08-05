package common.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExamDisplayIdTest {

    @Test
    void formatAndParseRoundTrip() {
        String id = ExamDisplayId.format(17, 1, 2);
        assertThat(id).isEqualTo("001712");
        ExamDisplayId.Parts parts = ExamDisplayId.parse(id);
        assertThat(parts.sequence()).isEqualTo(17);
        assertThat(parts.courseCode()).isEqualTo(1);
        assertThat(parts.subjectCode()).isEqualTo(2);
    }

    @Test
    void rejectsInvalidInput() {
        assertThatThrownBy(() -> ExamDisplayId.format(0, 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ExamDisplayId.parse("12345"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
