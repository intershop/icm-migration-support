package com.intershop.customization.migration.common;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Global context for migration operations that tracks file and folder operations. Provides a way for migrators to
 * report success, skipped, unknown, warning and failed operations.
 * <p>
 * Every operation is attributed to the migration step that produced it, and, when a log is attached with
 * {@link #attachLog(OperationLog)}, streamed to a machine-readable log as it happens. The prose summary is generated
 * from the same records.
 */
public class MigrationContext
{
    private final Logger LOGGER = LoggerFactory.getLogger(getClass());
    private final List<String> criticalErrors = new ArrayList<>();

    public enum OperationType
    {
        MOVE, DELETE, CREATE, MODIFY
    }

    public enum OperationStatus
    {
        SUCCESS, SKIPPED, UNKNOWN, WARNING, FAILED
    }

    /**
     * One file or folder operation, attributed to the step that produced it.
     * <p>
     * {@code equals} and {@code hashCode} cover every component on purpose. An earlier version excluded
     * {@code message} from {@code equals} while the record-generated {@code hashCode} still included it, which breaks
     * the equals/hashCode contract: two operations that compared equal could land in different hash buckets, so the
     * de-duplication below did not reliably happen anyway. Covering everything makes de-duplication exact and stops
     * two genuinely different reports collapsing into one, which for an agent reading this log is lost evidence.
     */
    public record Operation(String step, OperationType type, Path source, Path target, OperationStatus status,
                    String message)
    {
        @Override
        public String toString()
        {
            String sourcePath = source != null ? source.toString() : "N/A";
            String targetPath = target != null ? target.toString() : "N/A";
            String path = Objects.equals(source, target)
                    ? sourcePath
                    : String.format("%s -> %s", sourcePath, targetPath);

            return String.format("%s %s: %s (%s)", status, type, path, message);
        }
    }

    // Store operations by cartridge/project. LinkedHashSet so the log preserves the order things happened in,
    // which is what makes a run reconstructable after the fact.
    private final Map<String, Set<Operation>> operationsByProject = new TreeMap<>();
    private final Map<String, Map<OperationStatus, Integer>> statisticsByProject = new HashMap<>();

    private OperationLog log = null;
    private String currentStep = "unknown";
    private int currentStepIndex = -1;
    private Map<OperationStatus, Integer> currentStepCounts = new EnumMap<>(OperationStatus.class);

    /**
     * Attaches a machine-readable log. Operations recorded from now on are streamed to it as well as kept in memory.
     *
     * @param log the log to write to, may be {@code null} to disable structured logging
     */
    public void attachLog(OperationLog log)
    {
        this.log = log;
    }

    /**
     * Marks the start of a migration step. Every operation recorded afterwards is attributed to it.
     *
     * @param stepIndex zero-based position of the step in the step folder
     * @param step the step name, typically the step descriptor file name
     * @param message the commit message the step will use
     */
    public void beginStep(int stepIndex, String step, String message)
    {
        this.currentStepIndex = stepIndex;
        this.currentStep = step;
        this.currentStepCounts = new EnumMap<>(OperationStatus.class);
        if (log != null)
        {
            log.stepStart(stepIndex, step, message);
        }
    }

    /**
     * Marks the end of a migration step.
     *
     * @param commit the commit the step produced, or {@code null} if it changed nothing or auto-commit is off
     */
    public void endStep(String commit)
    {
        if (log != null)
        {
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (OperationStatus status : OperationStatus.values())
            {
                counts.put(status.name(), currentStepCounts.getOrDefault(status, 0));
            }
            log.stepEnd(currentStepIndex, currentStep, commit, counts);
        }
    }

    /**
     * Record a file/folder operation
     *
     * @param projectName Project or cartridge name
     * @param type Operation type (MOVE, DELETE, etc.)
     * @param source Source path (can be null for CREATE operations)
     * @param target Target path (can be null for DELETE operations)
     * @param status success, skipped, unknown, or failed
     * @param message Optional message explaining the operation's status
     */
    public void recordOperation(String projectName, OperationType type, Path source, Path target,
                                OperationStatus status, String message)
    {
        Operation op = new Operation(currentStep, type, source, target, status, message);

        Set<Operation> projectOperations = operationsByProject.computeIfAbsent(projectName,
                        k -> new LinkedHashSet<>());
        if (!projectOperations.add(op))
        {
            return;
        }

        statisticsByProject.computeIfAbsent(projectName, k -> new EnumMap<>(OperationStatus.class))
                .merge(status, 1, Integer::sum);
        currentStepCounts.merge(status, 1, Integer::sum);

        if (log != null)
        {
            log.operation(currentStepIndex, currentStep, projectName, op);
        }

        if (status == OperationStatus.FAILED)
        {
            LOGGER.warn("Failed operation in {}: {} - {}", projectName, op, message);
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
     * Record a warning for an operation
     */
    public void recordWarning(String projectName, OperationType type, Path source, Path target, String warning)
    {
        recordOperation(projectName, type, source, target, OperationStatus.WARNING, warning);
    }

    /**
     * Record a failed operation
     */
    public void recordFailure(String projectName, OperationType type, Path source, Path target, String error)
    {
        recordOperation(projectName, type, source, target, OperationStatus.FAILED, error);
    }

    /**
     * Record a critical error that should cause the entire migration to abort.
     *
     * @param message The error message explaining why the migration should be aborted
     */
    public void recordCriticalError(String message)
    {
        this.criticalErrors.add(message);
        if (log != null)
        {
            log.criticalError(message);
        }
    }

    /**
     * Checks if a critical errors has been recorded that should abort the migration.
     *
     * @return {@code true} if a critical error has been recorded, {@code false} otherwise
     */
    public boolean hasCriticalError()
    {
        return !criticalErrors.isEmpty();
    }

    /**
     * Get the recorded critical errors, if any.
     *
     * @return List of critical error messages
     */
    public List<String> getCriticalErrors()
    {
        return criticalErrors;
    }

    /**
     * Whether any operation failed. Drives the process exit code, so that a run reporting failures cannot also report
     * success: the caller must not have to read the log to find out whether the run worked.
     *
     * @return {@code true} if at least one operation was recorded as FAILED
     */
    public boolean hasFailedOperations()
    {
        return countByStatus(OperationStatus.FAILED) > 0;
    }

    /**
     * Total number of operations recorded with the given status, across all projects.
     *
     * @param status the status to count
     * @return the number of matching operations
     */
    public int countByStatus(OperationStatus status)
    {
        return statisticsByProject.values().stream()
                .mapToInt(stats -> stats.getOrDefault(status, 0))
                .sum();
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
            int warning = stats.getOrDefault(OperationStatus.WARNING, 0);
            int failed = stats.getOrDefault(OperationStatus.FAILED, 0);
            int operationsSum = success + skipped + unknown + warning + failed;

            report.append(String.format("Project '%s': %d operations (%d successful, %d skipped, %d unknown, %d warnings, %d failed)%n",
                    project, operationsSum, success, skipped, unknown, warning, failed));

            appendOperations(report, project, OperationStatus.UNKNOWN, unknown, "Unknown operations");
            appendOperations(report, project, OperationStatus.WARNING, warning, "Warnings");
            appendOperations(report, project, OperationStatus.FAILED, failed, "Failed operations");
        }

        return report.toString();
    }

    private void appendOperations(StringBuilder report, String project, OperationStatus status, int count,
                    String heading)
    {
        if (count <= 0)
        {
            return;
        }
        report.append("  ").append(heading).append(":\n");
        operationsByProject.get(project)
                .stream()
                .filter(op -> op.status() == status)
                .forEach(op -> report.append("    - [").append(op.step()).append("] ").append(op).append("\n"));
    }
}
