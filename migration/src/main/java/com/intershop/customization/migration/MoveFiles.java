package com.intershop.customization.migration;

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

    @Override
    public String getCommitMessage()
    {
        return "refactor: Move static files of '" + cartridgeName + "' to src/main/resources";
    }
}
