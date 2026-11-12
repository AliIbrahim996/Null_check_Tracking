package com.aliibrahim.nullcheck.core;

import static com.aliibrahim.nullcheck.core.SyntaxTree.field;
import static com.aliibrahim.nullcheck.core.SyntaxTree.fieldChildren;
import static com.aliibrahim.nullcheck.core.SyntaxTree.namedChildren;
import static com.aliibrahim.nullcheck.core.SyntaxTree.parent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.treesitter.TSLanguage;
import org.treesitter.TSNode;
import org.treesitter.TreeSitterJava;

/** Java: {@code x == null}, {@code Objects.isNull(x)} and {@code Objects.nonNull(x)}. */
final class JavaRules extends LanguageRules {

    private static final Set<String> CALLS = Set.of("method_invocation", "object_creation_expression");
    private static final Set<String> FIELDS = Set.of("field_access", "array_access");
    private static final Set<String> NAMES = Set.of("identifier");
    private static final Set<String> ASSIGNMENTS = Set.of("assignment_expression");
    private static final Set<String> NULL_CHECKS = Set.of("isNull", "nonNull");
    private static final Set<String> OBJECTS = Set.of("Objects", "java.util.Objects");
    private static final Set<String> METHODS = Set.of(
            "method_declaration", "constructor_declaration", "compact_constructor_declaration", "lambda_expression");
    private static final Set<String> BLOCKS = Set.of(
            "block", "constructor_body", "switch_block_statement_group", "switch_rule");
    private static final Set<String> TYPE_BODIES = Set.of(
            "class_body", "enum_body", "interface_body", "annotation_type_body", "enum_body_declarations");
    private static final Set<String> DECLARATIONS = Set.of(
            "local_variable_declaration", "field_declaration", "constant_declaration");

    @Override
    TSLanguage grammar() {
        return new TreeSitterJava();
    }

    @Override
    boolean isNullLiteral(TSNode node) {
        return node.getType().equals("null_literal");
    }

    @Override
    List<TSNode> nullCheckArguments(TSNode node, SyntaxTree tree) {
        if (!node.getType().equals("method_invocation")) {
            return null;
        }
        TSNode name = field(node, "name");
        TSNode object = field(node, "object");
        TSNode arguments = field(node, "arguments");
        if (name == null || object == null || arguments == null
                || !NULL_CHECKS.contains(tree.text(name)) || !OBJECTS.contains(tree.code(object))) {
            return null;
        }
        List<TSNode> args = namedChildren(arguments).stream().filter(a -> !a.getType().endsWith("comment")).toList();
        return args.size() == 1 ? args : null;
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
    String normalize(String expression) {
        return expression.startsWith("this.") ? expression.substring(5) : expression;
    }

    /**
     * Walks out from the name through the enclosing blocks, methods, lambdas and (anonymous) classes. A name
     * without a visible declaration is most likely an inherited field or a static import, so it is reported
     * as a member.
     */
    @Override
    NameScope resolveScope(TSNode name, SyntaxTree tree) {
        if (!name.getType().equals("identifier")) {
            return null;
        }
        String n = tree.text(name);
        int position = tree.start(name);
        for (TSNode a = parent(name); a != null; a = parent(a)) {
            String type = a.getType();
            if (METHODS.contains(type)) {
                if (parameterNames(field(a, "parameters"), tree).contains(n)) {
                    return NameScope.PARAMETER;
                }
            } else if (type.equals("catch_clause")) {
                for (TSNode child : namedChildren(a)) {
                    if (child.getType().equals("catch_formal_parameter") && n.equals(nameOf(child, tree))) {
                        return NameScope.LOCAL;
                    }
                }
            } else if (type.equals("enhanced_for_statement")) {
                if (n.equals(nameOf(a, tree))) {
                    return NameScope.LOCAL;
                }
            } else if (type.equals("for_statement")) {
                for (TSNode init : fieldChildren(a, "init")) {
                    if (declaredExpressions(init, tree).contains(n)) {
                        return NameScope.LOCAL;
                    }
                }
            } else if (type.equals("try_with_resources_statement")) {
                TSNode resources = field(a, "resources");
                for (TSNode resource : resources == null ? List.<TSNode>of() : namedChildren(resources)) {
                    if (tree.start(resource) < position && n.equals(nameOf(resource, tree))) {
                        return NameScope.LOCAL;
                    }
                }
            } else if (BLOCKS.contains(type)) {
                for (TSNode statement : namedChildren(a)) {
                    if (tree.start(statement) < position && statement.getType().equals("local_variable_declaration")
                            && declaredExpressions(statement, tree).contains(n)) {
                        return NameScope.LOCAL;
                    }
                }
            } else if (TYPE_BODIES.contains(type)) {
                for (TSNode member : namedChildren(a)) {
                    if (declaredExpressions(member, tree).contains(n)
                            || (member.getType().equals("enum_constant") && n.equals(nameOf(member, tree)))) {
                        return NameScope.MEMBER;
                    }
                }
            } else if (type.equals("record_declaration")) {
                if (parameterNames(field(a, "parameters"), tree).contains(n)) {
                    return NameScope.MEMBER;
                }
            }
        }
        return NameScope.MEMBER;
    }

    private static List<String> parameterNames(TSNode parameters, SyntaxTree tree) {
        List<String> names = new ArrayList<>();
        if (parameters == null) {
            return names;
        }
        if (parameters.getType().equals("identifier")) { // x -> ...
            names.add(tree.text(parameters));
            return names;
        }
        for (TSNode p : namedChildren(parameters)) {
            String name = p.getType().equals("identifier") ? tree.text(p) : nameOf(p, tree);
            if (name != null) {
                names.add(name);
            }
        }
        return names;
    }

    /** Returns the name declared by a parameter, resource, enum constant or similar node. */
    private static String nameOf(TSNode node, SyntaxTree tree) {
        TSNode name = field(node, "name");
        if (name != null) {
            return tree.text(name);
        }
        for (TSNode child : namedChildren(node)) {
            if (child.getType().equals("variable_declarator")) { // String... args
                TSNode n = field(child, "name");
                return n == null ? null : tree.text(n);
            }
        }
        return null;
    }

    @Override
    List<String> declaredExpressions(TSNode node, SyntaxTree tree) {
        if (!DECLARATIONS.contains(node.getType())) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (TSNode declarator : fieldChildren(node, "declarator")) {
            TSNode name = field(declarator, "name");
            if (name != null) {
                names.add(tree.text(name));
            }
        }
        return names;
    }
}
