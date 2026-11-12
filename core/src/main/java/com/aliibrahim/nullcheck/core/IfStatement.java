package com.aliibrahim.nullcheck.core;

/**
 * An {@code if} statement found in the analysed source.
 *
 * @param condition  the condition between the parentheses, with whitespace collapsed
 * @param line       the 1-based line on which the statement starts
 * @param checksNull whether at least one part of the condition compares something with {@code null}
 */
public record IfStatement(String condition, int line, boolean checksNull) {

    /**
     * Returns the statement head as it would appear in code.
     *
     * @return {@code "if (" + condition + ")"}
     */
    public String text() {
        return "if (" + condition + ")";
    }
}
