package common.util;

import common.entities.GradeStatus;
import common.network.CheckedAnswer;
import common.network.CheckedExamResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CheckedExamExporterTest {

    @Test
    void toTextIncludesScoreCommentAndWrongAnswers() throws Exception {
        CheckedExamResult result = new CheckedExamResult(
                1, 2, 3, "Algorithms Quiz", 75, GradeStatus.APPROVED,
                null, "Nice work on Q1",
                List.of(new CheckedAnswer(10, "What is 2+2?", "1", "2", "3", "4",
                        4, 4, 25, true),
                        new CheckedAnswer(11, "Binary search?", "O(n)", "O(log n)", "O(1)", "O(n^2)",
                                1, 2, 25, false)));

        String text = CheckedExamExporter.toText(result);
        assertThat(text).contains("Algorithms Quiz");
        assertThat(text).contains("Score: 75");
        assertThat(text).contains("Nice work on Q1");
        assertThat(text).contains("(incorrect)");
        assertThat(text).contains("Correct answer: 2");

        byte[] pdf = CheckedExamExporter.toPdf(result);
        assertThat(pdf.length).isGreaterThan(100);
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }
}
