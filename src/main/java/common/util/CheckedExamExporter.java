package common.util;

import common.network.CheckedAnswer;
import common.network.CheckedExamResult;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Formats a student-visible checked exam as plain text or a simple PDF copy. */
public final class CheckedExamExporter {

    private CheckedExamExporter() { }

    public static String toText(CheckedExamResult result) {
        if (result == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("Checked exam copy\n");
        sb.append("=================\n");
        sb.append("Exam: ").append(nullToEmpty(result.getExamTitle())).append("\n");
        sb.append("Score: ").append(result.getScore()).append("\n");
        sb.append("Status: ").append(result.getGradeStatus()).append("\n");
        if (result.getTeacherComment() != null && !result.getTeacherComment().isBlank()) {
            sb.append("Teacher comment: ").append(result.getTeacherComment().trim()).append("\n");
        }
        if (result.getOverrideJustification() != null && !result.getOverrideJustification().isBlank()) {
            sb.append("Override justification: ").append(result.getOverrideJustification().trim()).append("\n");
        }
        sb.append("\n");
        int n = 1;
        for (CheckedAnswer a : result.getAnswers()) {
            sb.append(n++).append(". ").append(nullToEmpty(a.getQuestionText()))
                    .append(" (").append(a.getPoints()).append(" pts)\n");
            sb.append("   Your answer: ").append(a.getSelectedAnswer() == null ? "—" : a.getSelectedAnswer());
            sb.append(a.isCorrect() ? " (correct)" : " (incorrect)").append("\n");
            if (!a.isCorrect()) {
                sb.append("   Correct answer: ").append(a.getCorrectAnswer()).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString().trim() + "\n";
    }

    public static byte[] toUtf8Bytes(CheckedExamResult result) {
        return toText(result).getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] toPdf(CheckedExamResult result) throws IOException {
        List<String> lines = wrap(toText(result), 95);
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            int i = 0;
            while (i < lines.size()) {
                PDPage page = new PDPage(PDRectangle.LETTER);
                doc.addPage(page);
                float y = page.getMediaBox().getHeight() - 50;
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(font, 10);
                    cs.newLineAtOffset(50, y);
                    while (i < lines.size() && y >= 50) {
                        cs.showText(sanitize(lines.get(i)));
                        cs.newLineAtOffset(0, -14);
                        y -= 14;
                        i++;
                    }
                    cs.endText();
                }
            }
            if (doc.getNumberOfPages() == 0) {
                doc.addPage(new PDPage(PDRectangle.LETTER));
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String sanitize(String line) {
        StringBuilder sb = new StringBuilder();
        for (char c : line.toCharArray()) {
            sb.append(c < 32 || c > 255 ? '?' : c);
        }
        return sb.toString();
    }

    private static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        for (String raw : text.replace("\r", "").split("\n", -1)) {
            String line = raw;
            while (line.length() > width) {
                lines.add(line.substring(0, width));
                line = line.substring(width);
            }
            lines.add(line);
        }
        return lines;
    }
}
