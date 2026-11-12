package com.aliibrahim.nullcheck.core;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Analyses every supported source file in a folder and its subfolders.
 *
 * <p>Files are picked by extension ({@link Language#forFileName}). Hidden folders (starting with a dot) and
 * the usual build, dependency and output folders ({@code build}, {@code target}, {@code out}, {@code dist},
 * {@code node_modules}, {@code vendor}) are skipped. Files are analysed in parallel; a file that cannot be read
 * is reported in {@link ProjectAnalysis#errors()} instead of stopping the analysis.
 */
public final class ProjectAnalyzer {

    /** Folder names that are never searched. */
    public static final Set<String> SKIPPED_FOLDERS = Set.of("build", "target", "out", "dist", "node_modules", "vendor");

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
                if (dir.equals(root)) {
                    return FileVisitResult.CONTINUE;
                }
                String name = dir.getFileName().toString();
                return name.startsWith(".") || SKIPPED_FOLDERS.contains(name)
                        ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
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
        files.sort(Comparator.comparing(p -> p.toString().replace('\\', '/')));
        return files;
    }

    /**
     * Analyses every supported source file under a folder.
     *
     * @param root the folder to analyse
     * @return what was found
     * @throws IOException if the folder cannot be listed
     */
    public ProjectAnalysis analyze(Path root) throws IOException {
        List<Path> files = sourceFiles(root);
        ConcurrentLinkedQueue<FileAnalysis> results = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<ProjectAnalysis.FileError> errors = new ConcurrentLinkedQueue<>();
        files.parallelStream().forEach(relative -> {
            Path file = root.resolve(relative);
            try {
                Language language = Language.forFileName(file.getFileName().toString()).orElseThrow();
                results.add(new FileAnalysis(relative, language, analyzer.analyze(file)));
            } catch (IOException | UncheckedIOException e) {
                errors.add(new ProjectAnalysis.FileError(relative, String.valueOf(e.getMessage())));
            }
        });
        Comparator<Path> byPath = Comparator.comparing(p -> p.toString().replace('\\', '/'));
        List<FileAnalysis> sortedResults = new ArrayList<>(results);
        sortedResults.sort(Comparator.comparing(FileAnalysis::file, byPath));
        List<ProjectAnalysis.FileError> sortedErrors = new ArrayList<>(errors);
        sortedErrors.sort(Comparator.comparing(ProjectAnalysis.FileError::file, byPath));
        return new ProjectAnalysis(root, sortedResults, sortedErrors);
    }
}
