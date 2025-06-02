package com.intershop.customization.migration.parser;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parser for the `dbinit.properties` file. This class processes the file
 * and returns a list of entries representing the lines of the file.
 * The class is instantiable and requires a `Path` object for initialization.
 */
public class DBInitPropertiesParser
{
    private final Logger LOGGER = LoggerFactory.getLogger(getClass());

    private final List<LineEntry> parsedLines;
    private final Map<GroupType, PropertyEntry> firstEntries;
    private final Map<GroupType, PropertyEntry> highestIdEntries;

    private final static Pattern PROPERTY_PATTERN = Pattern.compile("^\\s*([^#\\s][^=]*)=(.*)$");
    private final static Pattern ID_PATTERN = Pattern.compile("(\\d+)(?:\\..*)?$");

    /**
     * Constructor that accepts the path to the `dbinit.properties` file.
     *
     * @param lines the preread lines of the `dbinit.properties` file
     */
    public DBInitPropertiesParser(List<String> lines)
    {
        firstEntries = new EnumMap<>(GroupType.class);
        highestIdEntries = new EnumMap<>(GroupType.class);
        parsedLines = parse(lines);
    }

    public List<LineEntry> getParsedLines()
    {
        return parsedLines;
    }

    /**
     * LineEntry is a sealed interface that represents a line in the dbinit.properties file.
     * It can be a blank line, a comment line, or a property entry.
     */
    public sealed interface LineEntry permits BlankLine, CommentLine, PropertyEntry
    {
        String LINE_SEP = System.lineSeparator();

        int lineNumber();

        String text();
    }

    /**
     * Represents a blank line.
     */
    public record BlankLine(int lineNumber) implements LineEntry
    {
        @Override
        public String text()
        {
            return "";
        }
    }

    /**
     * Represents a comment line.
     *
     * @param lineNumber the line number where the comment is found
     * @param text the comment itself
     */
    public record CommentLine(int lineNumber, String text) implements LineEntry
    {
    }

    /**
     * Represents a property entry in the `dbinit.properties` file.
     *
     * @param lineNumber the line number where the property is found
     * @param group the group type of the property (PRE, MAIN, POST, UNKNOWN)
     * @param id the ID of the property (in case of compound IDs, only the first part is used)
     * @param key the key of the property
     * @param value the value of the property
     * @param comments list of comments associated with the property
     */
    public record PropertyEntry(int lineNumber, GroupType group, Integer id, String key, String value, List<String> comments)
                    implements LineEntry
    {
        @Override
        public String text()
        {
            return comments.stream().map(String::trim).reduce("", (a, b) -> a + LINE_SEP + b)
                            + LINE_SEP + key + "=" + value;
        }
    }

    /**
     * Represents a parsed property from the dbinit.properties file.
     * This record holds the group type, numeric ID, key, and value of a property line.
     *
     * @param group The group type of the property (PRE, MAIN, POST, UNKNOWN)
     * @param id    The numeric ID extracted from the property key
     * @param key   The full property key as found in the file
     * @param value The property value
     */
    public record ParsedProperty(GroupType group, int id, String key, String value) {}

    /**
     * Helper function to detect the group type of given key based on its prefix.
     *
     * @param key the key to check
     * @return the group type (PRE, MAIN, POST, UNKNOWN)
     */
    private static GroupType detectGroup(String key)
    {
        return GroupType.valueByPrefix(key);
    }

    /**
     * Parses the `dbinit.properties` file and returns a list of LineEntry objects.
     *
     * @return the list of LineEntry objects
     */
    protected List<LineEntry> parse(List<String> lines)
    {
        List<LineEntry> result = new ArrayList<>();
        List<String> commentBuffer = new ArrayList<>();
        int lineNumber = 0;

        for (String line : lines)
        {
            lineNumber++;
            String trimmed = line.trim();

            if (trimmed.isEmpty())
            {
                // Blank line: comments before it are "independent"
                for (String c : commentBuffer)
                {
                    result.add(new CommentLine(lineNumber, c));
                }
                commentBuffer.clear();
                result.add(new BlankLine(lineNumber));
            }
            else if (trimmed.startsWith("#") || trimmed.startsWith(";"))
            {
                // Comment: collect it
                commentBuffer.add(line);
            }
            else
            {

                Optional<ParsedProperty> parsedPropertyOpt = extractParsedLine(line);
                if (parsedPropertyOpt.isPresent())
                {
                    ParsedProperty parsedLine = parsedPropertyOpt.get();
                    GroupType group = parsedLine.group();
                    int id = parsedLine.id();
                    String key = parsedLine.key();
                    String value = parsedLine.value();

                    PropertyEntry entry = new PropertyEntry(lineNumber, group, id, key, value, new ArrayList<>(commentBuffer));
                    result.add(entry);
                    commentBuffer.clear();

                    // Store the first entry for each group
                    firstEntries.putIfAbsent(group, entry);

                    // Update highestIdEntries
                    highestIdEntries.merge(group, entry, (existing, newEntry) -> {
                        Matcher existingMatcher = ID_PATTERN.matcher(existing.key());
                        int existingId = existingMatcher.find() ? Integer.parseInt(existingMatcher.group(1)) : -1;
                        return id > existingId ? newEntry : existing;
                    });
                }
                else
                {
                    // Fallback: handle unrecognized lines as comments
                    result.add(new CommentLine(lineNumber, line));
                }
            }
        }
        // Add remaining comments at the end
        for (String c : commentBuffer)
        {
            result.add(new CommentLine(lineNumber, c));
        }
        return result;
    }

    protected Optional<ParsedProperty> extractParsedLine(String line)
    {
        Matcher m = PROPERTY_PATTERN.matcher(line);
        if (m.find())
        {
            String key = m.group(1).trim();
            String value = m.group(2).trim();
            GroupType group = detectGroup(key);

            if (!GroupType.UNKNOWN.equals(group))
            {
                Matcher idMatcher = ID_PATTERN.matcher(key);
                int id = idMatcher.find() ? Integer.parseInt(idMatcher.group(1)) : 0;

                return Optional.of(new ParsedProperty(group, id, key, value));
            }
        }

        return Optional.empty();
    }

    public DBInitPropertiesParser.PropertyEntry getFirstEntry(GroupType group)
    {
        return firstEntries.get(group);
    }

    public DBInitPropertiesParser.PropertyEntry getHighestIdEntry(GroupType group)
    {
        return highestIdEntries.get(group);
    }
}