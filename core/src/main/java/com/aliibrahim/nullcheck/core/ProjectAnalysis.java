package com.aliibrahim.nullcheck.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Everything {@link ProjectAnalyzer} found in a folder. Instances are immutable. */
public final class ProjectAnalysis {

    /**
     * A file that could not be analysed.
     *
     * @param file    the file, relative to the analysed folder
     * @param message why it failed
     */
    public record FileError(Path file, String message) {
    }

    private final Path root;
    private final List<FileAnalysis> files;
    private final List<FileError> errors;
    private final AnalysisResult total;

    /**
     * Creates a result with unmodifiable copies of the lists.
     *
     * @param root   the analysed folder
     * @param files  the analysed files, sorted by path
     * @param errors the files that could not be analysed, sorted by path
     */
    public ProjectAnalysis(Path root, List<FileAnalysis> files, List<FileError> errors) {
        this.root = root;
        this.files = List.copyOf(files);
        this.errors = List.copyOf(errors);
        List<IfStatement> ifs = new ArrayList<>();
        List<NullComparand> comparands = new ArrayList<>();
        List<Definition> definitions = new ArrayList<>();
        for (FileAnalysis f : this.files) {
            ifs.addAll(f.result().ifStatements());
            comparands.addAll(f.result().comparands());
            definitions.addAll(f.result().definitions());
        }
        this.total = new AnalysisResult("", ifs, comparands, definitions);
    }

    /**
     * Returns the analysed folder.
     *
     * @return the folder as it was given to the analyzer
     */
    public Path root() {
        return root;
    }

    /**
     * Returns the analysed files.
     *
     * @return the files, sorted by path
     */
    public List<FileAnalysis> files() {
        return files;
    }

    /**
     * Returns the files that could not be analysed.
     *
     * @return the failed files, sorted by path
     */
    public List<FileError> errors() {
        return errors;
    }

    /**
     * Returns all files' findings combined, for project-wide counts and statistics. Its source is empty, and its
     * line numbers refer to the individual files. It is computed once, when the result is created.
     *
     * @return the combined result
     */
    public AnalysisResult total() {
        return total;
    }

    /**
     * Returns a name for the folder, for titles.
     *
     * @return the folder's name, or its full path for a root directory
     */
    public String name() {
        Path name = root.toAbsolutePath().normalize().getFileName();
        return name == null ? root.toAbsolutePath().toString() : name.toString();
    }
}
