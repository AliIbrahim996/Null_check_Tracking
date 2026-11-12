package com.aliibrahim.nullcheck.core;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;
import org.treesitter.TSNode;
import org.treesitter.TSParser;
import org.treesitter.TSTree;

/**
 * A tree-sitter syntax tree of one source text, with helpers that turn tree-sitter's UTF-8 byte offsets into
 * indices of the Java string and nodes into text and line numbers.
 *
 * <p>tree-sitter recovers from syntax errors, so a tree is produced for any input; broken parts show up as
 * {@code ERROR} nodes and are simply not recognised.
 */
final class SyntaxTree implements AutoCloseable {

    /** The analysed text, with {@code \n} line endings. */
    final String source;
    final TSNode root;

    private final TSParser parser;
    private final TSTree tree;
    private final int prefixLength;
    /** Maps a UTF-8 byte offset of the parsed text to a char index of the parsed text. */
    private final int[] charIndex;

    /**
     * Parses {@code source}. {@code prefix}, if not empty, is put in front of the text on the same line, so
     * line numbers are unchanged; offsets and texts returned by this class never include it.
     */
    SyntaxTree(String source, String prefix, LanguageRules rules) {
        this.source = source;
        this.prefixLength = prefix.length();
        String parsed = prefix + source;
        byte[] utf8 = parsed.getBytes(StandardCharsets.UTF_8);
        this.charIndex = new int[utf8.length + 1];
        int ch = 0;
        for (int b = 0; b < utf8.length; ) {
            int lead = utf8[b] & 0xFF;
            int length = lead < 0x80 ? 1 : lead < 0xE0 ? 2 : lead < 0xF0 ? 3 : 4;
            for (int k = 0; k < length && b + k < utf8.length; k++) {
                charIndex[b + k] = ch;
            }
            b += length;
            ch += length == 4 ? 2 : 1; // a 4-byte character is a surrogate pair in Java
        }
        charIndex[utf8.length] = parsed.length();

        this.parser = new TSParser();
        parser.setLanguage(rules.grammar());
        this.tree = parser.parseString(null, parsed);
        this.root = tree.getRootNode();
    }

    @Override
    public void close() {
        tree.close();
        parser.close();
    }

    /** Returns the string index where a node starts. */
    int start(TSNode node) {
        return Math.max(0, charIndex[node.getStartByte()] - prefixLength);
    }

    /** Returns the string index just after a node. */
    int end(TSNode node) {
        return Math.max(0, charIndex[node.getEndByte()] - prefixLength);
    }

    /** Returns the source text of a node. */
    String text(TSNode node) {
        return source.substring(start(node), end(node));
    }

    /** Returns the source text of a node with comments removed and whitespace collapsed. */
    String code(TSNode node) {
        int start = start(node);
        char[] chars = source.substring(start, end(node)).toCharArray();
        walk(node, n -> {
            if (n.getType().endsWith("comment")) {
                for (int i = start(n) - start; i < end(n) - start; i++) {
                    chars[i] = ' ';
                }
            }
        });
        return collapse(new String(chars));
    }

    /** Returns the 1-based line on which a node starts. */
    static int line(TSNode node) {
        return node.getStartPoint().getRow() + 1;
    }

    /** Returns the child in a field, or {@code null} if there is none. */
    static TSNode field(TSNode node, String name) {
        TSNode child = node.getChildByFieldName(name);
        return child == null || child.isNull() ? null : child;
    }

    /** Returns the named children of a node, comments included. */
    static List<TSNode> namedChildren(TSNode node) {
        int count = node.getNamedChildCount();
        List<TSNode> children = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            children.add(node.getNamedChild(i));
        }
        return children;
    }

    /** Returns the children of a node that are in the given field. */
    static List<TSNode> fieldChildren(TSNode node, String field) {
        List<TSNode> children = new ArrayList<>();
        for (int i = 0; i < node.getChildCount(); i++) {
            if (field.equals(node.getFieldNameForChild(i))) {
                children.add(node.getChild(i));
            }
        }
        return children;
    }

    /** Returns a node's parent, or {@code null} for the root. */
    static TSNode parent(TSNode node) {
        TSNode parent = node.getParent();
        return parent == null || parent.isNull() ? null : parent;
    }

    /** Visits a node and all its named descendants in source order. */
    static void walk(TSNode node, Consumer<TSNode> visitor) {
        Deque<TSNode> stack = new ArrayDeque<>();
        stack.push(node);
        while (!stack.isEmpty()) {
            TSNode n = stack.pop();
            visitor.accept(n);
            for (int i = n.getNamedChildCount() - 1; i >= 0; i--) {
                stack.push(n.getNamedChild(i));
            }
        }
    }

    /** Whether a node or one of its descendants has one of the given types. */
    static boolean contains(TSNode node, java.util.Set<String> types) {
        boolean[] found = {false};
        walk(node, n -> found[0] |= types.contains(n.getType()));
        return found[0];
    }

    static String collapse(String s) {
        return s.replaceAll("\\s+", " ").trim();
    }
}
