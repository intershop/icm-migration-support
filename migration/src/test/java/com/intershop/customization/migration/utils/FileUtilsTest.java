package com.intershop.customization.migration.utils;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FileUtilsTest
{
    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException
    {
        tempDir = Files.createTempDirectory("fileutils-test");
    }

    @AfterEach
    void tearDown() throws IOException
    {
        try (Stream<Path> streams = Files.walk(tempDir))
        {
            streams.sorted(Comparator.reverseOrder())
                   .forEach(p -> p.toFile().delete());
        }

    }

    @Test
    void testWriteAndReadString() throws IOException
    {
        Path file = tempDir.resolve("test.txt");
        String content = "Hello, world!";
        FileUtils.writeString(file, content);
        assertEquals(content, FileUtils.readString(file));
    }

    @Test
    void testWriteAndReadLines() throws IOException
    {
        Path file = tempDir.resolve("lines.txt");
        List<String> lines = List.of("one", "two", "three");
        FileUtils.writeLines(file, lines);
        assertEquals(lines, FileUtils.readAllLines(file));
    }

    @Test
    void testContainsText() throws IOException
    {
        Path file = tempDir.resolve("contains.txt");
        List<String> lines = List.of("foo", "bar", "baz");
        FileUtils.writeLines(file, lines);
        assertTrue(FileUtils.containsText(file, "bar"));
        assertFalse(FileUtils.containsText(file, "qux"));
    }

    @Test
    void testListFiles() throws IOException
    {
        Path subDir = tempDir.resolve("sub");
        Files.createDirectory(subDir);
        Path file1 = tempDir.resolve("a.txt");
        Path file2 = subDir.resolve("b.txt");
        Files.createFile(file1);
        Files.createFile(file2);

        List<Path> files = FileUtils.listFiles(tempDir, Files::isRegularFile, null);
        assertTrue(files.contains(file1));
        assertTrue(files.contains(file2));
        assertEquals(2, files.size());
    }

    @Test
    void testListFilesWithFilterAndSort() throws IOException
    {
        Path file1 = tempDir.resolve("a.txt");
        Path file2 = tempDir.resolve("b.log");
        Files.createFile(file1);
        Files.createFile(file2);

        Predicate<Path> txtFilter = p -> p.toString().endsWith(".txt");
        Comparator<Path> byName = Comparator.comparing(p -> p.getFileName().toString());

        List<Path> files = FileUtils.listFiles(tempDir, txtFilter, byName);
        assertEquals(List.of(file1), files);
    }

    @Test
    void testListTopLevelFiles() throws IOException
    {
        Path subDir = tempDir.resolve("sub");
        Files.createDirectory(subDir);
        Path file1 = tempDir.resolve("a.txt");
        Path file2 = subDir.resolve("b.txt");
        Files.createFile(file1);
        Files.createFile(file2);

        List<Path> files = FileUtils.listTopLevelFiles(tempDir, Files::isRegularFile, null);
        assertTrue(files.contains(file1));
        assertFalse(files.contains(file2));
        assertEquals(1, files.size());
    }

    @Test
    void testListTopLevelFilesWithFilterAndSort() throws IOException
    {
        Path file1 = tempDir.resolve("a.txt");
        Path file2 = tempDir.resolve("b.log");
        Files.createFile(file1);
        Files.createFile(file2);

        Predicate<Path> txtFilter = p -> p.toString().endsWith(".txt");
        Comparator<Path> byName = Comparator.comparing(p -> p.getFileName().toString());

        List<Path> files = FileUtils.listTopLevelFiles(tempDir, txtFilter, byName);
        assertEquals(List.of(file1), files);
    }
}