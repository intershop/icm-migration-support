package com.intershop.customization.migration.common;

import static com.intershop.customization.migration.common.MigrationContext.OperationStatus.FAILED;
import static com.intershop.customization.migration.common.MigrationContext.OperationStatus.SUCCESS;
import static com.intershop.customization.migration.common.MigrationContext.OperationType.MOVE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class MigrationContextTest
{
    private static final Path SOURCE = Path.of("a");
    private static final Path TARGET = Path.of("b");

    @Test
    void operationsAreAttributedToTheRunningStep()
    {
        MigrationContext context = new MigrationContext();

        context.beginStep(0, "020_MoveFolder", "refactor: move");
        context.recordSuccess("cartridge_a", MOVE, SOURCE, TARGET);
        context.endStep("sha-1");

        context.beginStep(1, "030_ConvertBuildGradle", "refactor: convert");
        context.recordFailure("cartridge_a", MOVE, SOURCE, TARGET, "boom");
        context.endStep("sha-2");

        String report = context.generateSummaryReport();
        assertTrue(report.contains("[030_ConvertBuildGradle]"),
                        "the failing operation must name the step that produced it: " + report);
    }

    @Test
    void failedOperationsAreVisibleToTheCaller()
    {
        MigrationContext context = new MigrationContext();
        context.beginStep(0, "020_MoveFolder", "refactor: move");

        assertFalse(context.hasFailedOperations());

        context.recordFailure("cartridge_a", MOVE, SOURCE, TARGET, "boom");

        assertTrue(context.hasFailedOperations(), "a failed operation must be reportable without parsing the log");
        assertEquals(1, context.countByStatus(FAILED));
    }

    @Test
    void identicalOperationsAreDeduplicated()
    {
        MigrationContext context = new MigrationContext();
        context.beginStep(0, "020_MoveFolder", "refactor: move");

        context.recordSuccess("cartridge_a", MOVE, SOURCE, TARGET);
        context.recordSuccess("cartridge_a", MOVE, SOURCE, TARGET);

        assertEquals(1, context.countByStatus(SUCCESS));
    }

    /**
     * Regression cover for a broken equals/hashCode contract: {@code equals} used to ignore the message while the
     * record-generated {@code hashCode} included it. Two reports about the same path with different reasons are
     * different evidence and both have to survive.
     */
    @Test
    void operationsDifferingOnlyInMessageAreBothKept()
    {
        MigrationContext context = new MigrationContext();
        context.beginStep(0, "020_MoveFolder", "refactor: move");

        context.recordFailure("cartridge_a", MOVE, SOURCE, TARGET, "target already exists");
        context.recordFailure("cartridge_a", MOVE, SOURCE, TARGET, "permission denied");

        assertEquals(2, context.countByStatus(FAILED));
        String report = context.generateSummaryReport();
        assertTrue(report.contains("target already exists"), report);
        assertTrue(report.contains("permission denied"), report);
    }

    /**
     * The same operation recorded under two different steps is two facts, not one.
     */
    @Test
    void theSameOperationInTwoStepsIsRecordedTwice()
    {
        MigrationContext context = new MigrationContext();

        context.beginStep(0, "020_MoveFolder", "refactor: move");
        context.recordSuccess("cartridge_a", MOVE, SOURCE, TARGET);
        context.endStep(null);

        context.beginStep(1, "025_MoveJavasource", "refactor: move java");
        context.recordSuccess("cartridge_a", MOVE, SOURCE, TARGET);
        context.endStep(null);

        assertEquals(2, context.countByStatus(SUCCESS));
    }
}
