package com.intershop.customization.migration.file;

import com.intershop.customization.migration.common.MigrationPreparer;
import com.intershop.customization.migration.common.MigrationStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

import static com.intershop.customization.migration.file.MoveFilesConstants.PLACEHOLDER_CARTRIDGE_NAME;

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
                LOGGER.debug("Can't find cartridges folder '{}'.", sourcePath);
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
                }
                else {
                    LoggerFactory.getLogger(getClass()).warn("Folder '{}' exists.", targetPath);
                }
            }
            catch(IOException e)
            {
                throw new RuntimeException(e);
            }
        }
    }
}
