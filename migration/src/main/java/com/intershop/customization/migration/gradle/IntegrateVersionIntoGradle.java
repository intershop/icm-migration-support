package com.intershop.customization.migration.gradle;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.intershop.customization.migration.common.MigrationPreparer;
import com.intershop.customization.migration.common.MigrationStep;

/**
 * This class is used to parse all version files (except intershopBuild.version)
 * and integrate the version information into the gradle.properties file.
 * <p>
 * Example YAML configuration:
 * <pre>
 * type: specs.intershop.com/v1beta/migrate
 * migrator: com.intershop.customization.migration.gradle.IntegrateVersionIntoGradle
 * message: "refactor: integrated version data into gradle.properties"
 * </pre>
 */
public class IntegrateVersionIntoGradle implements MigrationPreparer
{
    private final Logger LOGGER = LoggerFactory.getLogger(getClass());

    private static final Charset CHARSET_BUILD_GRADLE = Charset.defaultCharset();
    private static final String LINE_SEP = System.lineSeparator();
    
    @Override
    public void setStep(MigrationStep step)
    {
    }
    
    @Override
    public void migrate(Path projectDir)
    {
        List<Path> versionFiles = collectVersionFiles(projectDir);

        if (versionFiles.isEmpty())
        {
            LOGGER.info("No version files to migrate found in '{}'.", projectDir);
            return;
        }

        Path gradleProperties = projectDir.resolve("gradle.properties");
        try (Stream<String> linesStream = Files.lines(gradleProperties, CHARSET_BUILD_GRADLE))
        {
            List<String> lines = linesStream.collect(Collectors.toList());
            Files.writeString(gradleProperties, appendTo(lines, versionFiles), CHARSET_BUILD_GRADLE);
        }
        catch(IOException e)
        {
            LOGGER.error("Can't extend gradle.properties", e);
        }
    }

    /**
     * Collects all version files to migrate in the given project directory.
     * @param projectDir the project directory
     * @return a list of version files
     */
    protected List<Path> collectVersionFiles(Path projectDir)
    {
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:*.version");

        try (Stream<Path> pathStream = Files.list(projectDir))
        {
            return pathStream
                            .filter(Files::isRegularFile)
                            .filter(path -> matcher.matches(path.getFileName()))
                            .filter(path -> !path.getFileName().toString().equals("intershopBuild.version")) // exclude build version information
                            .toList();
        }
        catch (IOException e)
        {
            LOGGER.error("Error while resolving files of '{}': {}", projectDir, e.getMessage());
        }
        return Collections.emptyList();
    }

    /**
     * go step by step through migration steps to fix gradle build
     * @param lines lines to migrate
     * @param versionFiles list of version files to integrate into gradle.properties
     * @return new content of gradle.properties
     */
    String appendTo(List<String> lines, List<Path> versionFiles)
    {
        // add empty line as separator
        lines.add(LINE_SEP);
        lines.add("# migrated version information");

        for (Path versionFile : versionFiles)
        {
            String versionFileName = versionFile.getFileName().toString();
            String versionFileContent = null;
            try
            {
                versionFileContent = Files.readString(versionFile, CHARSET_BUILD_GRADLE);
            }
            catch (IOException e)
            {
                LOGGER.error("Can't read file '{}'", versionFile, e);
            }

            if (versionFileContent != null)
            {
                LOGGER.info("versionFileContent =  '{}'", versionFileContent);
                if (versionFileContent.contains("="))
                {
                    versionFileContent = versionFileContent.substring(versionFileContent.indexOf('=') + 1).trim();
                    LOGGER.info("stripped versionFileContent =  '{}'", versionFileContent);
                }

                String versionEntry = trimFileName(versionFileName) + " = " + versionFileContent;

                if (!lines.contains(versionEntry))
                {
                    LOGGER.info("Migrating version information for '{}:{}'", versionFileName, versionFileContent);
                    lines.add(versionEntry);
                    deleteFile(versionFile);
                }
            }
        }

        return String.join(LINE_SEP, lines);
    }

    /**
     * removes the file prefixes and extension from the version file name
     * @param versionFileName file name to trim
     * @return adjusted file name to be used as version reference
     */
    private static String trimFileName(String versionFileName)
    {
        return versionFileName
                        .replace(".pom", "")
                        .replace(".ivy", "")
                        .replace(".version", "");
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
