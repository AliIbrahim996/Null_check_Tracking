package com.aliibrahim.nullcheck.core;

import static com.aliibrahim.nullcheck.core.SyntaxTree.field;
import static com.aliibrahim.nullcheck.core.SyntaxTree.namedChildren;
import static com.aliibrahim.nullcheck.core.SyntaxTree.parent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.treesitter.TSLanguage;
import org.treesitter.TSNode;
import org.treesitter.TreeSitterPhp;

/**
 * PHP: comparisons with {@code null} using {@code ==}, {@code ===}, {@code !=}, {@code !==} or {@code <>} (the
 * keyword in any case), and {@code is_null(...)} and {@code isset(...)}, each argument of {@code isset} being a
 * comparand. {@code elseif} and the {@code and}, {@code or} and {@code xor} keywords are understood.
 *
 * <p>A bare {@code $variable} is a parameter when the enclosing function, method or closure declares it, and a
 * local otherwise (variables captured with {@code use} are locals). PHP has no implicit members, so properties
 * always appear as field accesses such as {@code $this->name}; their declarations count as definitions.
 */
final class PhpRules extends LanguageRules {

    private static final Set<String> CALLS = Set.of("function_call_expression", "member_call_expression",
            "nullsafe_member_call_expression", "scoped_call_expression", "object_creation_expression");
    private static final Set<String> FIELDS = Set.of("member_access_expression", "nullsafe_member_access_expression",
            "scoped_property_access_expression", "subscript_expression", "class_constant_access_expression");
    private static final Set<String> NAMES = Set.of("variable_name", "name", "qualified_name");
    private static final Set<String> ASSIGNMENTS = Set.of(
            "assignment_expression", "augmented_assignment_expression", "reference_assignment_expression");
    private static final Set<String> FUNCTIONS = Set.of(
            "function_definition", "method_declaration", "anonymous_function", "arrow_function");
    private static final Set<String> PARAMETERS = Set.of(
            "simple_parameter", "variadic_parameter", "property_promotion_parameter");
    private static final Set<String> NULL_FUNCTIONS = Set.of("is_null", "isset");

    @Override
    TSLanguage grammar() {
        return new TreeSitterPhp();
    }

    /** Snippets without an opening tag are treated as PHP code rather than inline HTML. */
    @Override
    String prefix(String source) {
        return source.contains("<?") ? "" : "<?php ";
    }

    @Override
    boolean isIf(TSNode node) {
        return node.getType().equals("if_statement") || node.getType().equals("else_if_clause");
    }

    @Override
    Set<String> logicalOperators() {
        return Set.of("&&", "||", "and", "or", "xor");
    }

    @Override
    Set<String> equalityOperators() {
        return Set.of("==", "!=", "===", "!==", "<>");
    }

    @Override
    boolean isNullLiteral(TSNode node) {
        return node.getType().equals("null");
    }

    @Override
    List<TSNode> nullCheckArguments(TSNode node, SyntaxTree tree) {
        if (!node.getType().equals("function_call_expression")) {
            return null;
        }
        TSNode function = field(node, "function");
        TSNode arguments = field(node, "arguments");
        if (function == null || arguments == null) {
            return null;
        }
        String name = tree.text(function).toLowerCase(Locale.ROOT);
        if (!NULL_FUNCTIONS.contains(name.startsWith("\\") ? name.substring(1) : name)) {
            return null;
        }
        List<TSNode> checked = new ArrayList<>();
        for (TSNode argument : namedChildren(arguments)) {
            if (argument.getType().equals("argument")) {
                List<TSNode> parts = namedChildren(argument);
                checked.add(parts.get(parts.size() - 1));
            }
        }
        return checked;
    }

    @Override
    Set<String> callTypes() {
        return CALLS;
    }

    @Override
    Set<String> fieldAccessTypes() {
        return FIELDS;
    }

    @Override
    Set<String> nameTypes() {
        return NAMES;
    }

    @Override
    Set<String> assignmentTypes() {
        return ASSIGNMENTS;
    }

    @Override
    NameScope resolveScope(TSNode name, SyntaxTree tree) {
        if (!name.getType().equals("variable_name")) {
            return null; // a constant
        }
        String n = tree.text(name);
        if (n.equals("$this")) {
            return NameScope.MEMBER;
        }
        for (TSNode a = parent(name); a != null; a = parent(a)) {
            if (FUNCTIONS.contains(a.getType())) {
                TSNode parameters = field(a, "parameters");
                if (parameters != null) {
                    for (TSNode p : namedChildren(parameters)) {
                        TSNode pName = field(p, "name");
                        if (PARAMETERS.contains(p.getType()) && pName != null && n.equals(tree.text(pName))) {
                            return NameScope.PARAMETER;
                        }
                    }
                }
                if (!a.getType().equals("arrow_function")) {
                    return NameScope.LOCAL; // only arrow functions see the variables around them
                }
            }
        }
        return NameScope.LOCAL;
    }

    /** Property declarations define {@code $this->name} and {@code self::$name}; parameter defaults define the parameter. */
    @Override
    List<String> declaredExpressions(TSNode node, SyntaxTree tree) {
        String type = node.getType();
        TSNode name = field(node, "name");
        if (name == null) {
            return List.of();
        }
        if (type.equals("property_element")) {
            String property = tree.text(name).substring(1);
            return List.of("$this->" + property, "self::$" + property, "static::$" + property);
        }
        if (PARAMETERS.contains(type) && field(node, "default_value") != null) {
            return List.of(tree.text(name));
        }
        return List.of();
    }

    @Override
    TSNode definitionNode(TSNode declaration) {
        TSNode parent = parent(declaration);
        return declaration.getType().equals("property_element") && parent != null ? parent : declaration;
    }

    @Override
    String normalize(String expression) {
        return expression.replaceAll("\\s+", "");
    }
}
