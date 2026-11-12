package com.aliibrahim.nullcheck.cli;

import com.aliibrahim.nullcheck.core.AnalysisResult;
import com.aliibrahim.nullcheck.core.NullCheckAnalyzer;
import com.aliibrahim.nullcheck.core.ProjectAnalysis;
import com.aliibrahim.nullcheck.core.ProjectAnalyzer;
import com.aliibrahim.nullcheck.core.Reports;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;

/**
 * Command-line entry point.
 *
 * <pre>
 * null-check SOURCE              print a text report
 * null-check SOURCE REPORT.pdf   also write a PDF report
 * </pre>
 *
 * <p>SOURCE is a Java, PHP or C/C++ file, whose language is chosen from the extension (unknown extensions are
 * analysed as Java), or a folder, in which case every supported file in it and its subfolders is analysed (see
 * {@link ProjectAnalyzer}).
 */
public final class Main {

    /** Exit code for success. */
    public static final int OK = 0;
    /** Exit code when a file cannot be read or written. */
    public static final int IO_ERROR = 1;
    /** Exit code for wrong arguments. */
    public static final int USAGE_ERROR = 2;

    private static final String USAGE = """
            Usage: null-check SOURCE [REPORT.pdf]

            Finds the null checks in SOURCE and prints a report. SOURCE is a Java (.java),
            PHP (.php, .phtml) or C/C++ (.cpp, .cc, .cxx, .h, .hpp, .c ...) file, or a folder:
            then every such file in it is analysed, skipping hidden, build-output and dependency
            folders. With REPORT.pdf, also writes the report as a PDF file.""";

    private Main() {
    }

    /**
     * Runs the tool and exits with its exit code.
     *
     * @param args the command-line arguments
     */
    public static void main(String[] args) {
        System.exit(run(List.of(args), System.out, System.err));
    }

    /**
     * Runs the tool without exiting the JVM.
     *
     * @param args the command-line arguments
     * @param out  where the report goes
     * @param err  where errors and usage help go
     * @return {@link #OK}, {@link #IO_ERROR} or {@link #USAGE_ERROR}
     */
    public static int run(List<String> args, PrintStream out, PrintStream err) {
        if (args.size() == 1 && (args.get(0).equals("-h") || args.get(0).equals("--help"))) {
            out.println(USAGE);
            return OK;
        }
        if (args.isEmpty() || args.size() > 2) {
            err.println(USAGE);
            return USAGE_ERROR;
        }
        Path source;
        Path pdf;
        try {
            source = Path.of(args.get(0));
            pdf = args.size() == 2 ? Path.of(args.get(1)) : null;
        } catch (InvalidPathException e) {
            err.println("Invalid path: " + e.getMessage());
            return USAGE_ERROR;
        }
        boolean folder = Files.isDirectory(source);
        if (!folder && !Files.isRegularFile(source)) {
            err.println("No such file or folder: " + source);
            return IO_ERROR;
        }
        try {
            int status = OK;
            if (folder) {
                ProjectAnalysis project = new ProjectAnalyzer().analyze(source);
                out.print(Reports.toText(project));
                for (ProjectAnalysis.FileError e : project.errors()) {
                    err.println("Could not read " + e.file() + ": " + e.message());
                    status = IO_ERROR;
                }
                if (pdf != null) {
                    createParent(pdf);
                    Reports.writePdf(project, pdf);
                }
            } else {
                AnalysisResult result = new NullCheckAnalyzer().analyze(source);
                String name = source.getFileName().toString();
                out.print(Reports.toText(result, name));
                if (pdf != null) {
                    createParent(pdf);
                    Reports.writePdf(result, name, pdf);
                }
            }
            if (pdf != null) {
                out.println();
                out.println("PDF report written to " + pdf);
            }
            return status;
        } catch (IOException e) {
            err.println("Error: " + e.getMessage());
            return IO_ERROR;
        }
    }

    private static void createParent(Path file) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }
}
