package com.aliibrahim.nullcheck.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MainTest {

    private static final Path SAMPLES = Path.of(System.getProperty("samples.dir", "../samples"));

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private int run(String... args) {
        return Main.run(List.of(args), new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    @Test
    void invalidPathIsAUsageError() {
        assertEquals(Main.USAGE_ERROR, run("bad\0name.java"));
        assertTrue(err.toString(StandardCharsets.UTF_8).startsWith("Invalid path"));
    }

    @Test
    void analysesAFolder(@TempDir Path dir) throws Exception {
        Path pdf = dir.resolve("out/samples.pdf");
        assertEquals(Main.OK, run(SAMPLES.toString(), pdf.toString()));
        String text = out.toString(StandardCharsets.UTF_8);
        assertTrue(text.startsWith("Tracking null checks: samples (5 files)"), text);
        assertTrue(text.contains("Inventory.cpp  (C++)"), text);
        assertTrue(Files.size(pdf) > 500);
    }

    @Test
    void analysesCppFiles() {
        assertEquals(Main.OK, run(SAMPLES.resolve("Inventory.cpp").toString()));
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("first  (local name, line 53)"));
    }

    @Test
    void analysesPhpFiles() {
        assertEquals(Main.OK, run(SAMPLES.resolve("Order.php").toString()));
        String text = out.toString(StandardCharsets.UTF_8);
        assertTrue(text.startsWith("Tracking null checks: Order.php"), text);
        assertTrue(text.contains("$fallback  (parameter name, line 36)"), text);
    }

    @Test
    void printsTextReport() {
        assertEquals(Main.OK, run(SAMPLES.resolve("Customer.java").toString()));
        String text = out.toString(StandardCharsets.UTF_8);
        assertTrue(text.startsWith("Tracking null checks: Customer.java"), text);
        assertTrue(text.contains("city2  (local name, line 98)"), text);
    }

    @Test
    void writesPdfIntoNewFolder(@TempDir Path dir) throws Exception {
        Path pdf = dir.resolve("reports/customer.pdf");
        assertEquals(Main.OK, run(SAMPLES.resolve("Customer.java").toString(), pdf.toString()));
        assertTrue(Files.size(pdf) > 500);
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("PDF report written to"));
    }

    @Test
    void wrongArgumentsShowUsage() {
        assertEquals(Main.USAGE_ERROR, run());
        assertTrue(err.toString(StandardCharsets.UTF_8).startsWith("Usage:"));
    }

    @Test
    void helpGoesToStandardOutput() {
        assertEquals(Main.OK, run("--help"));
        assertTrue(out.toString(StandardCharsets.UTF_8).startsWith("Usage:"));
    }

    @Test
    void missingFileIsAnError() {
        assertEquals(Main.IO_ERROR, run("does-not-exist.java"));
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("No such file"));
    }
}
