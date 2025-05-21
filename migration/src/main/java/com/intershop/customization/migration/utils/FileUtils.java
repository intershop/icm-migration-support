package com.intershop.customization.migration.utils;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 *
 */
public class FileUtils
{

    public final static Charset BUILD_GRADLE_CHARSET = StandardCharsets.UTF_8;

    private FileUtils() { }

    /**
     * Lists all files in the given directory and its subdirectories.
     * @param directory the directory to list files from
     * @param filter an optional filter to apply to the files
     * @param sorting an optional comparator to sort the files
     * @return a list of paths to the files
     * @throws IOException if an I/O error occurs
     */
    public static List<Path> listFiles(Path directory, Optional<Predicate<Path>> filter, Optional<Comparator<Path>> sorting)
                    throws IOException
    {
        try (Stream<Path> stream = Files.walk(directory)) {
            Stream<Path> filtered = filter.map(stream::filter).orElse(stream);
            return sorting.map(filtered::sorted)
                            .orElse(filtered)
                            .toList();
        }
    }

    /**
     * Reads the content of a file as a string.
     * @param path the path to the file
     * @return to file content as string
     * @throws IOException
     */
    public static String readString(Path path) throws IOException
    {
        return Files.readString(path, BUILD_GRADLE_CHARSET);
    }

    /**
     * Reads all lines of a file into a list of strings.
     * @param path the path to the file
     * @return to file content as list of strings
     * @throws IOException
     */
    public static List<String> readAllLines(Path path) throws IOException
    {
        return Files.readAllLines(path, BUILD_GRADLE_CHARSET);
    }

    /**
     * Reads all lines of a file into a list of strings.
     * @param path the path to the file
     * @throws IOException
     */
    public static void writeString(Path path, String content) throws IOException
    {
        Files.writeString(path, content, BUILD_GRADLE_CHARSET);
    }

    /**
     * Writes a list of strings to a file.
     * @param path the path to the file
     * @param lines the lines to write
     * @throws IOException
     */
    public static void writeLines(Path path, List<String> lines) throws IOException
    {
        Files.write(path, lines, BUILD_GRADLE_CHARSET);
    }

    /**
     * Reads all lines of a file into a stream of strings.
     * @param path the path to the file
     * @return a stream of strings representing the lines of the file
     * @throws IOException
     */
    public static Stream<String> lines(Path path) throws IOException
    {
        return Files.lines(path, BUILD_GRADLE_CHARSET);
    }

    /**
     *
     * @param path the path to the file
     * @param text a string to search for
     * @return true if the file contains the string, false otherwise
     * @throws IOException
     */
    public static boolean containsText(Path path, String text) throws IOException
    {
        try (Stream<String>  streamedLines = Files.lines(path, BUILD_GRADLE_CHARSET))
        {
            return streamedLines.anyMatch(l -> l.contains(text));
        }
    }
}
