package com.intershop.customization.migration.file;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.intershop.customization.migration.common.MigrationPreparer;
import com.intershop.customization.migration.common.MigrationStep;

/**
 * This class is used to remove files from a cartridge directory based on the
 * configuration of the migration step
 * <p>
 * The configuration is defined in the migration step and contains a map of
 * patterns to match files for deletion. The keys of the map are the
 * pattern types (glob or regex) and the values are the patterns to match.
 * See {@link java.nio.file.FileSystem#getPathMatcher(String)} for more details.
 * <p>
 * Deletion of Directories is not supported by this migration preparer.
 * <p>
 * Example YAML configuration:
 * <pre>
 * type: specs.intershop.com/v1beta/migrate
 * migrator: migrator: com.intershop.customization.migration.file.RemoveFiles
 * message: "refactor: removed *.bak files"
 * options:
 *   glob:
 *   - "*.bak"
 *   regex:
 *   - "*\\.bak$"
 * </pre>
 */
public class RemoveFiles implements MigrationPreparer
{
    private final Logger LOGGER = LoggerFactory.getLogger(getClass());

    private static final String YAML_KEY_GLOB = "glob";
    private static final String YAML_KEY_REGEX = "regex";

    private List<String> globPattern = Collections.emptyList();
    private List<String> regexPattern = Collections.emptyList();

    @Override
    @SuppressWarnings("unchecked")
    public void setStep(MigrationStep step)
    {
        this.globPattern = (List<String>)Optional.ofNullable(step.getOption(YAML_KEY_GLOB))
                                                 .orElse(Collections.emptyList());
        this.regexPattern = (List<String>)Optional.ofNullable(step.getOption(YAML_KEY_REGEX))
                                                  .orElse(Collections.emptyList());
    }

    public void migrate(Path cartridgeDir)
    {
        String cartridgeName = getResourceName(cartridgeDir);
        LOGGER.info("Processing cartridge {}.", cartridgeName);

        globPattern.forEach(pattern -> deleteByPattern(cartridgeDir, YAML_KEY_GLOB, pattern));
        regexPattern.forEach(pattern -> deleteByPattern(cartridgeDir, YAML_KEY_REGEX, pattern));
    }

    private void deleteByPattern(Path cartridgeDir, String patternType, String pattern)
    {
        LOGGER.debug("Deleting files with '{}' pattern '{}'", patternType, pattern);

        PathMatcher matcher = FileSystems.getDefault().getPathMatcher(patternType + ":" + pattern);

        try (Stream<Path> pathStream = Files.list(cartridgeDir))
        {
            pathStream
                .filter(Files::isRegularFile)
                .filter(path -> matcher.matches(path.getFileName()))
                .forEach(file -> {
                    LOGGER.info("Deleting file: '{}'", file);
                    try
                    {
                        Files.delete(file);
                    }
                    catch (IOException e)
                    {
                        LOGGER.error("Error while deleting file '{}': {}", file, e.getMessage());
                    }
                });
        }
        catch (IOException e)
        {
            LOGGER.error("Error while resolving files of '{}': {}", cartridgeDir, e.getMessage());
        }
    }
}
