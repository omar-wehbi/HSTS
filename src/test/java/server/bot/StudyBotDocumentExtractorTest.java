package server.bot;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StudyBotDocumentExtractorTest {

    @Test
    void rejectsEmptyOrHugeOrUnsupported() {
        assertThatThrownBy(() -> StudyBotDocumentExtractor.extract("a.pdf", "application/pdf", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StudyBotDocumentExtractor.extract("a.pdf", "application/pdf", new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StudyBotDocumentExtractor.extract("a.pdf", "application/pdf",
                new byte[StudyBotDocumentExtractor.MAX_FILE_BYTES + 1]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("5 MB");
        assertThatThrownBy(() -> StudyBotDocumentExtractor.extract("notes.txt", "text/plain", new byte[]{1, 2, 3}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PDF");
    }
}
