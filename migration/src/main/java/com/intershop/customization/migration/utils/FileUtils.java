package com.intershop.customization.migration.utils;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Utility class for file operations.
 */
public class FileUtils
{

    public final static Charset BUILD_GRADLE_CHARSET = StandardCharsets.UTF_8;

    private FileUtils() { }

    /**
     * Lists all files in the given directory and its subdirectories.
     *
     * @param directory the directory to list files from
     * @param filter an optional filter to apply to the files
     * @param sorting an optional comparator to sort the files
     * @return a list of paths to the files
     * @throws IOException if an I/O error occurs
     */
    public static List<Path> listFiles(Path directory, Predicate<Path> filter, Comparator<Path> sorting)
                    throws IOException
    {
        try (Stream<Path> stream = Files.walk(directory))
        {
            Stream<Path> filtered = filter != null ? stream.filter(filter) : stream;
            return  sorting != null ? filtered.sorted(sorting).toList() : filtered.toList();
        }
    }

    /**
     * Reads the content of a file as a string.
     *
     * @param path the path to the file
     * @return to file content as string
     * @throws IOException if the file cannot be read
     */
    public static String readString(Path path) throws IOException
    {
        return Files.readString(path, BUILD_GRADLE_CHARSET);
    }

    /**
     * Reads all lines of a file into a list of strings.
     *
     * @param path the path to the file
     * @return to file content as list of strings
     * @throws IOException if the file cannot be read
     */
    public static List<String> readAllLines(Path path) throws IOException
    {
        return Files.readAllLines(path, BUILD_GRADLE_CHARSET);
    }

    /**
     * Writes a string to a file.
     *
     * @param path the path to the file
     * @param content the content to write
     * @throws IOException if the file cannot be written
     */
    public static void writeString(Path path, String content) throws IOException
    {
        Files.writeString(path, content, BUILD_GRADLE_CHARSET);
    }

    /**
     * Writes a list of strings to a file.
     *
     * @param path the path to the file
     * @param lines the lines to write
     * @throws IOException if the file cannot be written
     */
    public static void writeLines(Path path, List<String> lines) throws IOException
    {
        Files.write(path, lines, BUILD_GRADLE_CHARSET);
    }

    /**
     * Checks if a file contains a specific text.
     *
     * @param path the path to the file
     * @param text a string to search for
     * @return true if the file contains the string, false otherwise
     * @throws IOException if the file cannot be read
     */
    public static boolean containsText(Path path, String text) throws IOException
    {
        try (Stream<String>  streamedLines = Files.lines(path, BUILD_GRADLE_CHARSET))
        {
            return streamedLines.anyMatch(l -> l.contains(text));
        }
    }
}
