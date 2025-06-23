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
public class KtsDependencyAnalyzer {


    private static final String START_DEPENDENCIES = "dependencies";

    /** * Parses a Kotlin script (.kts) file to extract dependencies.
     * 
     * @param buildGradle   
     * @return List of Dependency objects representing the dependencies found in the file.
     */
    public List<Dependency> parseKtsFile(Path buildGradle) 
    {     
        List<Dependency> delendencies = new ArrayList<>();
        try
        {
            List<String> lines = FileUtils.readAllLines(buildGradle);

            Position dependencyPos = Position.findBracketBlock(START_DEPENDENCIES,lines).orElse(Position.NOT_FOUND(lines));
            List<String> dependencyLines = dependencyPos.matchingLines();
            if (dependencyLines.size() > 0 && !dependencyLines.isEmpty())
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
                    if(!line.isEmpty() && !line.startsWith("//"))
                    {
                        String aLine[] = line.trim().split("\"");
                        if (aLine.length == 3)
                        {
                            String prefix = aLine[0].trim();
                            String dependencySSubject = aLine[1].trim();;
                                if(dependencySSubject.startsWith(":"))
                                {
                                    // cartridge(project( ... ) . remove the ":"
                                    dependencySSubject = dependencySSubject.substring(1);
                                }

                                DependencyType dependencyType = DependencyType.UNKNOWN;
                            if(prefix.startsWith("implementation(") 
                            || prefix.startsWith("cartridge(")
                            || prefix.startsWith("cartridgeRuntime(project("))
                            {
                                // cartridge( ... ) or implementation( ... )
                                dependencyType = DependencyType.CARTRIDGE;
                            }
                            else if(prefix.startsWith("runtimeOnly(") ||
                             prefix.startsWith("cartridgeRuntime("))
                            {
                                // cartridgeRuntime( ... ) or runtimeOnly( ... )
                                dependencyType = DependencyType.PACKAGE;
                            }

                            // Parse the dependency line and create a Dependency object
                            if(!dependencySSubject.isEmpty())
                            {
                                Dependency dependency = new Dependency(
                                    dependencySSubject, 
                                    buildGradle.getFileName().toString(), 
                                    dependencyType);
                                delendencies.add(dependency);
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
        catch (Exception e)
        {
            System.err.println("Error parsing KTS file: " + e.getMessage());
        }
        return delendencies;


    }
    
}
