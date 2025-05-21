package com.intershop.customization.migration.gradle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Pattern;

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
    public void migrate(Path projectDir)
    {
        Pattern assemblyPattern = Pattern.compile("^assembly\\s*\\{");// it is a top level block and should appear at the beginning of the line

        Path buildGradle = projectDir.resolve("build.gradle");
        try
        {
            List<String> lines = FileUtils.readAllLines(buildGradle);
            if (lines.stream().anyMatch(l -> assemblyPattern.matcher(l).find()))
            {
                deleteAssembly(projectDir);
                LoggerFactory.getLogger(getClass()).info("Assembly '{}' removed at location '{}'.",
                                getResourceName(projectDir), projectDir);
            }
        }
        catch (IOException e)
        {
            LoggerFactory.getLogger(getClass()).error("Can't delete build.gradle", e);
        }
    }

    /**
     * Deletes the entire given directory.
     * @param directory directory to delete
     */
    protected void deleteAssembly(Path directory)
    {
        try
        {
            Consumer<Path> removeConsumer = p -> {
                try
                {
                    Files.delete(p);
                    LOGGER.debug("Deleted: {}", p);
                }
                catch(IOException e)
                {
                    throw new RuntimeException(e);
                }
            };
            FileUtils.listFiles(directory, Optional.empty(), Optional.of(Comparator.reverseOrder()))
                            .forEach(removeConsumer);
        }
        catch(IOException e)
        {
            LOGGER.error("Error while processing directory '{}': {}", directory, e.getMessage());
        }
    }
}
