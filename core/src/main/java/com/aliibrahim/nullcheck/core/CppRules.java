package com.aliibrahim.nullcheck.core;

import static com.aliibrahim.nullcheck.core.SyntaxTree.field;
import static com.aliibrahim.nullcheck.core.SyntaxTree.fieldChildren;
import static com.aliibrahim.nullcheck.core.SyntaxTree.namedChildren;
import static com.aliibrahim.nullcheck.core.SyntaxTree.parent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.treesitter.TSLanguage;
import org.treesitter.TSNode;
import org.treesitter.TreeSitterCpp;

/**
 * C and C++: comparisons with {@code nullptr} or {@code NULL}, and implicit checks of pointers, such as
 * {@code if (p)}, {@code if (!p)} and {@code if (Node* n = next())}, where the variable is declared as a raw
 * pointer, {@code std::unique_ptr} or {@code std::shared_ptr}.
 *
 * <p>Names resolve to parameters, locals, or members of the enclosing class, including the class of an
 * out-of-line member function ({@code void List::clear()}) when it is defined in the same file. Global and
 * namespace variables, and names without a visible declaration (such as inherited members), count as members.
 */
final class CppRules extends LanguageRules {

    private static final Set<String> CALLS = Set.of("call_expression", "new_expression");
    private static final Set<String> FIELDS = Set.of(
            "field_expression", "subscript_expression", "qualified_identifier", "pointer_expression");
    private static final Set<String> NAMES = Set.of("identifier");
    private static final Set<String> ASSIGNMENTS = Set.of("assignment_expression");
    private static final Set<String> BLOCKS = Set.of("compound_statement", "case_statement");
    private static final Set<String> GLOBAL_SCOPES = Set.of("translation_unit", "declaration_list");
    private static final Set<String> DECLARATIONS = Set.of(
            "declaration", "field_declaration", "parameter_declaration", "optional_parameter_declaration");
    private static final Pattern SMART_POINTER = Pattern.compile("^(?:std::)?(?:unique_ptr|shared_ptr)\\b.*");

    /** Where a name is declared. */
    private record Declaration(NameScope scope, TSNode node) {
    }

    private final Supplier<TSLanguage> grammar;

    /** Rules for C++ with the C++ grammar. */
    CppRules() {
        this(TreeSitterCpp::new);
    }

    /** Rules for C++ or C; the C grammar has the same node types for everything these rules use. */
    CppRules(Supplier<TSLanguage> grammar) {
        this.grammar = grammar;
    }

    @Override
    TSLanguage grammar() {
        return grammar.get();
    }

    @Override
    TSNode conditionValue(TSNode condition) {
        return condition.getType().equals("condition_clause") ? field(condition, "value") : condition;
    }

    @Override
    boolean isNullLiteral(TSNode node) {
        return node.getType().equals("null"); // nullptr and NULL
    }

    @Override
    List<TSNode> implicitChecks(TSNode condition, SyntaxTree tree) {
        TSNode e = isNot(condition) ? unwrapParens(operand(condition)) : condition;
        if (e.getType().equals("declaration") || e.getType().equals("condition_declaration")) { // if (Node* n = next())
            TSNode name = declaredName(e);
            return name != null && isPointer(e, tree) ? List.of(name) : List.of();
        }
        if (e.getType().equals("identifier") || isThisField(e, tree)) {
            Declaration d = declarationOf(e, tree);
            if (d != null && isPointer(d.node(), tree)) {
                return List.of(e);
            }
        }
        return List.of();
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
        String e = expression.replaceAll("\\s+", "");
        return e.startsWith("this->") ? e.substring(6) : e;
    }

    @Override
    NameScope resolveScope(TSNode name, SyntaxTree tree) {
        if (!name.getType().equals("identifier")) {
            return null;
        }
        Declaration d = declarationOf(name, tree);
        return d == null ? NameScope.MEMBER : d.scope();
    }

    @Override
    TSNode declarationNode(TSNode expression, SyntaxTree tree) {
        if (!expression.getType().equals("identifier") && !isThisField(expression, tree)) {
            return null;
        }
        Declaration d = declarationOf(expression, tree);
        return d == null ? null : d.node();
    }

    /** Finds the declaration of an identifier, or of {@code this->field}, visible where the node is. */
    private Declaration declarationOf(TSNode node, SyntaxTree tree) {
        boolean memberOnly = !node.getType().equals("identifier");
        String n = memberOnly ? tree.text(field(node, "field")) : tree.text(node);
        int position = tree.start(node);
        for (TSNode a = parent(node); a != null; a = parent(a)) {
            String type = a.getType();
            if (type.equals("field_declaration_list")) {
                TSNode member = declarationIn(namedChildren(a), n, Integer.MAX_VALUE, tree);
                if (member != null) {
                    return new Declaration(NameScope.MEMBER, member);
                }
                continue;
            }
            if (type.equals("function_definition")) {
                TSNode function = functionDeclarator(field(a, "declarator"));
                if (!memberOnly && function != null) {
                    TSNode parameter = parameterIn(field(function, "parameters"), n, tree);
                    if (parameter != null) {
                        return new Declaration(NameScope.PARAMETER, parameter);
                    }
                }
                TSNode member = function == null ? null : outOfLineMember(function, n, tree);
                if (member != null) {
                    return new Declaration(NameScope.MEMBER, member);
                }
                continue;
            }
            if (memberOnly) {
                continue;
            }
            if (type.equals("lambda_expression")) {
                TSNode declarator = field(a, "declarator");
                TSNode parameter = declarator == null ? null : parameterIn(field(declarator, "parameters"), n, tree);
                if (parameter != null) {
                    return new Declaration(NameScope.PARAMETER, parameter);
                }
            } else if (BLOCKS.contains(type) || GLOBAL_SCOPES.contains(type)) {
                TSNode local = declarationIn(namedChildren(a), n, position, tree);
                if (local != null) {
                    return new Declaration(GLOBAL_SCOPES.contains(type) ? NameScope.MEMBER : NameScope.LOCAL, local);
                }
            } else if (type.equals("for_statement") || type.equals("condition_clause")
                    || type.equals("for_range_loop") || type.equals("catch_clause")) {
                List<TSNode> parts = new ArrayList<>();
                for (TSNode child : namedChildren(a)) {
                    parts.add(child.getType().equals("init_statement") && child.getNamedChildCount() > 0
                            ? child.getNamedChild(0) : child);
                    if (child.getType().equals("parameter_list")) {
                        parts.addAll(namedChildren(child));
                    }
                }
                TSNode local = declarationIn(parts, n, Integer.MAX_VALUE, tree);
                if (local == null && type.equals("for_range_loop") && n.equals(textOf(declaredName(a), tree))) {
                    local = a;
                }
                if (local != null) {
                    return new Declaration(NameScope.LOCAL, local);
                }
            }
        }
        return null;
    }

    /** Returns the first declaration among {@code nodes}, starting before {@code position}, that declares {@code n}. */
    private static TSNode declarationIn(List<TSNode> nodes, String n, int position, SyntaxTree tree) {
        for (TSNode node : nodes) {
            if (tree.start(node) < position && (DECLARATIONS.contains(node.getType())
                    || node.getType().equals("condition_declaration"))) {
                for (TSNode declarator : declarators(node)) {
                    if (n.equals(textOf(innermostName(declarator), tree))) {
                        return node;
                    }
                }
            }
        }
        return null;
    }

    private static TSNode parameterIn(TSNode parameters, String n, SyntaxTree tree) {
        if (parameters == null) {
            return null;
        }
        for (TSNode p : namedChildren(parameters)) {
            if (n.equals(textOf(declaredName(p), tree))) {
                return p;
            }
        }
        return null;
    }

    /** For {@code void List::clear()}, finds {@code n} among the fields of {@code List} in the same file. */
    private static TSNode outOfLineMember(TSNode function, String n, SyntaxTree tree) {
        TSNode qualified = field(function, "declarator");
        if (qualified == null || !qualified.getType().equals("qualified_identifier")) {
            return null;
        }
        TSNode scope = field(qualified, "scope");
        if (scope == null) {
            return null;
        }
        String className = tree.text(scope).replaceAll(".*::", "").replaceAll("<.*", "");
        TSNode[] found = {null};
        SyntaxTree.walk(tree.root, c -> {
            String type = c.getType();
            if (found[0] == null && (type.equals("class_specifier") || type.equals("struct_specifier"))) {
                TSNode name = field(c, "name");
                TSNode body = field(c, "body");
                if (name != null && body != null && className.equals(tree.text(name))) {
                    found[0] = declarationIn(namedChildren(body), n, Integer.MAX_VALUE, tree);
                }
            }
        });
        return found[0];
    }

    private static boolean isThisField(TSNode node, SyntaxTree tree) {
        if (!node.getType().equals("field_expression")) {
            return false;
        }
        TSNode argument = field(node, "argument");
        return argument != null && argument.getType().equals("this") && field(node, "field") != null;
    }

    /** Whether a declaration declares a pointer or a smart pointer. */
    private static boolean isPointer(TSNode declaration, SyntaxTree tree) {
        TSNode type = field(declaration, "type");
        if (type != null && SMART_POINTER.matcher(tree.code(type)).matches()) {
            return true;
        }
        boolean[] pointer = {false};
        for (TSNode declarator : declarators(declaration)) {
            SyntaxTree.walk(declarator, n -> pointer[0] |= n.getType().equals("pointer_declarator")
                    || n.getType().equals("abstract_pointer_declarator"));
        }
        return pointer[0];
    }

    private static List<TSNode> declarators(TSNode declaration) {
        List<TSNode> declarators = fieldChildren(declaration, "declarator");
        return declarators.isEmpty() ? List.of(declaration) : declarators;
    }

    /** Returns the declared identifier of a declaration, parameter or declarator. */
    private static TSNode declaredName(TSNode node) {
        TSNode declarator = field(node, "declarator");
        return innermostName(declarator == null ? node : declarator);
    }

    /** Follows nested declarators ({@code *p = nullptr}, {@code &r}) to the identifier; null for functions. */
    private static TSNode innermostName(TSNode declarator) {
        TSNode n = declarator;
        while (n != null) {
            String type = n.getType();
            if (type.equals("identifier") || type.equals("field_identifier")) {
                return n;
            }
            if (type.equals("function_declarator")) {
                return null;
            }
            TSNode inner = field(n, "declarator");
            if (inner == null) {
                for (TSNode child : namedChildren(n)) {
                    if (child.getType().endsWith("declarator") || child.getType().equals("identifier")) {
                        inner = child;
                        break;
                    }
                }
            }
            n = inner;
        }
        return null;
    }

    private static TSNode functionDeclarator(TSNode declarator) {
        TSNode n = declarator;
        while (n != null && !n.getType().equals("function_declarator")) {
            n = field(n, "declarator");
        }
        return n;
    }

    @Override
    List<String> declaredKeys(TSNode node, SyntaxTree tree) {
        if (!DECLARATIONS.contains(node.getType()) || node.getType().equals("parameter_declaration")) {
            return List.of();
        }
        List<String> keys = new ArrayList<>();
        for (TSNode declarator : fieldChildren(node, "declarator")) {
            TSNode name = innermostName(declarator);
            if (name != null) {
                keys.add(key(tree.text(name), node, tree));
            }
        }
        return keys;
    }

    private static String textOf(TSNode node, SyntaxTree tree) {
        return node == null ? null : tree.text(node);
    }
}
