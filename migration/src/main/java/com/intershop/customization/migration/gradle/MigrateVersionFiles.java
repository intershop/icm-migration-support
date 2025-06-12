package com.intershop.customization.migration.gradle;

import static com.intershop.customization.migration.common.MigrationContext.OperationType.MODIFY;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.intershop.customization.migration.common.MigrationContext;
import com.intershop.customization.migration.common.MigrationPreparer;
import com.intershop.customization.migration.common.Position;
import com.intershop.customization.migration.utils.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is used to parse version files (except: 'intershopBuild.version', '.ivy*.version', '.pom*.version')
 * and integrate the version information containing information about the artifact group, name and version
 * into the `versions/build.gradle.kts` file.
 * <p>
 * Migration to Kotlin is out of scope for this step and performed later in a separate step.
 * <p>
 * Example YAML configuration:
 * <pre>
 * type: specs.intershop.com/v1beta/migrate
 * migrator: com.intershop.customization.migration.gradle.MigrateVersionFiles
 * message: "refactor: transfer data from '*.version' files into 'versions/build.gradle.kts'"
 * </pre>
 */
public class MigrateVersionFiles implements MigrationPreparer
{
    private final Logger LOGGER = LoggerFactory.getLogger(getClass());

    private static final String LINE_SEP = System.lineSeparator();
    private static final String START_CONSTRAINTS = "constraints";
    public static final String EMPTY = "";
    public static final String BUILD_GRADLE_KTS = "build.gradle.kts";

    @Override
    public void prepareMigrateRoot(Path projectDir, MigrationContext context)
    {
        Path versionsDir = projectDir.resolve("versions");
        if (!Files.exists(versionsDir.resolve(BUILD_GRADLE_KTS)))
        {
            context.recordCriticalError("'" + BUILD_GRADLE_KTS + "' does not exist in " + versionsDir);
        }
    }

    @Override
    public void migrateRoot(Path projectDir, MigrationContext context)
    {
        List<Path> versionFiles = collectVersionFiles(projectDir);
        String projectName = getResourceName(projectDir);

        if (versionFiles.isEmpty())
        {
            LOGGER.info("No version files to migrate found in '{}'.", projectDir);
            return;
        }

        Map<String, Collection<String>> collectedVersionData = collectMigratedVersionData(versionFiles);
        if (collectedVersionData.isEmpty())
        {
            LOGGER.info("No version data to be migrated in '{}'.", projectDir);
            return;
        }

        Path versionsBuild = projectDir.resolve("versions").resolve(BUILD_GRADLE_KTS);
        try
        {
            List<String> linesStream = FileUtils.readAllLines(versionsBuild);
            FileUtils.writeString(versionsBuild, migrate(linesStream, collectedVersionData));
            context.recordSuccess(projectName, MODIFY, null, projectDir);
        }
        catch(IOException e)
        {
            LOGGER.error("Can't migrate *.version files to 'versions' project", e);
            context.recordFailure(projectName, MODIFY, null, projectDir,
                    "Can't migrate *.version files to 'versions' project: " + e.getMessage());
        }
    }

    /**
     * go step by step through migration steps to fix gradle build
     * @param lines lines to migrate
     * @param collectedVersionData map of version data comprised of filename (key) and migrated version information (value)
     * @return new content of versions/build.gradle.kts
     */
    String migrate(List<String> lines, Map<String, Collection<String>> collectedVersionData)
    {
        Position constraintsPos = Position.findBracketBlock(START_CONSTRAINTS, lines)
                                          .orElse(Position.NOT_FOUND(lines));
        List<String> constraintsLines = constraintsPos.matchingLines();
        List<String> beforeConstraintsLines = constraintsPos.nonMatchingLinesBefore();
        List<String> afterConstraintsLines = constraintsPos.nonMatchingLinesAfter();

        List<String> migratedConstraintsLines = new ArrayList<>();
        boolean first = true;
        for (String line : constraintsLines)
        {
            migratedConstraintsLines.add(line);

            // add migrated version information direct after the constraints block started
            if (first)
            {
                collectedVersionData.entrySet().stream()
                                .flatMap(entry -> {
                                    migratedConstraintsLines.add(EMPTY); // empty line
                                    migratedConstraintsLines.add("        // migrated version information of '" + entry.getKey() + "'");
                                    return entry.getValue().stream();
                                })
                                .map(migratedLine -> ("        api \"" + migratedLine + "\"")).forEach(migratedConstraintsLines::add);

                migratedConstraintsLines.add(EMPTY); // empty line
                first = false;
            }
        }

        // build result: start with code before constraints block
        String result = String.join(LINE_SEP, beforeConstraintsLines) + LINE_SEP;
        // now add adapted constraints block
        result += String.join(LINE_SEP, migratedConstraintsLines) + LINE_SEP;
        // finally add remaining code (after constraints block)
        result += String.join(LINE_SEP, afterConstraintsLines) + LINE_SEP;

        return result;
    }

    /**
     * Collects version files to migrate in the given project directory.
     * @param projectDir the project directory
     * @return a list of version files
     */
    protected List<Path> collectVersionFiles(Path projectDir)
    {
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:*.version");
        PathMatcher excludeMatcher = FileSystems.getDefault().getPathMatcher("glob:{.ivy*,.pom*,intershopBuild}.version");

        try (Stream<Path> pathStream = Files.list(projectDir))
        {
            return pathStream
                        .filter(Files::isRegularFile)
                        .filter(path -> matcher.matches(path.getFileName()))
                        .filter(path -> !excludeMatcher.matches(path.getFileName()))
                        .toList();
        }
        catch (IOException e)
        {
            LOGGER.error("Error while resolving files of '{}': {}", projectDir, e.getMessage());
        }
        return Collections.emptyList();
    }

    /**
     * Collects the data of the version files to migrate in the given project directory.
     * @param versionFiles list of version files to process
     * @return a map of version data, where the key is the filename and the value is a collection of migrated version data
     */
    protected Map<String, Collection<String>> collectMigratedVersionData(List<Path> versionFiles)
    {
        Map<String, Collection<String>> versionData = new HashMap<>();
        for (Path versionFile : versionFiles)
        {
            LOGGER.info("Migrating version information for '{}'", versionFile);
            try
            {
                List<String> versionFileLines = FileUtils.readAllLines(versionFile);
                Collection<String> migratedVersions = versionFileLines.stream()
                                                                      .map(this::migrateVersion)
                                                                      .filter(Objects::nonNull)
                                                                      .collect(Collectors.toSet());

                versionData.put(versionFile.getFileName().toString(), migratedVersions);
                deleteFile(versionFile);
            }
            catch (IOException e)
            {
                LOGGER.error("Can't read file '{}'", versionFile, e);
            }
        }

        return versionData;
    }

    /**
     * Migrates the version line to the format "group:name:version".
     * @param line the line containing the version data to migrate
     * @return the migrated line in the format "group:name:version" or null if the line is not in the expected format
     */
    protected String migrateVersion(String line)
    {
        if (line.isEmpty() || line.startsWith("#"))
        {
            return line;
        }

        if (!line.contains("="))
        {
            return null;
        }

        return line.replace(" ", EMPTY).replace("=", ":");
    }


    /**
     * Deletes the given file.
     * @param file file to delete
     */
    protected void deleteFile(Path file)
    {
        LOGGER.info("Deleting file: '{}'", file);
        try
        {
            Files.delete(file);
        }
        catch (IOException e)
        {
            LOGGER.error("Error while deleting file '{}': {}", file, e.getMessage());
        }
    }
}
