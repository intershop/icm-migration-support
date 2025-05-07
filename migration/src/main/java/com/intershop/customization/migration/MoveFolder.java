package com.intershop.customization.migration;

import com.intershop.customization.migration.common.MigrationPreparer;
import com.intershop.customization.migration.common.MigrationStep;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

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

    private String cartridgeName;
    public void migrate(Path cartridgeDir)
    {
        cartridgeName = getResourceName(cartridgeDir);
        LOGGER.info("Processing cartridge {}.", cartridgeName);

        for (Map.Entry<String, String> sourceEntry : sourceConfiguration.entrySet())
        {
            String artifactName = sourceEntry.getKey();
            Path sourcePath = cartridgeDir.resolve(sourceEntry.getValue());
            if (!sourcePath.toFile().exists())
            {
                LOGGER.debug("Can't find cartridges folder {}.", sourcePath);
                continue;
            }
            String targetPathAsString = targetConfiguration.get(artifactName);
            Path targetPath = cartridgeDir.resolve(targetPathAsString.replace("{cartridgeName}", cartridgeName));
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
                    LoggerFactory.getLogger(getClass()).warn("Folder {} exists.", targetPath);
                }
            }
            catch(IOException e)
            {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public String getCommitMessage()
    {
        return "refactor: Move static files of '" + cartridgeName + "' to src/main/resources";
    }
}
