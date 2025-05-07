package com.intershop.customization.migration.gradle;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.intershop.customization.migration.common.MigrationPreparer;
import com.intershop.customization.migration.common.Position;

public class UpdateGradleBuild7to10 implements MigrationPreparer
{
    private final Logger LOGGER = LoggerFactory.getLogger(getClass());
    private static final Charset CHARSET_BUILD_GRADLE = Charset.defaultCharset();
    private static final String START_DEPENDENCIES = "dependencies";
    private static final String START_INTERSHOP = "intershop";

    private static final String LINE_SEP = System.lineSeparator();
    private static final Predicate<String> IS_OLD_PLUGIN_LINE = (l) -> {
        return l.contains("apply plugin");
    };

    private static final Map<String, String> REPLACE_LINES = Map.of(
                    "zipCartridge.dependsOn lessCompile", "tasks.compileJava.dependsOn(tasks.lessCompile)"
    );
    private static final Map<String, String> PLUGIN_TASK = Map.of(
                    "com.intershop.gradle.isml", "tasks.test.dependsOn(tasks.isml)"
                );

    /**
     * Plugins can be removed
     */
    private static final List<String> PLUGIN_REMOVED = List.of("static-cartridge");

    /**
     * Plugins mapped
     */
    private static final Map<String, String> PLUGIN_MAP = Map.of(
                    "java-cartridge", "java",
                    "javabase-cartridge", "java",
                    "java-component", "java",
                    "ish-assembly", "java",
                    "ish-assembly-branding", "java"
                    );
    /**
     * Plugins leave at it is
     */
    private static final List<String> PLUGIN_UNTOUCHED = Arrays.asList(
                    "com.intershop.gradle.cartridge-resourcelist",
                    "com.intershop.gradle.isml");
    /**
     * Plugins added/replaced
     */
    private static final Map<String, List<String>> PLUGIN_ADDED = Map.of(
                    "java-cartridge", Arrays.asList("com.intershop.icm.cartridge.product", "java"),
                    "javabase-cartridge", Arrays.asList("com.intershop.icm.cartridge.product", "java"),
                    "static-cartridge", Arrays.asList("com.intershop.icm.cartridge.product", "java"),
                    "test-cartridge", Arrays.asList("com.intershop.icm.cartridge.test", "java")
                    );
    private String cartridgeName;

    public void migrate(Path projectDir)
    {
        cartridgeName = getResourceName(projectDir);

        Path buildGradle = projectDir.resolve("build.gradle");
        try
        {
            List<String> lines = Files.lines(buildGradle, CHARSET_BUILD_GRADLE).toList();
            String newContent = migrate(lines);
            Files.write(buildGradle, newContent.getBytes(CHARSET_BUILD_GRADLE));
        }
        catch(IOException e)
        {
            LOGGER.error("Can't convert build.gradle", e);
        }
    }

    /**
     * go step by step through migration steps to fix gradle build
     * 
     * @param lines all lines
     * @return build.gradle content
     * <li>list of plugins
     * <li>intershop block
     * <li>untouched lines
     * <li>new required tasks
     * <li>dependencies
     */
    String migrate(List<String> lines)
    {
        debug("all", lines);
        // collect "semantic" known lines
        List<String> pluginLines = filterPluginLines(lines);
        List<String> newPlugins = mapPluginLines(pluginLines);
        List<String> unknownLines = notIn(lines, pluginLines);
        debug("pluginLines", pluginLines);
        debug("unknownAfterPlugin", unknownLines);
        Position dependencyPos = Position.findBracketBlock(START_DEPENDENCIES,unknownLines).orElse(Position.NOT_FOUND(unknownLines));
        List<String> dependencyLines = dependencyPos.matchingLines();
        unknownLines = dependencyPos.nonMatchingLines();
        debug("dependencyLines", dependencyLines);
        debug("unknownAfterDependency", unknownLines);
        Position intershopBlockPos = Position.findBracketBlock(START_INTERSHOP, unknownLines).orElse(Position.NOT_FOUND(unknownLines));
        List<String> intershopLines = intershopBlockPos.matchingLines();
        unknownLines = intershopBlockPos.nonMatchingLines();
        debug("intershopLines", intershopLines);
        debug("unknownAfterIntershop", unknownLines);
        unknownLines = mapNewTasksForOldTasks(unknownLines);
        debug("unknownMap", unknownLines);

        // build result
        StringBuilder result = new StringBuilder();
        // add plugins
        result.append(joinPlugins(newPlugins)).append(LINE_SEP);
        // add intershop block (descriptions)
        result.append(intershopBlock(intershopLines)).append(LINE_SEP);
        // add all own known lines
        result.append(String.join(LINE_SEP, unknownLines)).append(LINE_SEP);
        // collect tasks for plugins
        result.append(joinTasksForNewPlugins(newPlugins)).append(LINE_SEP);
        // put dependencies to the end
        result.append(migrateDependencies(dependencyLines));
        return result.toString();
    }

    /**
     * @param newPlugins of project
     * @return content for required tasks by given new plugins
     */
    private String joinTasksForNewPlugins(List<String> newPlugins)
    {
        if (newPlugins.isEmpty())
        {
            return "";
        }
        List<String> tasks = new ArrayList<>(); 
        for(String plugin : newPlugins)
        {
            if (PLUGIN_TASK.containsKey(plugin))
            {
                tasks.add(PLUGIN_TASK.get(plugin));
            }
        }
        return String.join(LINE_SEP, tasks) + LINE_SEP;
    }

    /**
     * @param lines all lines
     * @return lines which contains replaced tasks, if there was an mapping at REPLACE_LINES
     */
    private List<String> mapNewTasksForOldTasks(List<String> lines)
    {
        List<String> tasks = new ArrayList<>(); 
        for(String line : lines)
        {
            String replacement = REPLACE_LINES.get(line.trim());
            tasks.add(replacement != null ? replacement : line);
        }
        return tasks;
    }

    private void debug(String step, List<String> lines)
    {
        int pos = 0;
        for (String line : lines)
        {
            LOGGER.trace("d:{} {} Line: {}", pos++, step, line);
        }
    }

    /**
     * @param lines of intershop block
     * @return description from intershop extension block
     */
    private String intershopBlock(List<String> lines)
    {
        String description = null;
        for (String line : lines)
        {
            // displayName = 'Application - Responsive Starter Store'
            if (line.contains("displayName"))
            {
                String[] parts = line.split("'");
                description = parts[1];
            }
        }
        if (description != null)
        {
            return "description = '" + description + "'" + LINE_SEP;
        }
        return "";
    }

    /**
     * Migrate dependency lines (starting with "dependencies {") ends with "}"
     * @param lines all lines
     * @return content for new dependencies
     */
    private String migrateDependencies(List<String> lines)
    {
        if (lines.isEmpty())
        {
            return "";
        }
        StringBuilder b = new StringBuilder();
        b = b.append(lines.get(0)).append(LINE_SEP);
        int start = 1;
        while(lines.get(start).trim().isEmpty() || lines.get(start).trim().equals("{"))
        {
            if (!lines.get(start).trim().isEmpty())
            {
                b = b.append(lines.get(start).trim()).append(LINE_SEP);
            }
            start++;
        }
        for (int i = start; i < lines.size() - 1; i++)
        {
            String converted = convertDependencyLine(lines.get(i)).trim();
            if (!converted.isEmpty())
            {
                b = b.append("    ").append(converted);
            }
            b = b.append(LINE_SEP);
        }
        b = b.append("}").append(LINE_SEP);
        return b.toString();
    }

    /**
     * @param lines original dependency lines without "header" and "footer"
     * @return converted lines
     */
    List<String> convertDependencies(List<String> lines)
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
        // convert to standard gradle configurations
        String converted = depLine.replace("compile", "implementation")
                                  .replace("runtime", "runtimeOnly")
                                  .replace("runtimeOnlyOnly", "runtimeOnly")
                                  .replace("testCompile", "testImplementation")
                                  .replace("testRuntime", "testRuntimeOnly")
                                  .replace("testRuntimeOnlyOnly", "testRuntimeOnly")
                                  .trim();
        if (converted.contains("group:") && !converted.contains("exclude group:"))
        {
            String[] partsImpl = converted.split("group:");
            String[] partsDep = converted.split("'");
            if (partsDep.length > 3)
            {
                converted = partsImpl[0] + "'" + partsDep[1] + ":" + partsDep[3] + "'";
            }
            else
            {
                LoggerFactory.getLogger(getClass()).warn("Can't convert group dependency '{}'", depLine);
            }
        }
        return converted.trim();
    }

    /**
     * @param lines all lines
     * @param filtered must be unique otherwise the line is removed twice
     * @return lines without filtered lines
     */
    private List<String> notIn(List<String> lines, List<String> filtered)
    {
        return lines.stream().filter(s -> !filtered.contains(s)).toList();
    }

    /**
     * @param lines all lines
     * @return lines with "plugin apply:"
     */
    private List<String> filterPluginLines(List<String> lines)
    {
        return lines.stream().filter(IS_OLD_PLUGIN_LINE).toList();
    }

    /**
     * @param plugins list of plugins to join
     * @return string for build.gradle block of defined plugins
     */
    private String joinPlugins(List<String> plugins)
    {
        if (plugins.isEmpty())
        {
            return "";
        }
        StringBuilder b = new StringBuilder();
        b.append("plugins {").append(LINE_SEP);
        for (String plugin : plugins)
        {
            b.append("    id \'" + plugin + "\'\n");
        }
        b.append("}").append(LINE_SEP);
        return b.toString();
    }

    List<String> mapPluginLines(List<String> lines)
    {
        return mapPlugins(lines.stream().map(this::extractPluginFromLine).toList());
    }

    List<String> mapPlugins(List<String> oldPlugins)
    {
        Set<String> result = new HashSet<>();
        for (String existingPlugin : oldPlugins)
        {
            boolean processedPlugin = false;
            if (PLUGIN_MAP.containsKey(existingPlugin))
            {
                result.add(PLUGIN_MAP.get(existingPlugin));
                processedPlugin = true;
            }
            if (PLUGIN_UNTOUCHED.contains(existingPlugin))
            {
                result.add(existingPlugin);
                processedPlugin = true;
            }
            if (PLUGIN_REMOVED.contains(existingPlugin))
            {
                processedPlugin = true;
                // ignore
            }
            if (PLUGIN_ADDED.containsKey(existingPlugin))
            {
                result.addAll(PLUGIN_ADDED.get(existingPlugin));
                processedPlugin = true;
            }
            if (!processedPlugin)
            {
                result.add(existingPlugin);
                LOGGER.warn("Unknown plugin '{}' was added.", existingPlugin);
            }
        }
        return result.stream().sorted().toList();
    }

    private String extractPluginFromLine(String line)
    {
        String[] parts = line.split("'");
        if (parts.length == 1) parts = line.split("\"");
        return parts[1];
    }

    @Override
    public String getCommitMessage()
    {
        return "refactor: upgrade '" + cartridgeName + "' to newer gradle build system";
    }
}
