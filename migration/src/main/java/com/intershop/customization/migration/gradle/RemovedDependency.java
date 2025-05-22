package com.intershop.customization.migration.gradle;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import com.intershop.customization.migration.common.MigrationPreparer;
import com.intershop.customization.migration.common.MigrationStep;
import com.intershop.customization.migration.common.Position;
import com.intershop.customization.migration.utils.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RemovedDependency implements MigrationPreparer
{
    private final Logger LOGGER = LoggerFactory.getLogger(getClass());

    private static final String YAML_KEY_REMOVED_DEPENDENCIES = "dependencies";
    private static final String START_DEPENDENCIES = "dependencies";
    private static final String LINE_SEP = System.lineSeparator();

    private List<String> removedDependencies = Collections.emptyList();

    @Override
    public void migrate(Path projectDir)
    {
        Path buildGradle = projectDir.resolve("build.gradle");
        try
        {
            List<String> lines = FileUtils.readAllLines(buildGradle);
            FileUtils.writeString(buildGradle, migrate(lines));
            LOGGER.info("build.gradle converted at {}.", projectDir);
        }
        catch(IOException e)
        {
            LOGGER.error("Can't convert build.gradle", e);
        }
    }

    @Override
    public void setStep(MigrationStep step)
    {
        this.removedDependencies = step.getOption(YAML_KEY_REMOVED_DEPENDENCIES);
    }
    /**
     * go step by step through migration steps to fix gradle build
     * @param lines lines to migrate
     * @return build.gradle content
     */
    String migrate(List<String> lines)
    {
        Position dependencyPos = Position.findBracketBlock(START_DEPENDENCIES, lines).orElse(Position.NOT_FOUND(lines));
        List<String> unknownLines = dependencyPos.nonMatchingLines();
        List<String> dependencyLines = dependencyPos.matchingLines();

        // build result
        StringBuilder result = new StringBuilder();
        // add all own known lines
        result.append(String.join(LINE_SEP, unknownLines)).append(LINE_SEP);
        // collect tasks for plugins
        // put dependencies to the end
        result.append(String.join(LINE_SEP, convertDependencyLines(dependencyLines))).append(LINE_SEP);
        return result.toString();
    }

    /**
     * @param lines original dependency lines without "header" and "footer"
     * @return converted lines
     */
    List<String> convertDependencyLines(List<String> lines)
    {
        return lines.stream().map(this::convertDependencyLine).filter(s -> !"(removed)".equals(s)).toList();
    }

    /**
     * @param depLine original dependency line
     * @return converted line
     */
    private String convertDependencyLine(String depLine)
    {
        if (depLine.trim().isEmpty())
        {
            return depLine.trim();
        }
        // use cartridge in case we assume it's a cartridge
        String[] parts = depLine.split("'");
        if (parts.length < 2)
        {
            return depLine;
        }
        // convert to standard gradle configurations
        String converted = depLine;
        if (removedDependencies.contains(parts[1]))
        {
            converted = "(removed)";
        }
        return converted;
    }
}
