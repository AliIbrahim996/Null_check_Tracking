package com.aliibrahim.nullcheck.core;

import static com.aliibrahim.nullcheck.core.SyntaxTree.field;
import static com.aliibrahim.nullcheck.core.SyntaxTree.line;
import static com.aliibrahim.nullcheck.core.SyntaxTree.parent;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.treesitter.TSNode;

/**
 * Finds the null checks in a Java, PHP or C++ source file.
 *
 * <p>The source is parsed into a syntax tree with <a href="https://tree-sitter.github.io/">tree-sitter</a>.
 * Every {@code if} statement's condition is split at its top-level {@code &&} and {@code ||} operators (and
 * the language's equivalents), and every part that compares an expression with {@code null} is recorded,
 * together with null-checking calls such as {@code Objects.isNull(x)}, PHP's {@code isset($x)} and C++'s
 * implicit pointer checks such as {@code if (p)}. Each comparand is classified as a method call, field access
 * or name, and names are resolved through the tree to a parameter, local variable or member. Finally, the
 * declarations and assignments of each comparand that come before its first check are collected so the report
 * can explain where a {@code null} may come from. The language-specific details are described on
 * {@link Language}.
 *
 * <p>Comments and string literals never produce findings, and source that does not compile is analysed as far
 * as it can be parsed. Instances are stateless and can be reused and shared between threads.
 */
public final class NullCheckAnalyzer {

    /** Creates an analyzer. */
    public NullCheckAnalyzer() {
    }

    /**
     * Analyses a source file. The language is chosen from the file extension ({@link Language#forFileName}),
     * and files with an unknown extension are analysed as Java. The file is read as UTF-8, falling back to
     * ISO-8859-1 if it is not valid UTF-8.
     *
     * @param file the file to analyse
     * @return what was found
     * @throws IOException if the file cannot be read
     */
    public AnalysisResult analyze(Path file) throws IOException {
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (CharacterCodingException notUtf8) {
            text = Files.readString(file, StandardCharsets.ISO_8859_1);
        }
        Path name = file.getFileName();
        Language language = name == null ? Language.JAVA
                : Language.forFileName(name.toString()).orElse(Language.JAVA);
        return analyze(text, language);
    }

    /**
     * Analyses Java source text.
     *
     * @param source the source text; it does not need to compile
     * @return what was found
     */
    public AnalysisResult analyze(String source) {
        return analyze(source, Language.JAVA);
    }

    /**
     * Analyses source text in the given language.
     *
     * @param source   the source text; it does not need to compile
     * @param language the language of the source
     * @return what was found
     */
    public AnalysisResult analyze(String source, Language language) {
        LanguageRules rules = LanguageRules.of(Objects.requireNonNull(language, "language"));
        String normalized = source.replace("\r\n", "\n").replace('\r', '\n');
        try (SyntaxTree tree = new SyntaxTree(normalized, rules.prefix(normalized), rules)) {
            List<IfStatement> ifs = new ArrayList<>();
            List<NullComparand> comparands = new ArrayList<>();
            Map<String, Check> firstChecks = new LinkedHashMap<>();
            SyntaxTree.walk(tree.root, node -> {
                if (rules.isIf(node)) {
                    analyzeIf(node, tree, rules, ifs, comparands, firstChecks);
                }
            });
            return new AnalysisResult(normalized, ifs, comparands, definitions(tree, rules, firstChecks));
        }
    }

    /** The first check of a comparand, which its definitions must come before. */
    private record Check(String subject, int line) {
    }

    private static void analyzeIf(TSNode ifNode, SyntaxTree tree, LanguageRules rules, List<IfStatement> ifs,
                                  List<NullComparand> comparands, Map<String, Check> firstChecks) {
        int line = line(ifNode);
        TSNode condition = rules.condition(ifNode);
        TSNode value = condition == null ? null : rules.conditionValue(condition);
        List<TSNode> parts = new ArrayList<>();
        if (value != null && !value.isMissing()) {
            split(value, rules, parts);
        }
        boolean checksNull = false;
        for (TSNode part : parts) {
            for (TSNode checked : rules.comparands(part, tree)) {
                checksNull = true;
                TSNode expression = LanguageRules.unwrapParens(checked);
                TSNode subject = LanguageRules.unwrapCasts(expression);
                ComparandKind kind = rules.kindOf(subject);
                NameScope scope = kind == ComparandKind.NAME ? rules.resolveScope(subject, tree) : null;
                comparands.add(new NullComparand(tree.code(expression), kind, scope, line));
                if (kind != ComparandKind.METHOD_CALL) {
                    firstChecks.putIfAbsent(rules.key(subject, tree), new Check(tree.code(subject), line));
                }
            }
        }
        ifs.add(new IfStatement(conditionText(condition, tree), line, checksNull));
    }

    /** Splits a condition at its top-level logical operators and negated groups. */
    private static void split(TSNode node, LanguageRules rules, List<TSNode> out) {
        TSNode e = LanguageRules.unwrapParens(node);
        if (rules.isLogical(e)) {
            TSNode left = field(e, "left");
            TSNode right = field(e, "right");
            if (left != null) {
                split(left, rules, out);
            }
            if (right != null) {
                split(right, rules, out);
            }
        } else if (LanguageRules.isNot(e) && LanguageRules.operand(e).getType().equals("parenthesized_expression")) {
            split(LanguageRules.operand(e), rules, out);
        } else {
            out.add(e);
        }
    }

    private static String conditionText(TSNode condition, SyntaxTree tree) {
        if (condition == null) {
            return "";
        }
        String text = tree.code(condition);
        return text.startsWith("(") && text.endsWith(")") ? text.substring(1, text.length() - 1).trim() : text;
    }

    // ---------------------------------------------------------------- definitions

    /**
     * Collects, for each checked variable and field, the assignments and declarations that start on a line
     * before its first check, in the order the comparands are first checked. Variables are matched by
     * {@linkplain LanguageRules#key key}, so a same-named variable in another method does not count.
     */
    private static List<Definition> definitions(SyntaxTree tree, LanguageRules rules, Map<String, Check> firstChecks) {
        Map<String, List<Definition>> found = new LinkedHashMap<>();
        firstChecks.keySet().forEach(key -> found.put(key, new ArrayList<>()));
        SyntaxTree.walk(tree.root, node -> {
            if (rules.assignmentTypes().contains(node.getType())) {
                TSNode left = field(node, "left");
                String key = left == null ? null : rules.key(left, tree);
                Check check = key == null ? null : firstChecks.get(key);
                if (check != null && line(node) < check.line()) {
                    found.get(key).add(new Definition(check.subject(), tree.code(statement(node)), line(node)));
                }
                return;
            }
            for (String key : rules.declaredKeys(node, tree)) {
                Check check = firstChecks.get(key);
                if (check != null && line(node) < check.line()) {
                    found.get(key).add(
                            new Definition(check.subject(), tree.code(rules.definitionNode(node)), line(node)));
                }
            }
        });
        List<Definition> result = new ArrayList<>();
        found.values().forEach(result::addAll);
        return result;
    }

    /** Returns the statement an assignment is part of, so that the definition includes its semicolon. */
    private static TSNode statement(TSNode assignment) {
        TSNode parent = parent(assignment);
        return parent != null && parent.getType().equals("expression_statement") ? parent : assignment;
    }
}
