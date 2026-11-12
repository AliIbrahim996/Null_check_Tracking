package com.aliibrahim.nullcheck.core;

import static com.aliibrahim.nullcheck.core.SyntaxTree.field;
import static com.aliibrahim.nullcheck.core.SyntaxTree.fieldChildren;
import static com.aliibrahim.nullcheck.core.SyntaxTree.namedChildren;
import static com.aliibrahim.nullcheck.core.SyntaxTree.parent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.treesitter.TSLanguage;
import org.treesitter.TSNode;
import org.treesitter.TreeSitterJava;

/**
 * Java: {@code x == null}, and {@code Objects.isNull(x)} / {@code Objects.nonNull(x)}, also when statically
 * imported.
 */
final class JavaRules extends LanguageRules {

    private static final Set<String> CALLS = Set.of("method_invocation", "object_creation_expression");
    private static final Set<String> FIELDS = Set.of("field_access", "array_access");
    private static final Set<String> NAMES = Set.of("identifier");
    private static final Set<String> ASSIGNMENTS = Set.of("assignment_expression");
    private static final Set<String> NULL_CHECKS = Set.of("isNull", "nonNull");
    private static final Set<String> OBJECTS = Set.of("Objects", "java.util.Objects");
    private static final Pattern STATIC_IMPORT =
            Pattern.compile("import static java\\.util\\.Objects\\.(\\*|isNull|nonNull) ?;");
    private static final Set<String> METHODS = Set.of(
            "method_declaration", "constructor_declaration", "compact_constructor_declaration", "lambda_expression");
    private static final Set<String> BLOCKS = Set.of("block", "constructor_body", "switch_rule");
    private static final Set<String> TYPE_BODIES = Set.of(
            "class_body", "enum_body", "interface_body", "annotation_type_body", "enum_body_declarations");
    private static final Set<String> DECLARATIONS = Set.of(
            "local_variable_declaration", "field_declaration", "constant_declaration");

    /** Where a name is declared. */
    private record Resolution(NameScope scope, TSNode node) {
    }

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
        if (name == null || arguments == null || !NULL_CHECKS.contains(tree.text(name))) {
            return null;
        }
        if (object == null ? !staticallyImported(tree) : !OBJECTS.contains(tree.code(object))) {
            return null;
        }
        List<TSNode> args = namedChildren(arguments).stream().filter(a -> !a.getType().endsWith("comment")).toList();
        return args.size() == 1 ? args : null;
    }

    /** Whether the file statically imports {@code Objects.isNull}/{@code nonNull}, so they can be called bare. */
    private static boolean staticallyImported(SyntaxTree tree) {
        for (TSNode child : namedChildren(tree.root)) {
            if (child.getType().equals("import_declaration") && STATIC_IMPORT.matcher(tree.code(child)).matches()) {
                return true;
            }
        }
        return false;
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
     * A name without a visible declaration is most likely an inherited field or a static import, so it is
     * reported as a member.
     */
    @Override
    NameScope resolveScope(TSNode name, SyntaxTree tree) {
        if (!name.getType().equals("identifier")) {
            return null;
        }
        Resolution r = resolve(tree.text(name), name, false, tree);
        return r == null ? NameScope.MEMBER : r.scope();
    }

    @Override
    TSNode declarationNode(TSNode expression, SyntaxTree tree) {
        if (expression.getType().equals("identifier")) {
            Resolution r = resolve(tree.text(expression), expression, false, tree);
            return r == null ? null : r.node();
        }
        TSNode object = field(expression, "object");
        TSNode fieldName = field(expression, "field");
        if (expression.getType().equals("field_access") && object != null && object.getType().equals("this")
                && fieldName != null) {
            Resolution r = resolve(tree.text(fieldName), expression, true, tree);
            return r == null ? null : r.node();
        }
        return null;
    }

    /**
     * Walks out from {@code from} through the enclosing blocks, methods, lambdas and (anonymous) classes to the
     * declaration of {@code n}; with {@code membersOnly}, only fields count ({@code this.n}).
     */
    private Resolution resolve(String n, TSNode from, boolean membersOnly, SyntaxTree tree) {
        int position = tree.start(from);
        for (TSNode a = parent(from); a != null; a = parent(a)) {
            String type = a.getType();
            if (TYPE_BODIES.contains(type)) {
                for (TSNode member : namedChildren(a)) {
                    if (declaredNames(member, tree).contains(n)
                            || (member.getType().equals("enum_constant") && n.equals(nameOf(member, tree)))) {
                        return new Resolution(NameScope.MEMBER, member);
                    }
                }
                if (membersOnly) {
                    return null; // this.n refers to the innermost class only
                }
                continue;
            }
            if (type.equals("record_declaration")) {
                TSNode component = parameterNamed(field(a, "parameters"), n, tree);
                if (component != null) {
                    return new Resolution(NameScope.MEMBER, component);
                }
                continue;
            }
            if (membersOnly) {
                continue;
            }
            TSNode local = null;
            if (METHODS.contains(type)) {
                TSNode parameters = field(a, "parameters");
                if (parameters != null && parameters.getType().equals("identifier")) { // x -> ...
                    if (n.equals(tree.text(parameters))) {
                        return new Resolution(NameScope.PARAMETER, parameters);
                    }
                } else {
                    TSNode parameter = parameterNamed(parameters, n, tree);
                    if (parameter != null) {
                        return new Resolution(NameScope.PARAMETER, parameter);
                    }
                }
                local = patternBinding(field(a, "body"), n, position, tree);
            } else if (type.equals("catch_clause")) {
                for (TSNode child : namedChildren(a)) {
                    if (child.getType().equals("catch_formal_parameter") && n.equals(nameOf(child, tree))) {
                        local = child;
                    }
                }
            } else if (type.equals("enhanced_for_statement")) {
                local = n.equals(nameOf(a, tree)) ? a : null;
            } else if (type.equals("for_statement")) {
                for (TSNode init : fieldChildren(a, "init")) {
                    if (declaredNames(init, tree).contains(n)) {
                        local = init;
                    }
                }
            } else if (type.equals("try_with_resources_statement")) {
                TSNode resources = field(a, "resources");
                for (TSNode resource : resources == null ? List.<TSNode>of() : namedChildren(resources)) {
                    if (tree.start(resource) < position && n.equals(nameOf(resource, tree))) {
                        local = resource;
                    }
                }
            } else if (BLOCKS.contains(type)) {
                local = localIn(namedChildren(a), n, position, tree);
            } else if (type.equals("switch_block")) {
                // A local declared in one case group is in scope in the groups after it.
                List<TSNode> statements = new ArrayList<>();
                for (TSNode group : namedChildren(a)) {
                    if (group.getType().equals("switch_block_statement_group")) {
                        statements.addAll(namedChildren(group));
                    }
                }
                local = localIn(statements, n, position, tree);
            }
            if (local != null) {
                return new Resolution(NameScope.LOCAL, local);
            }
        }
        return null;
    }

    private TSNode localIn(List<TSNode> statements, String n, int position, SyntaxTree tree) {
        for (TSNode statement : statements) {
            if (tree.start(statement) < position && statement.getType().equals("local_variable_declaration")
                    && declaredNames(statement, tree).contains(n)) {
                return statement;
            }
        }
        return null;
    }

    /**
     * Finds a pattern variable named {@code n} bound before {@code position} in a method body:
     * {@code o instanceof String s}, record patterns and {@code case String s ->}. Pattern variables are only in
     * scope where the pattern matched; this simplification treats them as locals of the whole method.
     */
    private static TSNode patternBinding(TSNode body, String n, int position, SyntaxTree tree) {
        if (body == null) {
            return null;
        }
        TSNode[] found = {null};
        SyntaxTree.walk(body, node -> {
            if (found[0] != null || tree.start(node) >= position) {
                return;
            }
            TSNode name = switch (node.getType()) {
                case "instanceof_expression" -> field(node, "name");
                case "type_pattern", "record_pattern_component" -> lastIdentifier(node);
                default -> null;
            };
            if (name != null && n.equals(tree.text(name))) {
                found[0] = node;
            }
        });
        return found[0];
    }

    private static TSNode lastIdentifier(TSNode node) {
        TSNode last = null;
        for (TSNode child : namedChildren(node)) {
            if (child.getType().equals("identifier")) {
                last = child;
            }
        }
        return last;
    }

    private static TSNode parameterNamed(TSNode parameters, String n, SyntaxTree tree) {
        if (parameters == null) {
            return null;
        }
        for (TSNode p : namedChildren(parameters)) {
            String name = p.getType().equals("identifier") ? tree.text(p) : nameOf(p, tree);
            if (n.equals(name)) {
                return p;
            }
        }
        return null;
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

    private static List<String> declaredNames(TSNode node, SyntaxTree tree) {
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

    @Override
    List<String> declaredKeys(TSNode node, SyntaxTree tree) {
        return declaredNames(node, tree).stream().map(n -> key(n, node, tree)).toList();
    }
}
