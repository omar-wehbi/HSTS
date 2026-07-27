package server.bot;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.io.ByteArrayInputStream;
import java.util.Locale;

public final class StudyBotDocumentExtractor {
    public static final int MAX_FILE_BYTES = 5 * 1024 * 1024;
    public static final int MAX_EXTRACTED_CHARS = 200_000;
    private StudyBotDocumentExtractor() { }

    public static String extract(String fileName, String mimeType, byte[] data) throws Exception {
        if (data == null || data.length == 0) throw new IllegalArgumentException("Document file is required.");
        if (data.length > MAX_FILE_BYTES) throw new IllegalArgumentException("Document is larger than 5 MB.");
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        String type = mimeType == null ? "" : mimeType.toLowerCase(Locale.ROOT);
        String text;
        if (name.endsWith(".pdf") || type.equals("application/pdf")) {
            try (var document = Loader.loadPDF(data)) { text = new PDFTextStripper().getText(document); }
        } else if (name.endsWith(".docx") || type.contains("officedocument.wordprocessingml")) {
            try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(data)); XWPFWordExtractor extractor = new XWPFWordExtractor(document)) { text = extractor.getText(); }
        } else if (name.endsWith(".doc") || type.equals("application/msword")) {
            try (HWPFDocument document = new HWPFDocument(new ByteArrayInputStream(data)); WordExtractor extractor = new WordExtractor(document)) { text = extractor.getText(); }
        } else {
            throw new IllegalArgumentException("Only PDF, DOC and DOCX files are supported.");
        }
        text = text == null ? "" : text.trim();
        if (text.isEmpty()) throw new IllegalArgumentException("No readable text was found in the document.");
        if (text.length() > MAX_EXTRACTED_CHARS) text = text.substring(0, MAX_EXTRACTED_CHARS);
        return text;
    }
}
