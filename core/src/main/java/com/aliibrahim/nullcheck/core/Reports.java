package com.aliibrahim.nullcheck.core;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Chunk;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.ExceptionConverter;
import com.itextpdf.text.Font;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** Turns an {@link AnalysisResult} into a plain-text or PDF report. */
public final class Reports {

    /** The kinds of report section, which the PDF writer styles differently. */
    private enum Style { NORMAL, CODE, WARNING, SUMMARY }

    private record Section(String title, List<String> lines, Style style) {
    }

    /** A group of sections, optionally under a heading such as a file name. */
    private record Part(String heading, List<Section> sections) {
    }

    /** Writes a report to a stream. */
    private interface PdfContent {
        void writeTo(OutputStream out) throws IOException;
    }

    private Reports() {
    }

    /**
     * Renders a plain-text report.
     *
     * @param result     the analysis result
     * @param sourceName a name for the analysed source, shown in the title, for example the file name
     * @return the report, with {@code \n} line endings
     */
    public static String toText(AnalysisResult result, String sourceName) {
        return toText(title(sourceName), List.of(new Part(null, sections(result))));
    }

    /**
     * Renders a plain-text report for a folder: a summary of the files, project-wide statistics, and then each
     * file's report.
     *
     * @param project the analysed folder
     * @return the report, with {@code \n} line endings
     */
    public static String toText(ProjectAnalysis project) {
        return toText(projectTitle(project), projectParts(project));
    }

    private static String toText(String title, List<Part> parts) {
        StringBuilder out = new StringBuilder(title).append('\n');
        for (Part part : parts) {
            if (part.heading() != null) {
                out.append('\n').append("=".repeat(part.heading().length() + 8)).append('\n');
                out.append("==  ").append(part.heading()).append("  ==\n");
                out.append("=".repeat(part.heading().length() + 8)).append('\n');
            }
            for (Section section : part.sections()) {
                out.append('\n').append(section.title()).append('\n');
                out.append("-".repeat(section.title().length())).append('\n');
                for (String line : section.lines()) {
                    out.append("  ").append(line).append('\n');
                }
            }
        }
        return out.toString();
    }

    /**
     * Writes a PDF report to a file, replacing it if it exists. The report is first written to a temporary file
     * in the same directory, so a failed write never leaves a partial report behind or damages an existing one.
     *
     * @param result     the analysis result
     * @param sourceName a name for the analysed source, shown in the title, for example the file name
     * @param target     the PDF file to write; its directory must exist
     * @throws IOException if the file cannot be written
     */
    public static void writePdf(AnalysisResult result, String sourceName, Path target) throws IOException {
        writeFile(target, out -> writePdf(result, sourceName, out));
    }

    /**
     * Writes a PDF report for a folder to a file, replacing it if it exists, in the same safe way as
     * {@link #writePdf(AnalysisResult, String, Path)}.
     *
     * @param project the analysed folder
     * @param target  the PDF file to write; its directory must exist
     * @throws IOException if the file cannot be written
     */
    public static void writePdf(ProjectAnalysis project, Path target) throws IOException {
        writeFile(target, out -> writePdf(project, out));
    }

    /**
     * Writes a PDF report for a folder to a stream. The stream is not closed.
     *
     * @param project the analysed folder
     * @param out     where to write the PDF
     * @throws IOException if writing fails
     */
    public static void writePdf(ProjectAnalysis project, OutputStream out) throws IOException {
        writePdf(projectTitle(project), projectParts(project), out);
    }

    private static void writeFile(Path target, PdfContent content) throws IOException {
        Path absolute = target.toAbsolutePath();
        Path temp = Files.createTempFile(absolute.getParent(), ".null-check-", ".pdf.tmp");
        try {
            try (OutputStream out = Files.newOutputStream(temp)) {
                content.writeTo(out);
            }
            try {
                Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /**
     * Writes a PDF report to a stream. The stream is not closed.
     *
     * <p>Text is set in a Unicode TrueType font of the operating system (Arial and Consolas on Windows, DejaVu on
     * Linux, Arial and Courier New on macOS) when one is found, so that non-Latin characters in the source are
     * kept. Otherwise the standard PDF fonts are used, which only cover Western European characters, and other
     * characters are shown as {@code ?}. Right-to-left text is not reordered.
     *
     * @param result     the analysis result
     * @param sourceName a name for the analysed source, shown in the title, for example the file name
     * @param out        where to write the PDF
     * @throws IOException if writing fails
     */
    public static void writePdf(AnalysisResult result, String sourceName, OutputStream out) throws IOException {
        writePdf(title(sourceName), List.of(new Part(null, sections(result))), out);
    }

    private static void writePdf(String title, List<Part> parts, OutputStream out) throws IOException {
        BaseFont sans = PdfFonts.SANS;
        BaseFont mono = PdfFonts.MONO;
        Font titleFont = new Font(sans, 16, Font.BOLD, BaseColor.RED);
        Font fileFont = new Font(sans, 14, Font.BOLD, BaseColor.BLUE);
        Font headingFont = new Font(sans, 13, Font.BOLD, BaseColor.DARK_GRAY);
        Font normalFont = new Font(sans, 10);
        Font codeFont = new Font(mono, 9);
        Font warningFont = new Font(sans, 10, Font.NORMAL, BaseColor.RED);
        Font summaryFont = new Font(sans, 11, Font.ITALIC, BaseColor.DARK_GRAY);

        Document doc = new Document(PageSize.A4, 50, 50, 50, 50);
        IOException failure = null;
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setCloseStream(false);
            doc.addTitle(title);
            doc.addCreator("Null Check Tracking");
            doc.open();
            doc.add(new Paragraph(new Chunk(printable(title, sans), titleFont)));
            for (Part part : parts) {
                if (part.heading() != null) {
                    Paragraph file = new Paragraph(printable(part.heading(), sans), fileFont);
                    file.setSpacingBefore(24);
                    doc.add(file);
                }
                for (Section section : part.sections()) {
                    Paragraph heading = new Paragraph(printable(section.title(), sans), headingFont);
                    heading.setSpacingBefore(14);
                    heading.setSpacingAfter(4);
                    doc.add(heading);
                    Font font = switch (section.style()) {
                        case CODE -> codeFont;
                        case WARNING -> warningFont;
                        case SUMMARY -> summaryFont;
                        case NORMAL -> normalFont;
                    };
                    for (String line : section.lines()) {
                        Paragraph p = new Paragraph(printable(line, font.getBaseFont()), font);
                        p.setIndentationLeft(12);
                        doc.add(p);
                    }
                }
            }
        } catch (DocumentException e) {
            failure = new IOException("Could not write the PDF report: " + e.getMessage(), e);
        } catch (ExceptionConverter e) {
            failure = unwrap(e);
        }
        if (doc.isOpen()) {
            // Closing writes the rest of the file and can fail as well; it must not hide the first failure.
            try {
                doc.close();
            } catch (ExceptionConverter e) {
                if (failure == null) {
                    failure = unwrap(e);
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    /** iText wraps checked exceptions, such as I/O errors, in an unchecked {@link ExceptionConverter}. */
    private static IOException unwrap(ExceptionConverter e) {
        return e.getException() instanceof IOException io
                ? io : new IOException("Could not write the PDF report: " + e.getMessage(), e);
    }

    /** Replaces the characters a font has no glyph for with {@code ?}, so that none silently disappear. */
    private static String printable(String text, BaseFont font) {
        StringBuilder out = new StringBuilder(text.length());
        text.codePoints().forEach(cp -> {
            if (Character.isISOControl(cp) || font.charExists(cp)) {
                out.appendCodePoint(cp);
            } else {
                out.append('?');
            }
        });
        return out.toString();
    }

    private static String title(String sourceName) {
        return "Tracking null checks: " + sourceName;
    }

    private static String projectTitle(ProjectAnalysis project) {
        int count = project.files().size();
        return title(project.name() + " (" + count + (count == 1 ? " file)" : " files)"));
    }

    private static List<Part> projectParts(ProjectAnalysis project) {
        List<String> files = new ArrayList<>();
        for (FileAnalysis f : project.files()) {
            AnalysisResult r = f.result();
            files.add(f.displayPath() + "  (" + f.language().label() + "): " + r.ifStatements().size()
                    + " if statements, " + r.comparands().size() + " null checks, " + r.definitions().size()
                    + " definitions");
        }
        List<Section> summary = new ArrayList<>();
        summary.add(new Section("Files (" + files.size() + ")", orNone(files), Style.NORMAL));
        if (!project.errors().isEmpty()) {
            List<String> errors = project.errors().stream()
                    .map(e -> e.file().toString().replace('\\', '/') + ": " + e.message()).toList();
            summary.add(new Section("Could not read (" + errors.size() + ")", errors, Style.WARNING));
        }
        AnalysisResult total = project.total();
        summary.add(new Section("Totals", List.of(
                "If statements: " + total.ifStatements().size(),
                "Null comparands: " + total.comparands().size(),
                "Definitions before a check: " + total.definitions().size()), Style.NORMAL));
        summary.add(statistics(total, "Statistics (all files)"));
        List<Part> parts = new ArrayList<>();
        parts.add(new Part(null, summary));
        for (FileAnalysis f : project.files()) {
            parts.add(new Part(f.displayPath(), sections(f.result())));
        }
        return parts;
    }

    private static List<Section> sections(AnalysisResult r) {
        List<Section> sections = new ArrayList<>();

        List<String> ifs = new ArrayList<>();
        for (IfStatement s : r.ifStatements()) {
            ifs.add("line " + s.line() + ": " + s.text());
        }
        sections.add(new Section("If statements (" + ifs.size() + ")", orNone(ifs), Style.CODE));

        List<String> comparands = new ArrayList<>();
        int i = 1;
        for (NullComparand c : r.comparands()) {
            String kind = c.scope() == null ? c.kind().label() : c.scope().label() + " " + c.kind().label();
            comparands.add("[" + i++ + "] " + c.expression() + "  (" + kind + ", line " + c.line() + ")");
        }
        sections.add(new Section("Null comparands (" + comparands.size() + ")", orNone(comparands), Style.NORMAL));

        List<String> definitions = new ArrayList<>();
        List<String> advice = new ArrayList<>();
        for (Definition d : r.definitions()) {
            definitions.add("line " + d.line() + ": " + d.code());
            advice.add("- " + d.advice());
        }
        sections.add(new Section("Comparand definitions (" + definitions.size() + ")", orNone(definitions),
                Style.CODE));
        sections.add(new Section("Advice", orNone(advice), Style.WARNING));

        sections.add(statistics(r, "Statistics"));
        return sections;
    }

    private static Section statistics(AnalysisResult r, String title) {
        return new Section(title, List.of(
                "If statements that check for null: " + r.percentOfIfsCheckingNull() + "%",
                "Comparands that are members: " + r.percentOfComparands(NameScope.MEMBER) + "%",
                "Comparands that are parameters: " + r.percentOfComparands(NameScope.PARAMETER) + "%",
                "Comparands that are locals: " + r.percentOfComparands(NameScope.LOCAL) + "%",
                "Comparands that are field accesses: " + r.percentOfComparands(ComparandKind.FIELD_ACCESS) + "%",
                "Comparands that are method calls: " + r.percentOfComparands(ComparandKind.METHOD_CALL) + "%"),
                Style.SUMMARY);
    }

    private static List<String> orNone(List<String> lines) {
        return lines.isEmpty() ? List.of("(none)") : lines;
    }

    /** The fonts of PDF reports, looked up once on first use. */
    private static final class PdfFonts {
        static final BaseFont SANS = load(BaseFont.HELVETICA,
                "%WINDIR%/Fonts/arial.ttf",
                "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
                "/usr/share/fonts/dejavu/DejaVuSans.ttf",
                "/usr/share/fonts/TTF/DejaVuSans.ttf",
                "/System/Library/Fonts/Supplemental/Arial.ttf",
                "/Library/Fonts/Arial.ttf");
        static final BaseFont MONO = load(BaseFont.COURIER,
                "%WINDIR%/Fonts/consola.ttf",
                "%WINDIR%/Fonts/cour.ttf",
                "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf",
                "/usr/share/fonts/dejavu/DejaVuSansMono.ttf",
                "/usr/share/fonts/TTF/DejaVuSansMono.ttf",
                "/System/Library/Fonts/Supplemental/Courier New.ttf",
                "/Library/Fonts/Courier New.ttf");

        private PdfFonts() {
        }

        private static BaseFont load(String standardFont, String... candidates) {
            String windows = System.getenv("WINDIR");
            for (String candidate : candidates) {
                if (candidate.startsWith("%WINDIR%")) {
                    if (windows == null) {
                        continue;
                    }
                    candidate = windows + candidate.substring("%WINDIR%".length());
                }
                if (Files.isRegularFile(Path.of(candidate))) {
                    try {
                        return BaseFont.createFont(candidate, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                    } catch (DocumentException | IOException | ExceptionConverter e) {
                        // An unreadable or unsupported font file: try the next one.
                    }
                }
            }
            try {
                return BaseFont.createFont(standardFont, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            } catch (DocumentException | IOException e) {
                throw new IllegalStateException("The standard PDF font " + standardFont + " is missing", e);
            }
        }
    }
}
