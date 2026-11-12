package com.aliibrahim.nullcheck.core;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Analyses every supported source file in a folder and its subfolders.
 *
 * <p>Files are picked by extension ({@link Language#forFileName}). Hidden folders (starting with a dot) and
 * {@code node_modules} are always skipped. Build output folders ({@code build}, {@code target}, {@code out},
 * {@code dist}) are skipped only when the folder that contains them has a build file such as
 * {@code build.gradle.kts}, {@code pom.xml}, {@code package.json} or {@code CMakeLists.txt}, and {@code vendor}
 * only next to {@code composer.json} or {@code go.mod}, so source packages that happen to have these names are
 * still analysed. Files are analysed in parallel; a file that cannot be read or analysed is reported in
 * {@link ProjectAnalysis#errors()} instead of stopping the analysis.
 */
public final class ProjectAnalyzer {

    /** Folder names that are never searched, in addition to hidden folders. */
    public static final Set<String> ALWAYS_SKIPPED = Set.of("node_modules");
    /** Folder names that are skipped as build output when their parent folder contains a build file. */
    public static final Set<String> BUILD_OUTPUT_FOLDERS = Set.of("build", "target", "out", "dist");
    /** Files that mark a folder as the root of a build, whose output folders are skipped. */
    public static final Set<String> BUILD_FILES = Set.of("build.gradle", "build.gradle.kts", "settings.gradle",
            "settings.gradle.kts", "pom.xml", "build.xml", "package.json", "CMakeLists.txt", "Makefile",
            "meson.build", "composer.json", "Cargo.toml");
    /** Files that mark a {@code vendor} folder next to them as third-party dependencies. */
    public static final Set<String> DEPENDENCY_FILES = Set.of("composer.json", "go.mod");

    private static final Comparator<Path> BY_PATH = Comparator.comparing(p -> p.toString().replace('\\', '/'));

    private final NullCheckAnalyzer analyzer = new NullCheckAnalyzer();

    /** Creates a project analyzer. */
    public ProjectAnalyzer() {
    }

    /**
     * Finds the files that {@link #analyze(Path)} would analyse.
     *
     * @param root the folder to search
     * @return the files, relative to {@code root}, sorted by path
     * @throws IOException if the folder cannot be listed
     */
    public List<Path> sourceFiles(Path root) throws IOException {
        List<Path> files = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                return dir.equals(root) || !skipped(dir) ? FileVisitResult.CONTINUE : FileVisitResult.SKIP_SUBTREE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (attrs.isRegularFile() && Language.forFileName(file.getFileName().toString()).isPresent()) {
                    files.add(root.relativize(file));
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException e) {
                return FileVisitResult.CONTINUE; // unreadable entries are skipped
            }
        });
        files.sort(BY_PATH);
        return files;
    }

    /** Whether a folder is hidden, dependencies or build output rather than sources. */
    private static boolean skipped(Path dir) {
        String name = dir.getFileName().toString();
        if (name.startsWith(".") || ALWAYS_SKIPPED.contains(name)) {
            return true;
        }
        Path parent = dir.getParent();
        if (BUILD_OUTPUT_FOLDERS.contains(name)) {
            return parent != null && BUILD_FILES.stream().anyMatch(f -> Files.isRegularFile(parent.resolve(f)));
        }
        if (name.equals("vendor")) {
            return parent != null && DEPENDENCY_FILES.stream().anyMatch(f -> Files.isRegularFile(parent.resolve(f)));
        }
        return false;
    }

    /** The outcome for one file: a result or an error. */
    private record Outcome(FileAnalysis result, ProjectAnalysis.FileError error) {
    }

    /**
     * Analyses every supported source file under a folder.
     *
     * @param root the folder to analyse
     * @return what was found
     * @throws IOException if the folder cannot be listed
     */
    public ProjectAnalysis analyze(Path root) throws IOException {
        // An ordered parallel stream keeps the sorted order of sourceFiles.
        List<Outcome> outcomes = sourceFiles(root).parallelStream().map(relative -> analyzeOne(root, relative)).toList();
        List<FileAnalysis> results = new ArrayList<>();
        List<ProjectAnalysis.FileError> errors = new ArrayList<>();
        for (Outcome o : outcomes) {
            if (o.result() != null) {
                results.add(o.result());
            } else {
                errors.add(o.error());
            }
        }
        return new ProjectAnalysis(root, results, errors);
    }

    private Outcome analyzeOne(Path root, Path relative) {
        Path file = root.resolve(relative);
        try {
            Language language = Language.forFileName(file.getFileName().toString()).orElseThrow();
            return new Outcome(new FileAnalysis(relative, language, analyzer.analyze(file)), null);
        } catch (IOException | RuntimeException e) {
            // One unreadable or unusual file must not stop the analysis of all the others.
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return new Outcome(null, new ProjectAnalysis.FileError(relative, message));
        }
    }
}
