package com.intershop.customization.migration.gradle;

import static com.intershop.customization.migration.common.MigrationContext.OperationType.DELETE;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import com.intershop.customization.migration.common.MigrationContext;
import com.intershop.customization.migration.common.MigrationPreparer;
import com.intershop.customization.migration.utils.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This migration step is used to search for the assembly block in the build.gradle file,
 * since that identifies the project as an assembly project.
 * If an assembly block is found, the entire project directory is deleted.
 * <p>
 * Example YAML configuration:
 * <pre>
 * type: specs.intershop.com/v1beta/migrate
 * migrator: com.intershop.customization.migration.gradle.RemoveAssembly
 * message: "refactor: remove assembly projects"
 * </pre>
 */
public class RemoveAssembly implements MigrationPreparer
{
    private final Logger LOGGER = LoggerFactory.getLogger(getClass());

    @Override
    public void migrate(Path projectDir, MigrationContext context)
    {
        Pattern assemblyPattern = Pattern.compile("^assembly\\s*\\{");// it is a top level block and should appear at the beginning of the line

        Path buildGradle = projectDir.resolve("build.gradle");
        try
        {
            List<String> lines = FileUtils.readAllLines(buildGradle);
            if (lines.stream().anyMatch(l -> assemblyPattern.matcher(l).find()))
            {
                deleteAssembly(projectDir, context);
                LOGGER.info("Assembly '{}' removed at location '{}'.", getResourceName(projectDir), projectDir);
            }
        }
        catch (IOException e)
        {
            LOGGER.error("Can't delete build.gradle", e);
            context.recordFailure(getResourceName(projectDir), DELETE, buildGradle, null,
                    "Error reading build.gradle: " + e.getMessage());
        }
    }

    /**
     * Deletes the entire given directory.
     * @param directory directory to delete
     */
    protected void deleteAssembly(Path directory, MigrationContext context)
    {
        String projectName = getResourceName(directory);

        try
        {
            Consumer<Path> removeConsumer = p -> {
                try
                {
                    Files.delete(p);
                    LOGGER.debug("Deleted: {}", p);
                    context.recordSuccess(projectName, DELETE, p, null);
                }
                catch(IOException e)
                {
                    context.recordFailure(projectName, DELETE, p, null, "Error deleting file: " + e.getMessage());
                    throw new RuntimeException(e);
                }
            };
            FileUtils.listFiles(directory, null, Comparator.reverseOrder())
                            .forEach(removeConsumer);
        }
        catch(IOException e)
        {
            LOGGER.error("Error while processing directory '{}': {}", directory, e.getMessage());
            context.recordFailure(projectName, DELETE, directory, null,
                    "Error traversing directory: " + e.getMessage());
        }
    }
}
