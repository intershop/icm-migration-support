package com.intershop.customization.migration.file;

import com.intershop.customization.migration.common.MigrationPreparer;
import com.intershop.customization.migration.common.MigrationStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

import static com.intershop.customization.migration.file.MoveFilesConstants.PLACEHOLDER_CARTRIDGE_NAME;

/**
 * This migration step is used to migrate the files structure of a cartridge
 * by moving files from their old location to the new. The files to
 * handle are defined in the migration step configuration.
 * <p>
 * The configuration is composed of the maps: `source-map`, `target-map`
 * and `filter-map`. The keys of the maps are used to match the 3 parts
 * together. The values are the paths to the source and
 * target directories and the filter pattern, respectively.
 * <p>
 * The placeholder `{cartridgeName}` can be used in path names and will
 * be replaced by the cartridge name.
 * <p>
 * Example YAML configuration:
 * <pre>
 * type: specs.intershop.com/v1beta/migrate
 * migrator: com.intershop.customization.migration.file.MoveFiles
 * message: "refactor: move dbinit and migration properties to new location"
 * options:
 *   source-map:
 *     "dbprepare" : "staticfiles/cartridge"
 *   target-map:
 *     "dbprepare" : "src/main/resources/resources/{cartridgeName}"
 *   filter-map:
 *     "dbprepare" : "(migration|dbinit).*\\.properties"
 * </pre>
 */
public class MoveFiles implements MigrationPreparer
{
    private final Logger LOGGER = LoggerFactory.getLogger(getClass());

    private static final String YAML_KEY_SOURCE_MAP = "source-map";
    private static final String YAML_KEY_TARGET_MAP = "target-map";
    private static final String YAML_KEY_FILTER_MAP = "filter-map";
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

    public void migrate(Path cartridgeDir)
    {
        String cartridgeName = getResourceName(cartridgeDir);
        LOGGER.info("Processing cartridge {}.", cartridgeName);

        for (Map.Entry<String, String> sourceEntry : sourceConfiguration.entrySet())
        {
            String artifactName = sourceEntry.getKey();
            Path sourcePath = cartridgeDir.resolve(sourceEntry.getValue().replace(PLACEHOLDER_CARTRIDGE_NAME, cartridgeName));
            if (!sourcePath.toFile().exists())
            {
                LOGGER.debug("Can't find cartridges folder {}.", sourcePath);
                continue;
            }
            String targetPathAsString = targetConfiguration.get(artifactName);
            Path targetPath = cartridgeDir.resolve(targetPathAsString.replace(PLACEHOLDER_CARTRIDGE_NAME, cartridgeName));
            // create target if not exists
            if (!targetPath.toFile().exists())
            {
                targetPath.toFile().mkdirs();
            }
            File[] files = sourcePath.toFile().listFiles();
            if (files != null)
            {
                for(File file : files)
                {
                    if (file.isDirectory())
                    {
                        continue;
                    }
                    String fileName = file.getName();
                    if (!shouldMigrate(fileName, artifactName))
                    {
                        continue;
                    }
                    Path targetFile = targetPath.resolve(fileName);
                    try
                    {
                        Files.move(file.toPath(), targetFile);
                    }
                    catch(IOException e)
                    {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }

    private boolean shouldMigrate(String fileName, String artifactName)
    {
        if (filterConfiguration.containsKey(artifactName))
        {
            return fileName.matches(filterConfiguration.get(artifactName));
        }
        return false;
    }
}
