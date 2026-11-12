package com.aliibrahim.nullcheck.core;

/**
 * A line that declares or assigns a null comparand before it is first checked.
 *
 * @param comparand the comparand expression, for example {@code name}
 * @param code      the source line without comments, trimmed
 * @param line      the 1-based line number
 */
public record Definition(String comparand, String code, int line) {

    /**
     * Returns advice about the value assigned on this line.
     *
     * @return a one-line message for the report
     */
    public String advice() {
        int eq = assignmentIndex(code);
        if (eq < 0) {
            return "No value assigned to " + comparand + " in line " + line + "; assign a value.";
        }
        String target = code.substring(0, eq).trim();
        int semicolon = indexOutsideLiterals(code, ';', eq + 1);
        String value = (semicolon < 0 ? code.substring(eq + 1) : code.substring(eq + 1, semicolon)).trim();
        String where = " assigned to " + target + " in line " + line;
        if (value.equalsIgnoreCase("null") || value.equals("nullptr")) { // PHP's null is case-insensitive
            return value + where + "; assign another value instead of " + value + ".";
        }
        String kind;
        if (value.startsWith("(")) {
            kind = "a cast";
        } else if (value.startsWith("new ") || value.contains(" new ")) {
            kind = "an object creation";
        } else if (value.contains(")")) {
            kind = "a method call";
        } else {
            kind = "a reference value";
        }
        return value + " is " + kind + where + "; check the assigned value.";
    }

    /** Returns the index of the first {@code =} outside literals that is an assignment, or -1. */
    private static int assignmentIndex(String s) {
        for (int i = indexOutsideLiterals(s, '=', 0); i >= 0; i = indexOutsideLiterals(s, '=', i + 1)) {
            char before = i > 0 ? s.charAt(i - 1) : ' ';
            char after = i + 1 < s.length() ? s.charAt(i + 1) : ' ';
            if (after == '=') {
                i++; // skip "=="
            } else if ("!<>".indexOf(before) < 0) {
                return i;
            }
        }
        return -1;
    }

    /** Returns the index of {@code wanted} at or after {@code from} that is not inside a string or char literal. */
    private static int indexOutsideLiterals(String s, char wanted, int from) {
        char quote = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quote != 0) {
                if (c == '\\') {
                    i++;
                } else if (c == quote) {
                    quote = 0;
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == wanted && i >= from) {
                return i;
            }
        }
        return -1;
    }
}
