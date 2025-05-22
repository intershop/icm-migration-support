package com.intershop.customization.migration.common;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Global context for migration operations that tracks file and folder operations. Provides a way for migrators to
 * report success, skipped, unknown, and failed operations.
 */
public class MigrationContext
{
    private static final Logger LOGGER = LoggerFactory.getLogger(MigrationContext.class);

    public enum OperationType
    {
        MOVE, COPY, DELETE, CREATE, MODIFY
    }

    public enum OperationStatus
    {
        SUCCESS, SKIPPED, UNKNOWN, FAILED
    }

    public record Operation(OperationType type, Path source, Path target, OperationStatus status, String message)
    {
        @Override
        public String toString()
        {
            return String.format("%s %s: %s -> %s (%s)", status, type, source != null ? source : "N/A",
                    target != null ? target : "N/A", message != null ? message : "");
        }
    }

    // Store operations by cartridge/project
    private final Map<String, List<Operation>> operationsByProject = new ConcurrentHashMap<>();
    private final Map<String, Map<OperationStatus, Integer>> statisticsByProject = new ConcurrentHashMap<>();

    /**
     * Record a file/folder operation
     *
     * @param projectName Project or cartridge name
     * @param type Operation type (MOVE, COPY, etc.)
     * @param source Source path (can be null for CREATE operations)
     * @param target Target path (can be null for DELETE operations)
     * @param status success, skipped, unknown, or failed
     * @param message Optional message explaining the operation's status
     */
    public void recordOperation(String projectName, OperationType type, Path source, Path target,
                                OperationStatus status, String message)
    {
        Operation op = new Operation(type, source, target, status, message);

        operationsByProject.computeIfAbsent(projectName, k -> Collections.synchronizedList(new ArrayList<>())).add(op);

        // Update statistics
        statisticsByProject.computeIfAbsent(projectName, k -> new HashMap<>()).merge(status, 1, Integer::sum);

        if (status == OperationStatus.FAILED)
        {
            LOGGER.warn("Failed operation in {}: {} - {}", projectName, op, message);
        }
        else
        {
            LOGGER.debug("Recorded operation in {}: {}", projectName, op);
        }
    }

    /**
     * Record a successful operation
     */
    public void recordSuccess(String projectName, OperationType type, Path source, Path target)
    {
        recordOperation(projectName, type, source, target, OperationStatus.SUCCESS, null);
    }

    /**
     * Record a skipped operation
     */
    public void recordSkipped(String projectName, OperationType type, Path source, Path target, String reason)
    {
        recordOperation(projectName, type, source, target, OperationStatus.SKIPPED, reason);
    }

    /**
     * Record an unknown operation
     */
    public void recordUnknown(String projectName, OperationType type, Path source, Path target, String reason)
    {
        recordOperation(projectName, type, source, target, OperationStatus.UNKNOWN, reason);
    }

    /**
     * Record a failed operation
     */
    public void recordFailure(String projectName, OperationType type, Path source, Path target, String error)
    {
        recordOperation(projectName, type, source, target, OperationStatus.FAILED, error);
    }

    /**
     * Get operations for a specific project
     */
    public List<Operation> getOperations(String projectName)
    {
        return operationsByProject.getOrDefault(projectName, Collections.emptyList());
    }

    /**
     * Get all operations across projects
     */
    public Map<String, List<Operation>> getAllOperations()
    {
        return Collections.unmodifiableMap(operationsByProject);
    }

    /**
     * Get statistics for a specific project
     */
    public Map<OperationStatus, Integer> getStatistics(String projectName)
    {
        return statisticsByProject.getOrDefault(projectName, Collections.emptyMap());
    }

    /**
     * Get a summary of operations for all projects
     */
    public Map<String, Map<OperationStatus, Integer>> getAllStatistics()
    {
        return Collections.unmodifiableMap(statisticsByProject);
    }

    /**
     * Generate a summary report of all operations
     */
    public String generateSummaryReport()
    {
        StringBuilder report = new StringBuilder("Migration Summary Report:\n");

        for (String project : operationsByProject.keySet())
        {
            Map<OperationStatus, Integer> stats = statisticsByProject.getOrDefault(project, Collections.emptyMap());
            int success = stats.getOrDefault(OperationStatus.SUCCESS, 0);
            int skipped = stats.getOrDefault(OperationStatus.SKIPPED, 0);
            int unknown = stats.getOrDefault(OperationStatus.UNKNOWN, 0);
            int failed = stats.getOrDefault(OperationStatus.FAILED, 0);
            int operationsSum = success + skipped + unknown + failed;

            report.append(String.format("Project '%s': %d operations (%d successful, %d skipped, %d unknown, %d failed)%n",
                    project, operationsSum, success, skipped, unknown, failed));

            // List unknown operations for quick review
            if (unknown > 0)
            {
                report.append("  Unknown operations:\n");
                operationsByProject.get(project)
                        .stream()
                        .filter(op -> op.status() == OperationStatus.UNKNOWN)
                        .forEach(op -> report.append("    - ").append(op).append("\n"));
            }

            // List failed operations for quick review
            if (failed > 0)
            {
                report.append("  Failed operations:\n");
                operationsByProject.get(project)
                        .stream()
                        .filter(op -> op.status() == OperationStatus.FAILED)
                        .forEach(op -> report.append("    - ").append(op).append("\n"));
            }
        }

        return report.toString();
    }
}
