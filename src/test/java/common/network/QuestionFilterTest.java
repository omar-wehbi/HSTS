package common.network;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wire-shape test for the {@link QuestionFilter} DTO (Person 2, Phase 3):
 * bean contract (OCSF payloads need the no-arg constructor + setters) and
 * Java-serialization round-trip, which is how it actually travels.
 */
class QuestionFilterTest {

    @Test
    void beanContractWorks() {
        QuestionFilter f = new QuestionFilter();
        f.setCourseId(2);
        f.setTopic("SQL");
        f.setDifficulty("EASY");

        assertThat(f.getCourseId()).isEqualTo(2);
        assertThat(f.getTopic()).isEqualTo("SQL");
        assertThat(f.getDifficulty()).isEqualTo("EASY");
        assertThat(f.toString()).contains("SQL").contains("EASY");
    }

    @Test
    void survivesJavaSerialization() throws Exception {
        QuestionFilter original = new QuestionFilter(3, "Protocols", null);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(original);
        }
        QuestionFilter copy;
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            copy = (QuestionFilter) in.readObject();
        }

        assertThat(copy.getCourseId()).isEqualTo(3);
        assertThat(copy.getTopic()).isEqualTo("Protocols");
        assertThat(copy.getDifficulty()).isNull();
    }
}
