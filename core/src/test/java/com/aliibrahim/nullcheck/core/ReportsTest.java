package com.aliibrahim.nullcheck.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.PdfTextExtractor;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReportsTest {

    private final AnalysisResult result = new NullCheckAnalyzer().analyze("""
            class A {
              String name;
              A() { name = null; }
              void f(String s) { if (name == null || s != null) { } }
            }
            """);

    @Test
    void textReportListsEverySection() {
        String text = Reports.toText(result, "A.java");
        assertTrue(text.startsWith("Tracking null checks: A.java\n"), text);
        assertTrue(text.contains("line 4: if (name == null || s != null)"), text);
        assertTrue(text.contains("[1] name  (member name, line 4)"), text);
        assertTrue(text.contains("[2] s  (parameter name, line 4)"), text);
        assertTrue(text.contains("null assigned to name in line 3"), text);
        assertTrue(text.contains("If statements that check for null: 100%"), text);
    }

    @Test
    void emptyResultSaysNone() {
        String text = Reports.toText(new NullCheckAnalyzer().analyze(""), "Empty.java");
        assertTrue(text.contains("(none)"), text);
        assertTrue(text.contains("Comparands that are members: 0%"), text);
    }

    @Test
    void writesAPdf(@TempDir Path dir) throws IOException {
        Path pdf = dir.resolve("report.pdf");
        Reports.writePdf(result, "A.java", pdf);
        byte[] bytes = Files.readAllBytes(pdf);
        assertTrue(bytes.length > 500);
        assertEquals("%PDF-", new String(bytes, 0, 5, StandardCharsets.US_ASCII));
        try (var files = Files.list(dir)) {
            assertEquals(List.of(pdf), files.toList(), "no temporary file is left behind");
        }
    }

    @Test
    void nonLatinTextIsKeptOrVisiblyReplaced() throws IOException {
        AnalysisResult unicode = new NullCheckAnalyzer().analyze("""
                class A {
                  void f(String s) {
                    s = "café имя";
                    if (s == null) { }
                  }
                }
                """);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Reports.writePdf(unicode, "A.java", out);
        PdfReader reader = new PdfReader(out.toByteArray());
        try {
            String text = PdfTextExtractor.getTextFromPage(reader, 1);
            assertTrue(text.contains("café"), text);
            assertTrue(text.contains("имя") || text.contains("???"), text);
        } finally {
            reader.close();
        }
    }

    @Test
    void writeFailureReportsTheOriginalError() {
        OutputStream broken = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("disk full");
            }
        };
        IOException e = assertThrows(IOException.class, () -> Reports.writePdf(result, "A.java", broken));
        assertEquals("disk full", e.getMessage());
    }

    @Test
    void failedFileWriteKeepsTheOldReport(@TempDir Path dir) throws IOException {
        Path missingFolder = dir.resolve("missing/report.pdf");
        assertThrows(IOException.class, () -> Reports.writePdf(result, "A.java", missingFolder));
        Path existing = dir.resolve("report.pdf");
        Files.writeString(existing, "old");
        Reports.writePdf(result, "A.java", existing);
        assertTrue(Files.size(existing) > 500);
    }
}
