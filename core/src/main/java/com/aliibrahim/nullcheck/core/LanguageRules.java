package com.aliibrahim.nullcheck.core;

import static com.aliibrahim.nullcheck.core.SyntaxTree.field;
import static com.aliibrahim.nullcheck.core.SyntaxTree.namedChildren;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.treesitter.TSLanguage;
import org.treesitter.TSNode;

/**
 * What the analysis needs to know about one language's syntax tree: which nodes are {@code if} statements and
 * null checks, how expressions are classified and where a name is declared. The defaults fit the node types
 * that the Java, PHP and C++ grammars share.
 */
abstract class LanguageRules {

    /** Returns the rules for a language. */
    static LanguageRules of(Language language) {
        return switch (language) {
            case JAVA -> new JavaRules();
            case PHP -> new PhpRules();
            case CPP -> new CppRules();
        };
    }

    /** Returns a new instance of the tree-sitter grammar. */
    abstract TSLanguage grammar();

    /** Text put in front of the source before parsing, on the same line; empty by default. */
    String prefix(String source) {
        return "";
    }

    boolean isIf(TSNode node) {
        return node.getType().equals("if_statement");
    }

    /** Returns the condition of an {@code if} as written, including any parentheses. */
    TSNode condition(TSNode ifNode) {
        return field(ifNode, "condition");
    }

    /** Returns the expression part of a condition, or {@code null} if it has none. */
    TSNode conditionValue(TSNode condition) {
        return condition;
    }

    Set<String> logicalOperators() {
        return Set.of("&&", "||");
    }

    Set<String> equalityOperators() {
        return Set.of("==", "!=");
    }

    abstract boolean isNullLiteral(TSNode node);

    /**
     * If {@code node} is a call of a null-checking function such as {@code Objects.isNull(x)}, returns the
     * checked arguments; otherwise {@code null}.
     */
    List<TSNode> nullCheckArguments(TSNode node, SyntaxTree tree) {
        return null;
    }

    /** Returns the expressions that a condition checks without comparing them, such as a C++ {@code if (p)}. */
    List<TSNode> implicitChecks(TSNode condition, SyntaxTree tree) {
        return List.of();
    }

    abstract Set<String> callTypes();

    abstract Set<String> fieldAccessTypes();

    abstract Set<String> nameTypes();

    /** Returns where a name is declared, or {@code null} if the node is not a variable name. */
    abstract NameScope resolveScope(TSNode name, SyntaxTree tree);

    abstract Set<String> assignmentTypes();

    /** Normalises an assigned or checked expression so that, for example, {@code this.x} matches {@code x}. */
    String normalize(String expression) {
        return expression;
    }

    /**
     * If {@code node} declares variables, returns the normalised expressions it declares, for example
     * {@code ["name"]} for {@code String name = null;}; otherwise an empty list.
     */
    abstract List<String> declaredExpressions(TSNode node, SyntaxTree tree);

    /** Returns the node whose text is shown as the definition of a declaration found by {@link #declaredExpressions}. */
    TSNode definitionNode(TSNode declaration) {
        return declaration;
    }

    // ---------------------------------------------------------------- shared helpers

    /** Returns the expressions that one part of a condition checks for {@code null}. */
    final List<TSNode> comparands(TSNode part, SyntaxTree tree) {
        TSNode e = unwrapParens(part);
        if (e.getType().equals("binary_expression") && equalityOperators().contains(operator(e))) {
            TSNode left = field(e, "left");
            TSNode right = field(e, "right");
            if (left == null || right == null) {
                return List.of();
            }
            if (isNullLiteral(right) && !isNullLiteral(left)) {
                return List.of(left);
            }
            if (isNullLiteral(left) && !isNullLiteral(right)) {
                return List.of(right);
            }
            return List.of();
        }
        List<TSNode> arguments = nullCheckArguments(e, tree);
        if (arguments == null && isNot(e)) {
            arguments = nullCheckArguments(unwrapParens(operand(e)), tree);
        }
        return arguments != null ? arguments : implicitChecks(e, tree);
    }

    /** Whether a binary expression is a logical {@code &&}/{@code ||} (or language equivalent). */
    final boolean isLogical(TSNode node) {
        return node.getType().equals("binary_expression") && logicalOperators().contains(operator(node));
    }

    final ComparandKind kindOf(TSNode subject) {
        String type = subject.getType();
        if (nameTypes().contains(type)) {
            return ComparandKind.NAME;
        }
        if (callTypes().contains(type)) {
            return ComparandKind.METHOD_CALL;
        }
        if (fieldAccessTypes().contains(type)) {
            return ComparandKind.FIELD_ACCESS;
        }
        if (SyntaxTree.contains(subject, callTypes())) {
            return ComparandKind.METHOD_CALL;
        }
        return SyntaxTree.contains(subject, fieldAccessTypes()) ? ComparandKind.FIELD_ACCESS : ComparandKind.NAME;
    }

    /** Returns the operator of a binary expression in lower case. */
    static String operator(TSNode binary) {
        TSNode op = field(binary, "operator");
        if (op != null) {
            return op.getType().toLowerCase(Locale.ROOT);
        }
        for (int i = 0; i < binary.getChildCount(); i++) {
            TSNode child = binary.getChild(i);
            if (!child.isNamed()) {
                return child.getType().toLowerCase(Locale.ROOT);
            }
        }
        return "";
    }

    /** Whether a node is a logical negation {@code !x}. */
    static boolean isNot(TSNode node) {
        String type = node.getType();
        return (type.equals("unary_expression") || type.equals("unary_op_expression"))
                && node.getChildCount() > 0 && node.getChild(0).getType().equals("!");
    }

    /** Returns the operand of a unary expression. */
    static TSNode operand(TSNode unary) {
        List<TSNode> children = namedChildren(unary);
        return children.get(children.size() - 1);
    }

    static TSNode unwrapParens(TSNode node) {
        TSNode n = node;
        while (n.getType().equals("parenthesized_expression")) {
            TSNode inner = null;
            for (TSNode child : namedChildren(n)) {
                if (!child.getType().endsWith("comment")) {
                    inner = child;
                }
            }
            if (inner == null) {
                break;
            }
            n = inner;
        }
        return n;
    }

    /** Removes parentheses and casts: {@code ((String) o)} becomes {@code o}. */
    static TSNode unwrapCasts(TSNode node) {
        TSNode n = unwrapParens(node);
        while (n.getType().equals("cast_expression")) {
            TSNode value = field(n, "value");
            if (value == null) {
                List<TSNode> children = namedChildren(n);
                value = children.get(children.size() - 1);
            }
            n = unwrapParens(value);
        }
        return n;
    }
}
