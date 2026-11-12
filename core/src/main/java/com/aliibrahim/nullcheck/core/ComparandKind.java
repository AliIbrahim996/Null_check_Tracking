package com.aliibrahim.nullcheck.core;

/** The syntactic form of an expression that is compared against {@code null}. */
public enum ComparandKind {
    /** A method call, for example {@code map.get(key) == null}. */
    METHOD_CALL("method call"),
    /** A field access, for example {@code customer.address != null}. */
    FIELD_ACCESS("field access"),
    /** A plain name, for example {@code name == null}. */
    NAME("name");

    private final String label;

    ComparandKind(String label) {
        this.label = label;
    }

    /**
     * Returns a lower-case label for reports.
     *
     * @return the label, for example {@code "method call"}
     */
    public String label() {
        return label;
    }
}
