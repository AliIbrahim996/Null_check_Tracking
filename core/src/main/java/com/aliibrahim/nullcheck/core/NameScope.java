package com.aliibrahim.nullcheck.core;

/** Where a name that is compared against {@code null} is declared. */
public enum NameScope {
    /** A field of the enclosing class. */
    MEMBER("member"),
    /** A parameter of the enclosing method or constructor. */
    PARAMETER("parameter"),
    /** A local variable, or a name whose declaration was not found. */
    LOCAL("local");

    private final String label;

    NameScope(String label) {
        this.label = label;
    }

    /**
     * Returns a lower-case label for reports.
     *
     * @return the label, for example {@code "member"}
     */
    public String label() {
        return label;
    }
}
