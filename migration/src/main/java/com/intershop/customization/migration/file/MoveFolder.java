package com.intershop.customization.migration.file;

import static com.intershop.customization.migration.common.MigrationContext.OperationType.DELETE;
import static com.intershop.customization.migration.common.MigrationContext.OperationType.MOVE;
import static com.intershop.customization.migration.file.MoveFilesConstants.PLACEHOLDER_CARTRIDGE_NAME;

import com.intershop.customization.migration.common.MigrationContext;
import com.intershop.customization.migration.common.MigrationPreparer;
import com.intershop.customization.migration.common.MigrationStep;
import com.intershop.customization.migration.utils.FileUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * This migration step is used to migrate the files structure of a cartridge
 * by moving all files of a configured folder from their old location to the
 * new source location. The folders to move are configured in the migration
 * step configuration.
 * <p>
 * The configuration is composed of the 2 required maps: `source-map` and
 * `target-map`.
 * The keys of the `source-map` and `target-map` are used to join the 2
 * definitions. It is recommended to use the name of the artifact groups
 * here. The values are the paths to the source and target directories,
 * respectively.
 * <p>
 * The placeholder `{cartridgeName}` can be used in path names and will
 * be replaced by the cartridge name.
 * <p>
 * Example YAML configuration:
 * <pre>
 * type: specs.intershop.com/v1beta/migrate
 * migrator: com.intershop.customization.migration.file.MoveFolder
 * message: "refactor: move staticfiles to resources"
 * options:
 *   source-map:
 *     "pipelines" : "staticfiles/cartridge/pipelines"
 *     "webforms" : "staticfiles/cartridge/webforms"
 *   target-map:
 *     "pipelines" : "src/main/resources/resources/{cartridgeName}/pipelines"
 *     "webforms" : "src/main/resources/resources/{cartridgeName}/webforms"
 * </pre>
 */
public class MoveFolder implements MigrationPreparer
{
    private final Logger LOGGER = LoggerFactory.getLogger(getClass());

    private static final String YAML_KEY_SOURCE_MAP = "source-map";
    private static final String YAML_KEY_TARGET_MAP = "target-map";
    private Map<String, String> sourceConfiguration = Collections.emptyMap();
    private Map<String, String> targetConfiguration = Collections.emptyMap();

    @Override
    public void setStep(MigrationStep step)
    {
        this.sourceConfiguration = step.getOption(YAML_KEY_SOURCE_MAP);
        this.targetConfiguration = step.getOption(YAML_KEY_TARGET_MAP);
    }

    @Override
    public void migrate(Path cartridgeDir, MigrationContext context)
    {
        String cartridgeName = getResourceName(cartridgeDir);
        LOGGER.info("Processing cartridge {}.", cartridgeName);

        for (Map.Entry<String, String> sourceEntry : sourceConfiguration.entrySet())
        {
            String artifactName = sourceEntry.getKey();
            Path sourcePath = cartridgeDir.resolve(sourceEntry.getValue().replace(PLACEHOLDER_CARTRIDGE_NAME, cartridgeName));
            if (!sourcePath.toFile().exists())
            {
                LOGGER.debug("Can't find cartridges folder '{}'.", sourcePath);
                context.recordSkipped(cartridgeName, MOVE, sourcePath, null, "Source folder does not exist");
                continue;
            }
            String targetPathAsString = targetConfiguration.get(artifactName);
            Path targetPath = cartridgeDir.resolve(targetPathAsString.replace(PLACEHOLDER_CARTRIDGE_NAME, cartridgeName));
            // move everything at once
            try
            {
                // create parent if not exists (required by Files.move)
                if (!targetPath.getParent().toFile().exists())
                {
                    targetPath.getParent().toFile().mkdirs();
                }
                // target must not exist (required by Files.move)
                if (!targetPath.toFile().exists())
                {
                    Files.move(sourcePath, targetPath);
                    context.recordSuccess(cartridgeName, MOVE, sourcePath, targetPath);

                    if ("cluster".equals(artifactName) || "domains".equals(artifactName))
                    {
                        LOGGER.warn("Files in '{}' need to be wired in configuration.xml.", targetPath);
                        context.recordWarning(cartridgeName, MOVE, sourcePath, targetPath,
                                "Files need to be wired in configuration.xml. " +
                                "For details about the configuration framework, see 'Concept - Configuration'.");
                    }
                }
                else {
                    LOGGER.warn("Folder '{}' exists.", targetPath);
                    context.recordSkipped(cartridgeName, MOVE, sourcePath, targetPath, "Target folder already exists");
                }
            }
            catch(IOException e)
            {
                context.recordFailure(cartridgeName, MOVE, sourcePath, targetPath,
                        "Can't move folder: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }

        removeEmptyStaticFilesFolder(cartridgeDir, cartridgeName, context);
        checkRemainingStaticFiles(cartridgeDir, cartridgeName, context);
    }

    /**
     * Removes the staticfiles directory if it only contains empty subdirectories. This helps clean up after migration
     * when all files have been moved to their new locations.
     *
     * @param cartridgeDir The cartridge directory
     * @param cartridgeName The name of the cartridge
     * @param context The migration context
     */
    private void removeEmptyStaticFilesFolder(Path cartridgeDir, String cartridgeName, MigrationContext context)
    {
        Path staticFilesDir = cartridgeDir.resolve("staticfiles");
        if (!Files.exists(staticFilesDir))
        {
            return;
        }

        try
        {
            // Check if the directory contains any regular files (recursively)
            List<Path> files = FileUtils.listFiles(staticFilesDir, Files::isRegularFile, null);

            if (files.isEmpty())
            {
                LOGGER.info("Removing empty staticfiles directory in cartridge {}", cartridgeName);

                // Delete all content recursively, then the directory itself
                try (var pathStream = Files.walk(staticFilesDir))
                {
                    pathStream.sorted(Comparator.reverseOrder()).forEach(path -> {
                        try
                        {
                            Files.delete(path);
                            context.recordSuccess(cartridgeName, DELETE, path, null);
                        }
                        catch (IOException e)
                        {
                            LOGGER.error("Failed to delete {}: {}", path, e.getMessage());
                            context.recordFailure(cartridgeName, DELETE, path, null,
                                    "Failed to delete empty staticfiles directory: " + e.getMessage());
                        }
                    });
                }
            }
            else
            {
                LOGGER.debug("Static files directory still contains files, not removing");
            }
        }
        catch (IOException e)
        {
            LOGGER.error("Error checking if staticfiles directory is empty: {}", e.getMessage());
            context.recordFailure(cartridgeName, DELETE, staticFilesDir, null,
                    "Error checking if staticfiles directory is empty: " + e.getMessage());
        }
    }

    /**
     * Checks if the staticfiles directory still exists after migration and contains unmapped directories. Records any
     * remaining directories as unknown operations.
     *
     * @param cartridgeDir The cartridge directory
     * @param cartridgeName The name of the cartridge
     * @param context The migration context
     */
    private void checkRemainingStaticFiles(Path cartridgeDir, String cartridgeName, MigrationContext context)
    {
        Path staticFilesDir = cartridgeDir.resolve("staticfiles");
        if (Files.exists(staticFilesDir))
        {
            try
            {
                // Check if staticfiles/cartridge exists and has content
                Path cartridgeStaticDir = staticFilesDir.resolve("cartridge");
                if (Files.exists(cartridgeStaticDir))
                {
                    FileUtils.listTopLevelFiles(cartridgeStaticDir, Files::isDirectory, null).forEach(dir -> {
                        Path relativePath = cartridgeDir.relativize(dir);
                        LOGGER.warn("Unmapped directory found in staticfiles/cartridge: {}", relativePath);
                        context.recordUnknown(cartridgeName, MOVE, dir, null,
                                "Unmapped directory in staticfiles/cartridge");
                    });
                }

                // Check other directories in staticfiles (not cartridge)
                FileUtils.listTopLevelFiles(staticFilesDir, path -> Files.isDirectory(path)
                                && !"cartridge".equals(path.getFileName().toString()), null)
                        .forEach(dir -> {
                            Path relativePath = cartridgeDir.relativize(dir);
                            LOGGER.warn("Unmapped directory found in staticfiles: {}", relativePath);
                            context.recordUnknown(cartridgeName, MOVE, dir, null, "Unmapped directory in staticfiles");
                        });

                // Check for files directly in staticfiles
                FileUtils.listTopLevelFiles(staticFilesDir, path -> !Files.isDirectory(path), null).forEach(file -> {
                    Path relativePath = cartridgeDir.relativize(file);
                    LOGGER.warn("Unmapped file found in staticfiles: {}", relativePath);
                    context.recordUnknown(cartridgeName, MOVE, file, null, "Unmapped file in staticfiles");
                });
            }
            catch (IOException e)
            {
                LOGGER.error("Error checking remaining staticfiles: {}", e.getMessage());
                context.recordFailure(cartridgeName, MOVE, staticFilesDir, null,
                        "Error checking remaining staticfiles: " + e.getMessage());
            }
        }
    }
}
