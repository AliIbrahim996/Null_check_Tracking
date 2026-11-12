package com.aliibrahim.nullcheck.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PhpAnalyzerTest {

    private static final Path SAMPLES = Path.of(System.getProperty("samples.dir", "../samples"));

    private final NullCheckAnalyzer analyzer = new NullCheckAnalyzer();

    private AnalysisResult php(String source) {
        return analyzer.analyze(source, Language.PHP);
    }

    private static List<String> expressions(AnalysisResult r) {
        return r.comparands().stream().map(NullComparand::expression).toList();
    }

    @Test
    void languageComesFromTheExtension() {
        assertEquals(Optional.of(Language.PHP), Language.forFileName("index.PHP"));
        assertEquals(Optional.of(Language.PHP), Language.forFileName("view.phtml"));
        assertEquals(Optional.of(Language.JAVA), Language.forFileName("A.java"));
        assertEquals(Optional.empty(), Language.forFileName("notes.txt"));
    }

    @Test
    void comparisonOperatorsAndCaseInsensitiveNull() {
        AnalysisResult r = php("""
                <?php
                function f($a, $b, $c, $d, $e) {
                  if ($a === null || $b !== NULL) { }
                  if ($c==null && null != $d) { }
                  elseif ($e <> Null) { }
                }
                """);
        assertEquals(List.of("$a", "$b", "$c", "$d", "$e"), expressions(r));
        assertTrue(r.comparands().stream().allMatch(c -> c.scope() == NameScope.PARAMETER));
        assertEquals(3, r.ifStatements().size());
    }

    @Test
    void isNullIssetAndWordOperators() {
        AnalysisResult r = php("""
                <?php
                $x = load();
                if (is_null($x) or !isset($y, $z['k']) and $w != null) { }
                """);
        assertEquals(List.of("$x", "$y", "$z['k']", "$w"), expressions(r));
        assertEquals(ComparandKind.FIELD_ACCESS, r.comparands().get(2).kind());
        assertEquals(NameScope.LOCAL, r.comparands().get(0).scope());
    }

    @Test
    void htmlCommentsStringsAndHeredocsAreIgnored() {
        AnalysisResult r = php("""
                <p>if ($html == null) { }</p>
                <?php
                # if ($hash == null) { }
                // if ($line == null) { }
                /* if ($block == null) { } */
                $s = "if ($str == null) { }";
                $t = <<<EOT
                  if ($heredoc == null) { }
                  EOT;
                if ($real === null) { }
                ?>
                <p>if ($after == null) { }</p>
                """);
        assertEquals(List.of("$real"), expressions(r));
        assertEquals(10, r.comparands().get(0).line());
    }

    @Test
    void closuresHaveTheirOwnParameters() {
        AnalysisResult r = php("""
                <?php
                function outer($a) {
                  $f = function ($b) use ($a) {
                    if ($b === null || $a === null) { }
                  };
                }
                """);
        assertEquals(NameScope.PARAMETER, r.comparands().get(0).scope());
        assertEquals(NameScope.LOCAL, r.comparands().get(1).scope());
    }

    @Test
    void kindsAndConstants() {
        AnalysisResult r = php("""
                <?php
                if ($o->p === null || $o?->q === null || A::$s === null || $o->m() === null || FOO === null) { }
                """);
        List<ComparandKind> kinds = r.comparands().stream().map(NullComparand::kind).toList();
        assertEquals(List.of(ComparandKind.FIELD_ACCESS, ComparandKind.FIELD_ACCESS, ComparandKind.FIELD_ACCESS,
                ComparandKind.METHOD_CALL, ComparandKind.NAME), kinds);
        assertNull(r.comparands().get(4).scope(), "a constant has no variable scope");
    }

    @Test
    void definitionsIncludeAssignmentsAndPropertyDeclarations() {
        AnalysisResult r = php("""
                <?php
                class A {
                  private ?string $name = null;
                  function f() {
                    $v = null;
                    $v ??= $this->name;
                    if ($this->name === null || $v == null) { }
                  }
                }
                """);
        List<String> codes = r.definitions().stream().map(Definition::code).toList();
        assertEquals(List.of("private ?string $name = null;", "$v = null;", "$v ??= $this->name;"), codes);
        assertEquals("null assigned to $v in line 5; assign another value instead of null.",
                r.definitions().get(1).advice());
    }

    @Test
    void orderSample() throws IOException {
        AnalysisResult r = analyzer.analyze(SAMPLES.resolve("Order.php"));
        assertEquals(8, r.ifStatements().size());
        assertEquals(List.of("$this->customer", "$name", "$this->customer", "$address", "$fallback", "$address",
                "$address->city", "$first", "$line['price']", "$this->findDiscount($total)"), expressions(r));
        assertEquals(NameScope.PARAMETER, r.comparands().get(4).scope());
        assertEquals("private ?Customer $customer = null;", r.definitions().get(0).code());
        assertEquals(8, r.definitions().get(0).line());
    }
}
