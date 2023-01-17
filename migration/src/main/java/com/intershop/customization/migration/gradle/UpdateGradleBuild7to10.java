package com.intershop.customization.migration.gradle;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.intershop.customization.migration.common.Position;

public class UpdateGradleBuild7to10
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
     * Local execution to convert one single build.gradle
     * 
     * @param args
     *            <li>fileName - path to project directory, which contains build.gradle</li>
     */
    public static void main(String[] args)
    {
        UpdateGradleBuild7to10 migrator = new UpdateGradleBuild7to10();
        migrator.migrate(new File(args[0]).toPath());
    }

    public void migrate(Path projectDir)
    {
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
     * @param lines
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
        debug("unknowAfterPlugin", unknownLines);
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
        result = result.append(joinPlugins(newPlugins)).append(LINE_SEP);
        // add intershop block (descriptions)
        result = result.append(intershopBlock(intershopLines)).append(LINE_SEP);
        // add all own known lines
        result = result.append(String.join(LINE_SEP, unknownLines)).append(LINE_SEP);
        // collect tasks for plugins
        result = result.append(joinTasksForNewPlugins(newPlugins)).append(LINE_SEP);
        // put dependencies to the end
        result = result.append(migrateDependencies(dependencyLines));
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
     * @param lines
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
            LOGGER.debug("d:{} {} Line: {}", pos++, step, line);
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
     * @param lines
     * @return content for new dependencies
     */
    private String migrateDependencies(List<String> lines)
    {
        StringBuilder b = new StringBuilder();
        b = b.append("// TODO please validate that cartridges have \"cartridge\" as dependency declaration instead of \"implementation\".").append(LINE_SEP);
        b = b.append(lines.get(0)).append(LINE_SEP);
        convertDependencies(lines.subList(1, lines.size() - 1));
        for (int i = 1; i < lines.size() - 1; i++)
        {
            String converted = convertDependencyLine(lines.get(i));
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
     * @param lines original dependency line
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
                                  .replace("testCompile", "testImplementation")
                                  .replace("testRuntime", "testRuntimeOnly")
                                  .trim();
        if (converted.contains("group:"))
        {
            String[] partsImpl = converted.split("group:");
            String[] partsDep = converted.split("'");
            converted = partsImpl[0] + "'" + partsDep[1] + ":" + partsDep[3] + "'";
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
     * @param plugins
     * @return string for build.gradle block of defined plugins
     */
    private String joinPlugins(List<String> plugins)
    {
        StringBuilder b = new StringBuilder();
        b = b.append("plugins {").append(LINE_SEP);
        for (String plugin : plugins)
        {
            b = b.append("    id \'" + plugin + "\'\n");
        }
        b.append("}").append(LINE_SEP);
        return b.toString();
    }

    /**
     * Plugins can be removed
     */
    private static final List<String> PLUGIN_REMOVED = Arrays.asList("static-cartridge");

    /**
     * Plugins mapped
     */
    private static final Map<String, String> PLUGIN_MAP = Map.of("java-cartridge", "java");
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
                    "java-cartridge", Arrays.asList("com.intershop.icm.cartridge.product", "com.intershop.icm.cartridge.external"
                                    )
                    );

    List<String> mapPluginLines(List<String> lines)
    {
        return mapPlugins(lines.stream().map(this::extractPluginFromLine).toList());
    }

    List<String> mapPlugins(List<String> oldPlugins)
    {
        List<String> result = new ArrayList<>();
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
                LOGGER.warn("Unknow plugin '{}' was added.", existingPlugin);
            }
        }
        result.sort((a, b) -> a.compareTo(b));
        return result;
    }

    private String extractPluginFromLine(String line)
    {
        String[] parts = line.split("'");
        if (parts.length == 1) parts = line.split("\"");
        return parts[1];
    }

}
