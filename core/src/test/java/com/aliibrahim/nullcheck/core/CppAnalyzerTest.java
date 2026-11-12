package com.aliibrahim.nullcheck.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CppAnalyzerTest {

    private static final Path SAMPLES = Path.of(System.getProperty("samples.dir", "../samples"));

    private final NullCheckAnalyzer analyzer = new NullCheckAnalyzer();

    private AnalysisResult cpp(String source) {
        return analyzer.analyze(source, Language.CPP);
    }

    private static List<String> expressions(AnalysisResult r) {
        return r.comparands().stream().map(NullComparand::expression).toList();
    }

    private static List<NameScope> scopes(AnalysisResult r) {
        return r.comparands().stream().map(NullComparand::scope).toList();
    }

    @Test
    void extensions() {
        for (String name : List.of("a.cpp", "a.cc", "a.cxx", "a.hpp", "a.h")) {
            assertEquals(Optional.of(Language.CPP), Language.forFileName(name), name);
        }
        assertEquals(Optional.of(Language.C), Language.forFileName("a.c"));
    }

    @Test
    void cFilesUseTheCGrammar() {
        AnalysisResult r = analyzer.analyze("""
                struct node { struct node *next; };
                int f(struct node *this, int new) {
                  struct node *class = this->next;
                  if (this == NULL || !class) { return new; }
                  return 0;
                }
                """, Language.C);
        assertEquals(List.of("this", "class"), expressions(r));
        assertEquals(List.of(NameScope.PARAMETER, NameScope.LOCAL), scopes(r));
    }

    @Test
    void definitionsBelongToTheVariableNotTheName() {
        AnalysisResult r = cpp("""
                void a() {
                  int* item = nullptr;
                  if (item == nullptr) {}
                }
                void b(int* item) {
                  item = nullptr;
                  if (item == nullptr) {}
                }
                """);
        assertEquals(List.of(2, 6), r.definitions().stream().map(Definition::line).toList());
        assertEquals(List.of("int* item = nullptr;", "item = nullptr;"),
                r.definitions().stream().map(Definition::code).toList());
    }

    @Test
    void nullptrAndNullComparisons() {
        AnalysisResult r = cpp("""
                void f(int* a, int* b, int* c) {
                  if (a == nullptr || NULL != b && c != NULL) {}
                  if (nullptr == nullptr) {}
                }
                """);
        assertEquals(List.of("a", "b", "c"), expressions(r));
        assertEquals(List.of(NameScope.PARAMETER, NameScope.PARAMETER, NameScope.PARAMETER), scopes(r));
        assertEquals(List.of(true, false), r.ifStatements().stream().map(IfStatement::checksNull).toList());
    }

    @Test
    void implicitPointerChecksNeedAPointerDeclaration() {
        AnalysisResult r = cpp("""
                #include <memory>
                void f(int* p, int n, std::unique_ptr<int> u, bool flag) {
                  if (p) {}
                  if (!p) {}
                  if (n) {}
                  if (u) {}
                  if (!flag) {}
                  if (int* q = p) {}
                }
                """);
        assertEquals(List.of("p", "p", "u", "q"), expressions(r));
        assertEquals(List.of(3, 4, 6, 8), r.comparands().stream().map(NullComparand::line).toList());
        assertEquals(NameScope.LOCAL, r.comparands().get(3).scope());
    }

    @Test
    void membersLocalsAndOutOfLineMethods() {
        AnalysisResult r = cpp("""
                int* global = nullptr;
                class List {
                  int* head_;
                  void push(int* v) {
                    int* tmp = v;
                    if (head_ == nullptr || tmp == nullptr || global == nullptr || this->head_) {}
                  }
                  void clear();
                };
                void List::clear() {
                  if (head_ != nullptr) {}
                  auto drop = [](int* gone) { if (gone == nullptr) {} };
                }
                """);
        assertEquals(List.of("head_", "tmp", "global", "this->head_", "head_", "gone"), expressions(r));
        assertEquals(java.util.Arrays.asList(NameScope.MEMBER, NameScope.LOCAL, NameScope.MEMBER, null,
                NameScope.MEMBER, NameScope.PARAMETER), scopes(r));
        assertEquals(ComparandKind.FIELD_ACCESS, r.comparands().get(3).kind());
    }

    @Test
    void commentsStringsAndPreprocessorAreIgnored() {
        AnalysisResult r = cpp("""
                // if (a == nullptr) {}
                /* if (b == nullptr) {} */
                const char* s = "if (c == nullptr) {}";
                void f(int* d) { if (d == nullptr) {} }
                """);
        assertEquals(List.of("d"), expressions(r));
    }

    @Test
    void definitionsBeforeTheFirstCheck() {
        AnalysisResult r = cpp("""
                void f() {
                  int* p = nullptr;
                  p = nullptr;
                  if (p == nullptr) {}
                  p = new int(1);
                }
                """);
        assertEquals(List.of("int* p = nullptr;", "p = nullptr;"),
                r.definitions().stream().map(Definition::code).toList());
        assertEquals("nullptr assigned to p in line 3; assign another value instead of nullptr.",
                r.definitions().get(1).advice());
    }

    @Test
    void inventorySample() throws IOException {
        AnalysisResult r = analyzer.analyze(SAMPLES.resolve("Inventory.cpp"));
        assertEquals(8, r.ifStatements().size());
        assertEquals(List.of("item", "item", "item->next", "item", "this->head_", "featured_", "featured_->next",
                "first", "gone", "current"), expressions(r));
        assertEquals(NameScope.MEMBER, r.comparands().get(5).scope(), "member of Inventory, out-of-line method");
        assertEquals("Item* head_ = nullptr;", r.definitions().get(1).code());
    }
}
