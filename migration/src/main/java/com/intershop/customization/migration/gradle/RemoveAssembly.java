package com.intershop.customization.migration.gradle;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.intershop.customization.migration.common.MigrationPreparer;

public class RemoveAssembly implements MigrationPreparer
{
    private final Logger LOGGER = LoggerFactory.getLogger(getClass());

    private static final Charset CHARSET_BUILD_GRADLE = Charset.defaultCharset();

    @Override
    public void migrate(Path projectDir)
    {
        Pattern assemblyPattern = Pattern.compile("^assembly\\s*\\{");// it is a top level block and should appear at the beginning of the line

        Path buildGradle = projectDir.resolve("build.gradle");
        try (Stream<String> linesStream = Files.lines(buildGradle, CHARSET_BUILD_GRADLE))
        {
            if (linesStream.anyMatch(l -> assemblyPattern.matcher(l).find()))
            {
                deleteDirectoryRecursively(projectDir);
                LoggerFactory.getLogger(getClass()).info("Assembly '{}' removed at location '{}'.",
                                getResourceName(projectDir), projectDir);
            }
        }
        catch (IOException e)
        {
            LoggerFactory.getLogger(getClass()).error("Can't delete build.gradle", e);
        }
    }

    private void deleteDirectoryRecursively(Path directory)
    {
        try (Stream<Path> paths = Files.walk(directory))
        {
            paths.sorted(Comparator.reverseOrder()) // process files before directories
                 .forEach(path -> {
                     try
                     {
                         Files.delete(path);
                         LOGGER.debug("Deleted: {}", path);
                     }
                     catch (IOException e)
                     {
                         LOGGER.error("Error while deleting '{}': {}", path, e.getMessage());
                     }
                 });
        }
        catch (IOException e)
        {
            LOGGER.error("Error while processing directory '{}': {}", directory, e.getMessage());
        }
    }
}
