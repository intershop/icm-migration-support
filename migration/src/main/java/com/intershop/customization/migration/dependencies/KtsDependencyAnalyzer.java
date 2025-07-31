package com.intershop.customization.migration.dependencies;

import java.io.File;
// Import necessary Kotlin scripting libraries
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.intershop.customization.migration.common.Position;
import com.intershop.customization.migration.utils.FileUtils;

/**
 * * This class analyzes Kotlin script (.kts) files to extract dependencies.
 */
public class KtsDependencyAnalyzer
{

    public static final String MARK_EXCLUDED_DEPENDENCY = "(excl.)";

    Path startDir = null;

    private static final String START_DEPENDENCIES = "dependencies";

    /**
     * * Parses a Kotlin script (.kts) file to extract dependencies.
     * 
     * @param buildGradle
     * @return List of Dependency objects representing the dependencies found in the file.
     */
    public List<Dependency> parseKtsFile(Path buildGradle)
    {
        if (startDir == null)
        {
            startDir = buildGradle.getParent();
        }

        List<Dependency> depndencies = new ArrayList<>();
        try
        {
            List<String> lines = FileUtils.readAllLines(buildGradle);

            Position dependencyPos = Position.findBracketBlock(START_DEPENDENCIES, lines)
                                             .orElse(Position.NOT_FOUND(lines));
            List<String> dependencyLines = dependencyPos.matchingLines();
            if (!dependencyLines.isEmpty())
            {
                // Remove the first line which is the "dependencies" declaration
                dependencyLines.remove(0);
                // Remove any trailing closing bracket or semicolon
                if (dependencyLines.get(dependencyLines.size() - 1).trim().endsWith("}"))
                {
                    dependencyLines.remove(dependencyLines.size() - 1);
                }
                for (String line : dependencyLines)
                {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("//"))
                    {
                        String aLine[] = line.trim().split("\"");
                        if (aLine.length == 3)
                        {
                            String prefix = aLine[0].trim();
                            String dependencySSubject = aLine[1].trim();
                            DependencyType dependencyType = DependencyType.UNKNOWN;

                            if(prefix.startsWith("exclude"))
                            {
                                dependencySSubject = "    " 
                                + MARK_EXCLUDED_DEPENDENCY + dependencySSubject;
                                dependencyType = DependencyType.CARTRIDGE;
                            }   
                            else if (line.startsWith("implementation(\"") && line.endsWith("{"))
                            {   
                                dependencySSubject = aLine[1].trim();
                                dependencyType = DependencyType.CARTRIDGE;
                            }
                            else if (prefix.startsWith("implementation(") || prefix.startsWith("cartridge(")
                                || prefix.startsWith("cartridgeRuntime(project("))
                            {
                                dependencyType = DependencyType.CARTRIDGE;
                            }
                            else if (prefix.startsWith("runtimeOnly(") || prefix.startsWith("cartridgeRuntime("))
                            {
                                // cartridgeRuntime( ... ) or runtimeOnly( ... )
                                dependencyType = DependencyType.PACKAGE;
                            }

                            // Parse the dependency line and create a Dependency object
                            if (!dependencySSubject.isEmpty())
                            {
                                Dependency dependency = new Dependency(
                                    chopCartridgeName(dependencySSubject),
                                    buildGradle.getFileName().toString(),
                                    dependencyType);

                                depndencies.add(dependency);
                            }

                        }
                    }
                }
            }
            else
            {
                System.out.println("No dependencies found in the file: " + buildGradle.toString());
            }
        }
        catch(Exception e)
        {
            System.err.println("Error parsing KTS file: " + e.getMessage());
        }

        return depndencies;
    }

    /**
     * If the cartridge name starts with a ":" it is removed,
     * because it is not part of the directory name.<br/>
     *  
     * @param cartridgeName the cartridge name to check
     * @return the cartridge name without leading ":" if it exists
     */
    private static String chopCartridgeName(String cartridgeName)
    {
        if( cartridgeName.startsWith(":") && cartridgeName.length() > 2)
        {
            cartridgeName = cartridgeName.substring(1);
        }
        return cartridgeName;
    }

}
