package com.aliibrahim.nullcheck.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectAnalyzerTest {

    private static final Path SAMPLES = Path.of(System.getProperty("samples.dir", "../samples"));

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }

    @Test
    void findsSupportedFilesAndSkipsBuildHiddenAndDependencyFolders(@TempDir Path dir) throws IOException {
        write(dir.resolve("src/A.java"), "class A { void f(String s) { if (s == null) { } } }");
        write(dir.resolve("src/web/index.php"), "<?php if ($x === null) { }");
        write(dir.resolve("native/list.cpp"), "void f(int* p) { if (!p) {} }");
        write(dir.resolve("README.md"), "if (x == null)");
        write(dir.resolve("build/Gen.java"), "class Gen {}");
        write(dir.resolve("vendor/lib/x.php"), "<?php");
        write(dir.resolve("node_modules/m/y.c"), "int y;");
        write(dir.resolve(".git/hooks/z.java"), "class Z {}");

        ProjectAnalysis project = new ProjectAnalyzer().analyze(dir);

        assertEquals(List.of("native/list.cpp", "src/A.java", "src/web/index.php"),
                project.files().stream().map(FileAnalysis::displayPath).toList());
        assertEquals(List.of(Language.CPP, Language.JAVA, Language.PHP),
                project.files().stream().map(FileAnalysis::language).toList());
        assertEquals(3, project.total().comparands().size());
        assertEquals(100, project.total().percentOfIfsCheckingNull());
        assertTrue(project.errors().isEmpty());
        assertEquals(dir.getFileName().toString(), project.name());
    }

    @Test
    void emptyFolder(@TempDir Path dir) throws IOException {
        ProjectAnalysis project = new ProjectAnalyzer().analyze(dir);
        assertTrue(project.files().isEmpty());
        assertEquals(0, project.total().percentOfIfsCheckingNull());
        assertTrue(Reports.toText(project).contains("Files (0)"));
    }

    @Test
    void samplesFolderReports() throws IOException {
        ProjectAnalysis project = new ProjectAnalyzer().analyze(SAMPLES);
        assertEquals(List.of("Address.java", "Customer.java", "Inventory.cpp", "Message.java", "Order.php"),
                project.files().stream().map(FileAnalysis::displayPath).toList());

        String text = Reports.toText(project);
        assertTrue(text.startsWith("Tracking null checks: samples (5 files)\n"), text);
        assertTrue(text.contains("Customer.java  (Java): 15 if statements, 19 null checks, 16 definitions"), text);
        assertTrue(text.contains("Statistics (all files)"), text);
        assertTrue(text.contains("==  Order.php  =="), text);

        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        Reports.writePdf(project, pdf);
        assertEquals("%PDF-", new String(pdf.toByteArray(), 0, 5, StandardCharsets.US_ASCII));
    }
}
