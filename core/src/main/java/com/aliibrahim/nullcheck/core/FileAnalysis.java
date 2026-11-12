package com.aliibrahim.nullcheck.core;

import java.nio.file.Path;

/**
 * The analysis of one file in a folder.
 *
 * @param file     the file, relative to the analysed folder
 * @param language the language the file was analysed as
 * @param result   what was found
 */
public record FileAnalysis(Path file, Language language, AnalysisResult result) {

    /**
     * Returns the file path with {@code /} separators, for display.
     *
     * @return for example {@code src/main/Customer.java}
     */
    public String displayPath() {
        return file.toString().replace('\\', '/');
    }
}
