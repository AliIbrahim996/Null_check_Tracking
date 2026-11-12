package com.aliibrahim.nullcheck.core;

/**
 * An expression that an {@code if} condition compares with {@code null}.
 *
 * @param expression the expression, for example {@code customer.address}
 * @param kind       the syntactic form of the expression
 * @param scope      where the name is declared when {@code kind} is {@link ComparandKind#NAME}, otherwise
 *                   {@code null}
 * @param line       the 1-based line of the {@code if} statement
 */
public record NullComparand(String expression, ComparandKind kind, NameScope scope, int line) {
}
