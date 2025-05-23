package com.intershop.customization.migration.gradle;

import static com.intershop.customization.migration.common.MigrationContext.OperationType.MODIFY;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.intershop.customization.migration.common.MigrationContext;
import com.intershop.customization.migration.common.MigrationPreparer;
import com.intershop.customization.migration.common.MigrationStep;
import com.intershop.customization.migration.common.Position;
import com.intershop.customization.migration.utils.FileUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This migrator replaces the configured old dependencies in the 'build.gradle' files by their new
 * group/artifact name.
 * <p>
 * Example YAML configuration:
 * <pre>
 * type: specs.intershop.com/v1beta/migrate
 * migrator: com.intershop.customization.migration.gradle.RenamedDependency
 * message: "refactor: rename apache dependencies in build.gradle"
 * options:
 *   dependency-map:
 *     commons-lang:commons-lang: org.apache.commons:commons-lang3
 * </pre>
 */
public class RenamedDependency implements MigrationPreparer
{
    private final Logger LOGGER = LoggerFactory.getLogger(getClass());

    private static final String YAML_KEY_RENAMED_DEPENDENCY = "dependency-map";
    private static final String START_DEPENDENCIES = "dependencies";
    private static final String LINE_SEP = System.lineSeparator();

    private Map<String, String> renamedDependencies = Collections.emptyMap();

    @Override
    public void migrate(Path projectDir, MigrationContext context)
    {
        Path buildGradle = projectDir.resolve("build.gradle");
        String cartridgeName = getResourceName(projectDir);
        try
        {
            List<String> lines = FileUtils.readAllLines(buildGradle);

            FileUtils.writeString(buildGradle, migrate(lines));
            LOGGER.info("build.gradle converted at {}.", projectDir);
            context.recordSuccess(cartridgeName, MODIFY, buildGradle, buildGradle);
        }
        catch (IOException e)
        {
            LOGGER.error("Can't convert build.gradle", e);
            context.recordFailure(cartridgeName, MODIFY, buildGradle, buildGradle,
                    "Can't convert build.gradle: " + e.getMessage());
        }
    }

    @Override
    public void setStep(MigrationStep step)
    {
        this.renamedDependencies = step.getOption(YAML_KEY_RENAMED_DEPENDENCY);
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

        // build result: add all own known lines for plugins, put dependencies to the end
        return String.join(LINE_SEP, unknownLines)
                        + LINE_SEP
                        + String.join(LINE_SEP, convertDependencyLines(dependencyLines))
                        + LINE_SEP;
    }

    /**
     * @param lines original dependency lines without "header" and "footer"
     * @return converted lines
     */
    List<String> convertDependencyLines(List<String> lines)
    {
        return lines.stream().map(this::convertDependencyLine).toList();
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
        if (renamedDependencies.containsKey(parts[1]))
        {
            converted = converted.replace(parts[1], renamedDependencies.get(parts[1]));
        }
        return converted;
    }
}
