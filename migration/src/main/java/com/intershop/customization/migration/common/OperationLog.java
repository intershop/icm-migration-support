package com.intershop.customization.migration.common;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes one JSON object per line describing everything the migration did.
 * <p>
 * The human-readable summary is generated from the same records, so the two can never disagree. The machine format is
 * the primary one: the main consumer of this tool is an agent that has to verify the run, and prose is the wrong shape
 * for that. Records are flushed as they happen, so an aborted run still leaves a usable log.
 * <p>
 * Three record shapes share the file, distinguished by {@code event}:
 * <ul>
 *   <li>{@code step-start} - a step is about to run</li>
 *   <li>{@code operation}  - one file or folder operation</li>
 *   <li>{@code step-end}   - the step finished, carrying the commit it produced</li>
 * </ul>
 * Per-file content hashes are deliberately absent: the tool commits per step, so byte identity of a move is already
 * provable from git itself with {@code git log --follow} or {@code git show --stat}. Recording the commit here is what
 * makes that reachable.
 */
public class OperationLog implements AutoCloseable
{
    private static final Logger LOGGER = LoggerFactory.getLogger(OperationLog.class);

    private final Path file;
    private final BufferedWriter writer;

    private OperationLog(Path file, BufferedWriter writer)
    {
        this.file = file;
        this.writer = writer;
    }

    /**
     * Opens a log in the given directory, creating it if needed. Never throws: an unusable log must not abort a
     * migration, it degrades to no log and says so.
     *
     * @param directory target directory for {@code operations.jsonl}
     * @return the log, or {@code null} if it could not be opened
     */
    public static OperationLog open(Path directory)
    {
        try
        {
            Files.createDirectories(directory);
            Path file = directory.resolve("operations.jsonl");
            BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.WRITE);
            LOGGER.info("Writing structured operation log to '{}'.", file);
            return new OperationLog(file, writer);
        }
        catch(IOException e)
        {
            LOGGER.warn("Could not open the structured operation log in '{}'. Continuing without it.", directory, e);
            return null;
        }
    }

    public Path getFile()
    {
        return file;
    }

    public void stepStart(int stepIndex, String step, String message)
    {
        Map<String, Object> record = base("step-start");
        record.put("stepIndex", stepIndex);
        record.put("step", step);
        record.put("message", message);
        write(record);
    }

    public void stepEnd(int stepIndex, String step, String commit, Map<String, Integer> counts)
    {
        Map<String, Object> record = base("step-end");
        record.put("stepIndex", stepIndex);
        record.put("step", step);
        record.put("commit", commit);
        record.put("counts", counts);
        write(record);
    }

    public void operation(int stepIndex, String step, String project, MigrationContext.Operation operation)
    {
        Map<String, Object> record = base("operation");
        record.put("stepIndex", stepIndex);
        record.put("step", step);
        record.put("project", project);
        record.put("type", operation.type().name());
        record.put("status", operation.status().name());
        record.put("source", operation.source() == null ? null : operation.source().toString());
        record.put("target", operation.target() == null ? null : operation.target().toString());
        record.put("message", operation.message());
        write(record);
    }

    public void criticalError(String message)
    {
        Map<String, Object> record = base("critical-error");
        record.put("message", message);
        write(record);
    }

    private Map<String, Object> base(String event)
    {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("ts", Instant.now().toString());
        record.put("event", event);
        return record;
    }

    private void write(Map<String, Object> record)
    {
        try
        {
            writer.write(toJson(record));
            writer.newLine();
            // Flushed per record so an aborted run still leaves everything that happened before it.
            writer.flush();
        }
        catch(IOException e)
        {
            LOGGER.warn("Could not write to the operation log.", e);
        }
    }

    @Override
    public void close()
    {
        try
        {
            writer.close();
        }
        catch(IOException e)
        {
            LOGGER.warn("Could not close the operation log.", e);
        }
    }

    // --- minimal JSON writer -------------------------------------------------
    // Deliberately dependency-free: the records are flat maps of strings, numbers and one nested
    // map of counts, so a full JSON library would be the only reason to add a dependency here.

    @SuppressWarnings("unchecked")
    static String toJson(Object value)
    {
        if (value == null)
        {
            return "null";
        }
        if (value instanceof String string)
        {
            return quote(string);
        }
        if (value instanceof Number || value instanceof Boolean)
        {
            return value.toString();
        }
        if (value instanceof Map<?, ?> map)
        {
            List<String> parts = new ArrayList<>();
            for (Map.Entry<?, ?> entry : ((Map<Object, Object>) map).entrySet())
            {
                parts.add(quote(String.valueOf(entry.getKey())) + ":" + toJson(entry.getValue()));
            }
            return "{" + String.join(",", parts) + "}";
        }
        if (value instanceof Iterable<?> iterable)
        {
            List<String> parts = new ArrayList<>();
            iterable.forEach(item -> parts.add(toJson(item)));
            return "[" + String.join(",", parts) + "]";
        }
        return quote(value.toString());
    }

    static String quote(String value)
    {
        StringBuilder result = new StringBuilder(value.length() + 2);
        result.append('"');
        for (int i = 0; i < value.length(); i++)
        {
            char c = value.charAt(i);
            switch (c)
            {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                case '\b' -> result.append("\\b");
                case '\f' -> result.append("\\f");
                default -> {
                    if (c < 0x20)
                    {
                        result.append(String.format("\\u%04x", (int) c));
                    }
                    else
                    {
                        result.append(c);
                    }
                }
            }
        }
        return result.append('"').toString();
    }
}
