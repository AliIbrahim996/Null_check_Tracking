package com.aliibrahim.nullcheck.core;

import java.util.List;

/**
 * Everything {@link NullCheckAnalyzer} found in one source file.
 *
 * @param source       the analysed source text, with line endings normalised to {@code \n}
 * @param ifStatements every {@code if} statement, in source order
 * @param comparands   every expression compared with {@code null} in an {@code if} condition, in source order
 * @param definitions  declarations and assignments of the non-method-call comparands that come before their
 *                     first check, grouped by comparand in the order the comparands are first checked
 */
public record AnalysisResult(
        String source,
        List<IfStatement> ifStatements,
        List<NullComparand> comparands,
        List<Definition> definitions) {

    /**
     * Creates a result with unmodifiable copies of the lists.
     *
     * @param source       the analysed source text
     * @param ifStatements every {@code if} statement
     * @param comparands   every null comparand
     * @param definitions  definitions of the comparands
     */
    public AnalysisResult {
        ifStatements = List.copyOf(ifStatements);
        comparands = List.copyOf(comparands);
        definitions = List.copyOf(definitions);
    }

    /**
     * Returns the share of {@code if} statements that check something for {@code null}.
     *
     * @return a rounded percentage from 0 to 100; 0 when there are no {@code if} statements
     */
    public long percentOfIfsCheckingNull() {
        return percent(ifStatements.stream().filter(IfStatement::checksNull).count(), ifStatements.size());
    }

    /**
     * Returns the share of null comparands that are names declared in the given scope.
     *
     * @param scope the scope to count
     * @return a rounded percentage from 0 to 100; 0 when there are no comparands
     */
    public long percentOfComparands(NameScope scope) {
        return percent(comparands.stream().filter(c -> c.scope() == scope).count(), comparands.size());
    }

    /**
     * Returns the share of null comparands of the given kind.
     *
     * @param kind the kind to count
     * @return a rounded percentage from 0 to 100; 0 when there are no comparands
     */
    public long percentOfComparands(ComparandKind kind) {
        return percent(comparands.stream().filter(c -> c.kind() == kind).count(), comparands.size());
    }

    private static long percent(long part, long whole) {
        return whole == 0 ? 0 : Math.round(100.0 * part / whole);
    }
}
