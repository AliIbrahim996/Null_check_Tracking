package com.aliibrahim.nullcheck.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NullCheckAnalyzerTest {

    private static final Path SAMPLES = Path.of(System.getProperty("samples.dir", "../samples"));

    private final NullCheckAnalyzer analyzer = new NullCheckAnalyzer();

    private static List<String> expressions(AnalysisResult r) {
        return r.comparands().stream().map(NullComparand::expression).toList();
    }

    private static Map<String, NullComparand> byExpression(AnalysisResult r) {
        return r.comparands().stream()
                .collect(Collectors.toMap(NullComparand::expression, Function.identity(), (a, b) -> a));
    }

    // ------------------------------------------------------------ crashes in the original version

    @Test
    void oneLineIfWithoutBraceDoesNotCrash() {
        AnalysisResult r = analyzer.analyze("""
                class A {
                  void f(String s) {
                    if (s == null) return;
                  }
                }
                """);
        assertEquals(List.of("s"), expressions(r));
        assertEquals(3, r.comparands().get(0).line());
    }

    @Test
    void braceOnNextLineDoesNotCrash() {
        AnalysisResult r = analyzer.analyze("class A {\n  void f(String s)\n  {\n    if (s != null)\n    {\n    }\n  }\n}\n");
        assertEquals(List.of("s"), expressions(r));
        assertEquals(NameScope.PARAMETER, r.comparands().get(0).scope());
    }

    @Test
    void classWithoutConstructorDoesNotCrash() {
        AnalysisResult r = analyzer.analyze("""
                class B {
                  void f(String s) {
                    if (s == null) {
                      return;
                    }
                  }
                }
                """);
        assertEquals(List.of("s"), expressions(r));
    }

    @Test
    void ifInsideCommentsAndStringsIsIgnored() {
        AnalysisResult r = analyzer.analyze("""
                class C {
                  // if (a == null) {
                  /* if (b == null) { */
                  String s = "if (c == null) {";
                  void f(Object d) { if (d == null) { } }
                }
                """);
        assertEquals(List.of("d"), expressions(r));
        assertEquals(1, r.ifStatements().size());
    }

    @Test
    void comparandWithoutDefinitionBeforeItDoesNotCrash() {
        AnalysisResult r = analyzer.analyze("class D { void f() { if (inherited == null) { } } }");
        assertEquals(List.of("inherited"), expressions(r));
        assertTrue(r.definitions().isEmpty());
    }

    @Test
    void emptySourceGivesZeroPercentagesNotNaN() {
        AnalysisResult r = analyzer.analyze("class E { }");
        assertEquals(0, r.percentOfIfsCheckingNull());
        assertEquals(0, r.percentOfComparands(NameScope.MEMBER));
        assertEquals(0, r.percentOfComparands(ComparandKind.METHOD_CALL));
    }

    @Test
    void analyserRunsOnItsOwnSource() throws IOException {
        Path self = Path.of("src/main/java/com/aliibrahim/nullcheck/core/NullCheckAnalyzer.java");
        AnalysisResult r = analyzer.analyze(self);
        assertFalse(r.ifStatements().isEmpty());
    }

    // ------------------------------------------------------------ condition parsing

    @Test
    void recognisesAllFourFormsWithAndWithoutSpaces() {
        AnalysisResult r = analyzer.analyze("""
                class F { void f(Object a, Object b, Object c, Object d) {
                  if (a==null || null!=b || c != null || null == d) { }
                } }
                """);
        assertEquals(List.of("a", "b", "c", "d"), expressions(r));
    }

    @Test
    void splitsNestedAndNegatedConditions() {
        AnalysisResult r = analyzer.analyze("""
                class G { void f(Object a, Object b, H c) {
                  if (a != null && (b == null || c.d() != null) && !(a == null)) { }
                } }
                """);
        assertEquals(List.of("a", "b", "c.d()", "a"), expressions(r));
        assertEquals(ComparandKind.METHOD_CALL, r.comparands().get(2).kind());
    }

    @Test
    void operatorsInsideStringLiteralsDoNotSplitConditions() {
        AnalysisResult r = analyzer.analyze("""
                class H { void f(java.util.Map<String, String> m) {
                  if (m.get("a && b") == null) { }
                } }
                """);
        assertEquals(List.of("m.get(\"a && b\")"), expressions(r));
    }

    @Test
    void conditionSpanningLinesIsRead() {
        AnalysisResult r = analyzer.analyze("""
                class I { void f(Object a, Object b) {
                  if (a != null
                      && b != null) {
                  }
                } }
                """);
        assertEquals(List.of("a", "b"), expressions(r));
        assertEquals("a != null && b != null", r.ifStatements().get(0).condition());
    }

    @Test
    void ifWithoutNullCheckIsListedButNotCounted() {
        AnalysisResult r = analyzer.analyze("class J { void f(boolean b, Object o) { if (b) { } if (o == null) { } } }");
        assertEquals(2, r.ifStatements().size());
        assertFalse(r.ifStatements().get(0).checksNull());
        assertTrue(r.ifStatements().get(1).checksNull());
        assertEquals(50, r.percentOfIfsCheckingNull());
    }

    @Test
    void identicalLinesGetTheirOwnLineNumbers() {
        AnalysisResult r = analyzer.analyze("""
                class K { void f(Object o) {
                  if (o == null) { }
                  if (o == null) { }
                } }
                """);
        assertEquals(List.of(2, 3), r.comparands().stream().map(NullComparand::line).toList());
    }

    @Test
    void castIsNotAMethodCall() {
        AnalysisResult r = analyzer.analyze("class L { void f(Object o) { if (((String) o) == null) { } } }");
        assertEquals(ComparandKind.NAME, r.comparands().get(0).kind());
    }

    // ------------------------------------------------------------ samples

    @Test
    void customerSampleScopesAndKinds() throws IOException {
        AnalysisResult r = analyzer.analyze(SAMPLES.resolve("Customer.java"));
        Map<String, NullComparand> c = byExpression(r);

        assertEquals(NameScope.MEMBER, c.get("name").scope());
        assertEquals(NameScope.MEMBER, c.get("customers").scope());
        assertEquals(NameScope.PARAMETER, c.get("c").scope());
        assertEquals(NameScope.PARAMETER, c.get("newCity").scope());
        assertEquals(NameScope.LOCAL, c.get("cName").scope());
        // Both were misreported by the original version.
        assertEquals(NameScope.LOCAL, c.get("city2").scope());
        assertEquals(ComparandKind.METHOD_CALL, c.get("customer2.getCustomerbyName(cName)").kind());
        assertNull(c.get("customer2.getCustomerbyName(cName)").scope());
        assertEquals(ComparandKind.FIELD_ACCESS, c.get("customer2.adress").kind());

        assertEquals(19, r.comparands().size());
        assertEquals(15, r.ifStatements().size());
        assertEquals(80, r.percentOfIfsCheckingNull());
    }

    @Test
    void customerSampleDefinitionsUseOneBasedLines() throws IOException {
        AnalysisResult r = analyzer.analyze(SAMPLES.resolve("Customer.java"));
        List<Definition> name = r.definitions().stream().filter(d -> d.comparand().equals("name")).toList();
        assertEquals(List.of(8, 13, 19), name.stream().map(Definition::line).toList());
        assertEquals("No value assigned to name in line 8; assign a value.", name.get(0).advice());
        assertEquals("null assigned to name in line 13; assign another value instead of null.",
                name.get(1).advice());
    }

    @Test
    void messageSample() throws IOException {
        AnalysisResult r = analyzer.analyze(SAMPLES.resolve("Message.java"));
        Map<String, NullComparand> c = byExpression(r);
        assertEquals(NameScope.LOCAL, c.get("msg3").scope());
        assertEquals(NameScope.PARAMETER, c.get("msg2").scope());
        assertEquals(NameScope.MEMBER, c.get("mSg").scope());
        assertEquals(ComparandKind.FIELD_ACCESS, c.get("aMessage.Msg").kind());
        assertEquals(ComparandKind.METHOD_CALL, c.get("aMessage.Join(\"\")").kind());
    }

    // ------------------------------------------------------------ code review regressions

    @Test
    void castComparandResolvesTheCastName() {
        AnalysisResult r = analyzer.analyze("""
                class A {
                  void f(Object o) {
                    o = null;
                    if (((String) o) == null) { }
                  }
                }
                """);
        NullComparand c = r.comparands().get(0);
        assertEquals("(String) o", c.expression());
        assertEquals(ComparandKind.NAME, c.kind());
        assertEquals(NameScope.PARAMETER, c.scope());
        assertEquals(List.of("o = null;"), r.definitions().stream().map(Definition::code).toList());
        assertEquals("o", r.definitions().get(0).comparand());
    }

    @Test
    void anonymousClassMethodsHaveTheirOwnParameters() {
        AnalysisResult r = analyzer.analyze("""
                class A {
                  void outer(String x) {
                    Object o = new Object() {
                      void inner(String y) {
                        if (y == null) { }
                      }
                    };
                  }
                }
                """);
        assertEquals(NameScope.PARAMETER, byExpression(r).get("y").scope());
    }

    @Test
    void definitionUsesTheAcceptedDeclaration() {
        AnalysisResult r = analyzer.analyze("""
                class A {
                  void f() {
                    if (true) return s; String s;
                    if (s == null) { }
                  }
                }
                """);
        assertEquals(List.of("String s;"), r.definitions().stream().map(Definition::code).toList());
    }

    @Test
    void adviceIgnoresSemicolonsInLiterals() {
        Definition d = new Definition("s", "s = \"a;b\" + ';';", 3);
        assertEquals("\"a;b\" + ';' is a reference value assigned to s in line 3; check the assigned value.",
                d.advice());
        assertEquals("null assigned to get(\"x=y\").s in line 4; assign another value instead of null.",
                new Definition("get(\"x=y\").s", "get(\"x=y\").s = null;", 4).advice());
    }

    // ------------------------------------------------------------ syntax-tree analysis

    @Test
    void objectsIsNullAndNonNull() {
        AnalysisResult r = analyzer.analyze("""
                import java.util.Objects;
                class A {
                  void f(String a, String b) {
                    if (Objects.isNull(a) || !java.util.Objects.nonNull(b)) { }
                    if (Other.isNull(a)) { }
                  }
                }
                """);
        assertEquals(List.of("a", "b"), expressions(r));
        assertFalse(r.ifStatements().get(1).checksNull());
    }

    @Test
    void lambdaAndLoopVariables() {
        AnalysisResult r = analyzer.analyze("""
                import java.util.List;
                class A {
                  void f(List<String> items) {
                    items.forEach(item -> { if (item == null) { } });
                    for (String s : items) { if (s == null) { } }
                    try (var in = open()) { if (in == null) { } } catch (Exception e) { if (e == null) { } }
                  }
                }
                """);
        assertEquals(List.of(NameScope.PARAMETER, NameScope.LOCAL, NameScope.LOCAL, NameScope.LOCAL),
                r.comparands().stream().map(NullComparand::scope).toList());
    }

    @Test
    void patternVariablesAndSwitchGroupLocals() {
        AnalysisResult r = analyzer.analyze("""
                class A {
                  void f(Object o, int k) {
                    if (o instanceof String s && s == null) { }
                    if (o instanceof Point(int x, String y) && y == null) { }
                    switch (k) {
                      case 1: String a = null;
                      case 2: if (a == null) { }
                    }
                    switch (o) { case String t -> { if (t == null) { } } default -> { } }
                  }
                }
                """);
        assertEquals(List.of("s", "y", "a", "t"), expressions(r));
        assertTrue(r.comparands().stream().allMatch(c -> c.scope() == NameScope.LOCAL), r.comparands().toString());
    }

    @Test
    void staticallyImportedObjectsChecks() {
        AnalysisResult r = analyzer.analyze("""
                import static java.util.Objects.isNull;
                import static java.util.Objects.*;
                class A { void f(String a, String b) { if (isNull(a) || nonNull(b)) { } } }
                """);
        assertEquals(List.of("a", "b"), expressions(r));
        AnalysisResult notImported = analyzer.analyze("class A { void f(String a) { if (isNull(a)) { } } }");
        assertTrue(notImported.comparands().isEmpty());
    }

    @Test
    void definitionsOfSameNamedVariablesStayApart() {
        AnalysisResult r = analyzer.analyze("""
                class A {
                  String name;
                  void f() {
                    String name = load();
                    if (name == null) { }
                  }
                  void g() {
                    if (this.name == null) { }
                  }
                }
                """);
        List<String> codes = r.definitions().stream().map(d -> d.line() + ": " + d.code()).toList();
        assertEquals(List.of("4: String name = load();", "2: String name;"), codes);
    }

    @Test
    void undeclaredNamesAreInheritedMembers() {
        AnalysisResult r = analyzer.analyze("""
                class B extends A {
                  void f() { if (inheritedField == null) { } }
                }
                """);
        assertEquals(NameScope.MEMBER, r.comparands().get(0).scope());
    }

    @Test
    void readsNonUtf8Files(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("Latin.java");
        Files.write(file, "class M { /* café */ void f(Object o) { if (o == null) { } } }"
                .getBytes(StandardCharsets.ISO_8859_1));
        assertEquals(List.of("o"), expressions(analyzer.analyze(file)));
    }
}
