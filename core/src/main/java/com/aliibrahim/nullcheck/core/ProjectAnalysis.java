package com.aliibrahim.nullcheck.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Everything {@link ProjectAnalyzer} found in a folder.
 *
 * @param root   the analysed folder
 * @param files  the analysed files, sorted by path
 * @param errors the files that could not be read, sorted by path
 */
public record ProjectAnalysis(Path root, List<FileAnalysis> files, List<FileError> errors) {

    /**
     * A file that could not be analysed.
     *
     * @param file    the file, relative to the analysed folder
     * @param message why it failed
     */
    public record FileError(Path file, String message) {
    }

    /**
     * Creates a result with unmodifiable copies of the lists.
     *
     * @param root   the analysed folder
     * @param files  the analysed files
     * @param errors the files that could not be read
     */
    public ProjectAnalysis {
        files = List.copyOf(files);
        errors = List.copyOf(errors);
    }

    /**
     * Returns all files' findings combined, for project-wide counts and statistics. Its source is empty, and its
     * line numbers refer to the individual files.
     *
     * @return the combined result
     */
    public AnalysisResult total() {
        List<IfStatement> ifs = new ArrayList<>();
        List<NullComparand> comparands = new ArrayList<>();
        List<Definition> definitions = new ArrayList<>();
        for (FileAnalysis f : files) {
            ifs.addAll(f.result().ifStatements());
            comparands.addAll(f.result().comparands());
            definitions.addAll(f.result().definitions());
        }
        return new AnalysisResult("", ifs, comparands, definitions);
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
