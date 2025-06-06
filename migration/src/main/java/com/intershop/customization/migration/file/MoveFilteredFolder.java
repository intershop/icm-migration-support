package com.intershop.customization.migration.file;

import static com.intershop.customization.migration.common.MigrationContext.OperationType.MOVE;
import static com.intershop.customization.migration.file.MoveFilesConstants.PLACEHOLDER_CARTRIDGE_NAME;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Stream;

import com.intershop.customization.migration.common.MigrationContext;
import com.intershop.customization.migration.common.MigrationPreparer;
import com.intershop.customization.migration.common.MigrationStep;
import com.intershop.customization.migration.utils.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MigrationPreparer implementation that moves files from a source folder to a target folder,
 * filtering files based on a provided filter pattern.
 * <p>
 * Example usage in YAML configuration:
 * <pre>
 * type: specs.intershop.com/v1beta/migrate
 * migrator: com.intershop.customization.migration.file.MoveFilteredFolder
 * message: "refactor: move javasource code to src/main/java and pipelet XMLs to resources"
 * options:
 *   source-map:
 *     "java" : "javasource"
 *     "pipeletXmls" : "javasource"
 *   target-map:
 *     "java" : "src/main/java"
 *     "pipeletXmls" : "src/main/resources"
 *   filter-map:
 *     "java" : ".*\\.java$"
 *     "pipeletXmls" : "^.*\\\\pipelet\\\\.*\\.xml$"
 * </pre>
 */
public class MoveFilteredFolder implements MigrationPreparer
{
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final static String YAML_KEY_SOURCE_MAP = "source-map";
    private final static String YAML_KEY_TARGET_MAP = "target-map";
    private final static String YAML_KEY_FILTER_MAP = "filter-map";

    private Map<String, String> sourceConfiguration = Collections.emptyMap();
    private Map<String, String> targetConfiguration = Collections.emptyMap();
    private Map<String, String> filterConfiguration = Collections.emptyMap();

    @Override
    public void setStep(MigrationStep step)
    {
        this.sourceConfiguration = step.getOption(YAML_KEY_SOURCE_MAP);
        this.targetConfiguration = step.getOption(YAML_KEY_TARGET_MAP);
        this.filterConfiguration = step.getOption(YAML_KEY_FILTER_MAP);
    }

    @Override
    public void migrate(Path cartridgeDir, MigrationContext context)
    {
        String cartridgeName = getResourceName(cartridgeDir);
        logger.info("Processing cartridge {}.", cartridgeName);

        for (Map.Entry<String, String> sourceEntry : sourceConfiguration.entrySet())
        {
            String artifactName = sourceEntry.getKey();

            // determine source path
            Path sourcePath = cartridgeDir.resolve(sourceEntry.getValue().replace(PLACEHOLDER_CARTRIDGE_NAME, cartridgeName));
            if (!Files.exists(sourcePath))
            {
                logger.debug("Cannot find cartridges folder '{}'.", sourcePath);
                context.recordSkipped(cartridgeName, MOVE, sourcePath, null, "Source folder does not exists.");
                continue;
            }

            // determine target path
            String targetPathAsString = targetConfiguration.get(artifactName);
            Path targetPath = cartridgeDir.resolve(targetPathAsString.replace(PLACEHOLDER_CARTRIDGE_NAME, cartridgeName));

            try
            {
                // create target folder if not exists
                if (!Files.exists(targetPath.getParent()))
                {
                    Files.createDirectories(targetPath.getParent());
                }

                // determine filter for files to move
                String filter = filterConfiguration.get(artifactName);

                try (Stream<Path> stream = Files.walk(sourcePath))
                {
                    stream.filter(Files::isRegularFile)
                        .filter(path -> shouldMove(path, filter))
                        .forEach(file -> {
                            try
                            {
                                Path relativePath = sourcePath.relativize(file);
                                Path destination = targetPath.resolve(relativePath);
                                Files.createDirectories(destination.getParent());
                                Files.move(file, destination);

                                logger.debug("Moved file {} to {}.", file, destination);
                                context.recordSuccess(cartridgeName, MOVE, file, destination);
                            }
                            catch (IOException e)
                            {
                                logger.error("An error occurred while moving file " + file + " to " + targetPath + ": " + e.getMessage(), e);
                                context.recordFailure(cartridgeName, MOVE, file, null, "Cannot move file: " + e.getMessage());
                            }
                        });
                }

                FileUtils.removeEmptyDirectories(sourcePath);
            }
            catch(IOException ioe)
            {
                logger.error("An error occurred while moving folder: " + ioe.getMessage(), ioe);
                context.recordFailure(cartridgeName, MOVE, sourcePath, targetPath, "Cannot move folder: " + ioe.getMessage());
                throw new RuntimeException(ioe);
            }
        }
    }

    // apply the filter to determine if a file should be moved
    private boolean shouldMove(Path path, String filter)
    {
        String absolutePath = path.toFile().getAbsolutePath();
        String normalizedPath = absolutePath.replaceAll("\\\\", "/");

        return normalizedPath.matches(filter);
    }
}
