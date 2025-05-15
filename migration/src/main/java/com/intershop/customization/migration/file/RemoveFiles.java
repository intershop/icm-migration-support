package com.intershop.customization.migration.file;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
 * migrator: com.intershop.customization.migration.file.RemoveFiles
 * message: "refactor: removed *.bak files"
 * options:
 *   root-project:  // only applied to root project
 *     glob:
 *     - "*.bak"
 *     regex:
 *     - "*\\.bak$"
 *   sub-projects:  // applied to all sub-projects
 *     glob:
 *     - "*.bak"
 *     regex:
 *     - "*\\.bak$"
 * </pre>
 */
public class RemoveFiles implements MigrationPreparer
{
    private final Logger LOGGER = LoggerFactory.getLogger(getClass());

    private static final String YAML_KEY_ROOT_PROJECT = "root-project";
    private static final String YAML_KEY_SUB_PROJECTS = "sub-projects";
    private static final String YAML_KEY_GLOB = "glob";
    private static final String YAML_KEY_REGEX = "regex";

    private List<String> rootGlobPattern = Collections.emptyList();
    private List<String> rootRegexPattern = Collections.emptyList();
    private List<String> globPattern = Collections.emptyList();
    private List<String> regexPattern = Collections.emptyList();

    @Override
    public void setStep(MigrationStep step)
    {
        this.rootGlobPattern = getPatternFor(step, YAML_KEY_ROOT_PROJECT, YAML_KEY_GLOB);
        this.rootRegexPattern = getPatternFor(step, YAML_KEY_ROOT_PROJECT, YAML_KEY_REGEX);
        this.globPattern = getPatternFor(step, YAML_KEY_SUB_PROJECTS, YAML_KEY_GLOB);
        this.regexPattern = getPatternFor(step, YAML_KEY_SUB_PROJECTS, YAML_KEY_REGEX);
    }

    @SuppressWarnings("unchecked")
    private List<String> getPatternFor(MigrationStep step, String type, String patternKey)
    {
        Object root = step.getOption(type);
        if (root instanceof Map<?,?> rootData)
        {
            return Optional.ofNullable(rootData.get(patternKey))
                                           .map(o -> (List<String>)o)
                                           .orElse(Collections.emptyList());
        }

        return Collections.emptyList();
    }

    @Override
    public void migrateRoot(Path projectRoot)
    {
        String cartridgeName = getResourceName(projectRoot);
        LOGGER.info("Processing root project '{}'.", cartridgeName);

        rootGlobPattern.forEach(pattern -> deleteByPattern(projectRoot, YAML_KEY_GLOB, pattern));
        rootRegexPattern.forEach(pattern -> deleteByPattern(projectRoot, YAML_KEY_REGEX, pattern));
    }

    public void migrate(Path cartridgeDir)
    {
        String cartridgeName = getResourceName(cartridgeDir);
        LOGGER.info("Processing cartridge '{}'.", cartridgeName);

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
