package com.intershop.customization.migration.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OperationLogTest
{
    @Test
    void quotesTheCharactersThatWouldBreakAJsonLine()
    {
        assertEquals("\"plain\"", OperationLog.quote("plain"));
        assertEquals("\"say \\\"hi\\\"\"", OperationLog.quote("say \"hi\""));
        assertEquals("\"C:\\\\temp\"", OperationLog.quote("C:\\temp"));
        // A raw newline would split one record across two lines and silently corrupt the log.
        assertEquals("\"a\\nb\"", OperationLog.quote("a\nb"));
        assertEquals("\"a\\u0001b\"", OperationLog.quote("a\u0001b"));
    }

    @Test
    void rendersNestedValues()
    {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("step", "020_MoveFolder");
        record.put("count", 3);
        record.put("commit", null);
        record.put("counts", Map.of("FAILED", 0));

        assertEquals("{\"step\":\"020_MoveFolder\",\"count\":3,\"commit\":null,\"counts\":{\"FAILED\":0}}",
                        OperationLog.toJson(record));
    }

    @Test
    void writesOneJsonObjectPerLine(@TempDir Path tempDir) throws IOException
    {
        try (OperationLog log = OperationLog.open(tempDir))
        {
            log.stepStart(0, "020_MoveFolder", "refactor: move");
            log.operation(0, "020_MoveFolder", "cartridge_a",
                            new MigrationContext.Operation("020_MoveFolder", MigrationContext.OperationType.MOVE,
                                            Path.of("a"), Path.of("b"),
                                            MigrationContext.OperationStatus.SUCCESS, null));
            log.stepEnd(0, "020_MoveFolder", "sha-1", Map.of("SUCCESS", 1));
        }

        List<String> lines = Files.readAllLines(tempDir.resolve("operations.jsonl"), StandardCharsets.UTF_8);
        assertEquals(3, lines.size());
        assertTrue(lines.get(0).contains("\"event\":\"step-start\""), lines.get(0));
        assertTrue(lines.get(1).contains("\"event\":\"operation\""), lines.get(1));
        assertTrue(lines.get(1).contains("\"step\":\"020_MoveFolder\""), lines.get(1));
        assertTrue(lines.get(2).contains("\"commit\":\"sha-1\""), lines.get(2));
    }

    /**
     * An unwritable report directory must degrade to no log, never abort a migration.
     */
    @Test
    void anUnusableDirectoryDoesNotThrow(@TempDir Path tempDir) throws IOException
    {
        Path blocker = tempDir.resolve("blocked");
        Files.writeString(blocker, "not a directory");

        assertEquals(null, OperationLog.open(blocker.resolve("nested")));
    }
}
