package org.trypticon.luceneupgrader.cli;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.trypticon.luceneupgrader.TestIndices;
import org.trypticon.luceneupgrader.Utils;

import static org.junit.Assert.assertEquals;

public class MainTest {
    private Path temp;
    private ByteArrayOutputStream rawOut;
    private ByteArrayOutputStream rawErr;
    private PrintStream out;
    private PrintStream err;
    private int result;

    @Before
    public void setUp() throws Exception {
        temp = Files.createTempDirectory("test");
        rawOut = new ByteArrayOutputStream();
        rawErr = new ByteArrayOutputStream();
        out = new PrintStream(rawOut);
        err = new PrintStream(rawErr);
        TestIndices.explodeZip("4.0.0", "nonempty", temp);
    }

    @After
    public void tearDown() throws Exception {
        Utils.recursiveDeleteIfExists(temp);
    }

    @Test
    public void testUnknown() {
        run("pickle");
        assertResult(1);
        assertOutput();
        assertError("Unknown command: pickle",
                "Available commands:",
                "  help",
                "  info",
                "  upgrade");
    }

    @Test
    public void testHelp() {
        run("help", "info");
        assertResult(0);
        assertOutput();
        assertError("luceneupgrader info - Gives info about a text index",
                "usage: luceneupgrader info <index dir>");
    }

    @Test
    public void testHelp_Unknown() {
        run("help", "pickle");
        assertResult(1);
        assertOutput();
        assertError("Unknown command: pickle",
                "Available commands:",
                "  help",
                "  info",
                "  upgrade");
    }

    @Test
    public void testInfo() {
        run("info", temp.toString());
        assertResult(0);
        assertOutput("Lucene index version: 4");
        assertError();
    }

    @Test
    public void testInfo_InvalidPath() {
        Path invalid = temp.resolveSibling("invalid");
        run("info", invalid.toString());
        assertResult(1);
        assertOutput();
        assertError("Error getting info for Lucene index at: " + invalid,
                "java.nio.file.NoSuchFileException: " + invalid.resolve("segments"));
    }

    @Test
    public void testUpgrade() {
        run("upgrade", temp.toString(), "6");
        assertResult(0);
        assertOutput("Upgrading Lucene index at: " + temp + " to version 6...",
                "Index upgraded successfully.");
        assertError();
    }

    @Test
    public void testUpgrade_InvalidPath() {
        Path invalid = temp.resolveSibling("invalid");
        run("upgrade", invalid.toString(), "6");
        assertResult(1);
        assertOutput("Upgrading Lucene index at: " + invalid + " to version 6...");
        assertError("Error upgrading Lucene index at: " + invalid,
                "java.nio.file.NoSuchFileException: " + invalid.resolve("segments"));
    }

    @Test
    public void testUpgrade_InvalidNumber() {
        run("upgrade", temp.toString(), "X");
        assertResult(1);
        assertOutput();
        assertError("Not a number: X");
    }

    @Test
    public void testUpgrade_InvalidVersion() {
        run("upgrade", temp.toString(), "3939");
        assertResult(1);
        assertOutput();
        assertError("Not a known Lucene version: 3939");
    }

    private void run(String... args) {
        result = new Main().run(List.of(args), out, err);
    }

    private void assertResult(int expected) {
        assertEquals(expected, result);
    }

    private void assertOutput(String... expectedLines) {
        String expected = String.join(System.lineSeparator(), expectedLines);
        assertEquals(expected, rawOut.toString(StandardCharsets.UTF_8).trim());
    }

    private void assertError(String... expectedLines) {
        String expected = String.join(System.lineSeparator(), expectedLines);
        assertEquals(expected, rawErr.toString(StandardCharsets.UTF_8).trim());
    }
}
