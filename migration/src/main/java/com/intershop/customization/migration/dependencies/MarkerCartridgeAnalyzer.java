package com.intershop.customization.migration.dependencies;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.LoggerFactory;

import com.intershop.customization.migration.pfconfigurationfs.MigrateConfigResources;

/**
 * tHE 3rd step to examine the cartridge dependencies in a migration context.<br/>
 * 
 * This class analyzes marker cartridges in top-level cartridges based on a list of breadcrumb lines.
 * It checks if marker cartridges are used in top-level cartridges where they are not allowed.<br/>
 * A marker carteidge implements specific funtionality for an application, e.g. 
 * <code>com.intershop.business:smc</code> for the application <code>imntershop.SMC</code>. 
 * That's why it  must not appeare in te dependencies of the <code>intershop.EnterpriseBackoffice</code> PP:
 * 
 * The analysis is based on predefined properties files that map top-level cartridges to their allowed marker cartridges.
 */
public class MarkerCartridgeAnalyzer
{

    public static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(MigrateConfigResources.class);

    /** 
     * Analyzes marker cartridges in top-level cartridges based on breadcrumb lines
     * to check whether marker cartridges are used in the correct top-level cartridges
     * 
     * @param breadcrumbLines List of breadcrumb lines representing cartridge dependencies
     * where in the dependencies are listed top down 
     * as <cartridge> > <dependency1> > <dependency2> ...
     * 
     * @return Set of strings describing faulty assignments of marker cartridges
    */

    public static HashSet<String> analyzeMarkerCartridges(List<String> breadcrumbLines)
    {
        Map<String, Set<String>> cartridgeToAllDependencies = parseBreadcrumbDependencies(breadcrumbLines);
        
        Map<String, List<String>> appToTopLevelCartridges = parseResourceFile("cartridgedependencies/apps_top_level_cartridges.properties");
        Map<String, List<String>> topLevelToAllowedMarkers =parseResourceFile("cartridgedependencies/appmarker-cartridges.properties");

        return checkMarkerCartridges(appToTopLevelCartridges, topLevelToAllowedMarkers, cartridgeToAllDependencies);

    }

    /**
     * Checks for marker cartridges in top-level cartridges where they are not supposed to be.
     * 
     * @param appToTopLevelCartridges Map of top level application cartridges
     * @param topLevelToAllowedMarkers Map of top-level cartridge to its allowed marker cartridges
     * @param cartridgeToAllDependencies Map of cartridge to all its (recursive) dependencies
     * 
     * @return Set of strings describing faulty assignments of marker cartridges
     */
    public static HashSet<String>  checkMarkerCartridges(Map<String, List<String>> appToTopLevelCartridges,
                    Map<String, List<String>> topLevelToAllowedMarkers,
                    Map<String, Set<String>> cartridgeToAllDependencies)
    {
        HashSet<String> faultyAssignments = new HashSet<>();
        for (Map.Entry<String, List<String>> entry : appToTopLevelCartridges.entrySet())
        {
            String application = entry.getKey();
            // LOGGER.info("~~~ verify '{}' ...", application);
            for (String topLevelCartridge : entry.getValue())
            {
                Set<String> allDependencies = cartridgeToAllDependencies.getOrDefault(topLevelCartridge,
                                Collections.emptySet());
                List<String> allowedMarkers = topLevelToAllowedMarkers.getOrDefault(topLevelCartridge,
                                Collections.emptyList());

                for (String cartridge : allDependencies)
                {
                    // If dependency is a marker cartridge but not allowed for this top-level cartridge
                    if (isMarkerCartridge(cartridge, topLevelToAllowedMarkers) 
                    && !allowedMarkers.contains(cartridge)
                    && isAllowedInOtherTopLevel(cartridge, topLevelCartridge, topLevelToAllowedMarkers))
                    {
                        String fault = new StringBuilder()
                                        .append(" - Marker cartridge '")
                                        .append(cartridge)
                                        .append("' found in top-level cartridge '")
                                        .append(topLevelCartridge)
                                        .append("' (application: ")
                                        .append(application)
                                        .append(")")
                                        .toString();
                         if(!faultyAssignments.contains(fault)) 
                         {
                            faultyAssignments.add(fault);
                        }
                    }
                }
            }
        }
        return faultyAssignments;
    }

    /**
     * Checks if the given cartridge is in the allowed markers of any top-level dependency
     * except the specified one.
     *
     * @param cartridge The cartridge to check
     * @param currentTopLevel The top-level cartridge to exclude from the check
     * @param topLevelToAllowedMarkers The map of top-level cartridges to their allowed marker cartridges
     * @return true if the cartridge is allowed in any other top-level dependency, false otherwise
     */
    public static boolean isAllowedInOtherTopLevel
    (
        String cartridge,
        String currentTopLevel,
        Map<String, List<String>> topLevelToAllowedMarkers
    ) {
        for (Map.Entry<String, List<String>> entry : topLevelToAllowedMarkers.entrySet()) 
        {
            String topLevel = entry.getKey();
            if (!topLevel.equals(currentTopLevel) && entry.getValue().contains(cartridge)) 
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Determines if a cartridge is a marker cartridge by checking if it appears in any allowed marker list.
     * 
     * @param cartridge The cartridge to check
     * @param topLevelToAllowedMarkers The map of top-level cartridges to their allowed marker cartridges
     * @return true if the cartridge is a marker cartridge, false otherwise
     */
    private static boolean isMarkerCartridge(
        String cartridge, 
        Map<String, List<String>> topLevelToAllowedMarkers)
    {
        for (List<String> allowed : topLevelToAllowedMarkers.values())
        {
            //LOGGER.info("~~~ {} '{}' is in allowed list: {}", allowed.contains(cartridge), cartridge, allowed);
            if (allowed.contains(cartridge)) return true;
        }
        return false;
    }

    /**
     * resolve a resurce file and parses its content into a map
     * @ee parsePropertiesFile()<br/>
    * The resource file is located in the path 
     * <code>src/main/resources//</code>.<br/>
     * 
     * @param resourceName The name of the resource file to parse
     * @return the map containing the parsed key-value pairs
     */
    public static Map<String, List<String>> parseResourceFile(
        String resourceName
    )
    {

        Map<String, List<String>> map = new HashMap<>();
        ClassLoader classLoader = MarkerCartridgeAnalyzer.class.getClassLoader();
        try
        {
            URL resourceUrl = classLoader.getResource(resourceName);
            if (resourceUrl != null)
            {
                Path path = Paths.get(resourceUrl.toURI());
                map = parsePropertiesFile(path);
            }
            else
            {
                LOGGER.error("Resource {} not found", resourceName);
            }
        }
        catch (Exception e)
        {
            LOGGER.error("Error parsing resource file {}: {}", resourceName, e.getMessage());
        }
        return map;
    }

     /**
     * Parses a resource file and returns its content as a map.
     * <key>=[<value1>, <value2>, ...]<br/>
     * Comments lead by '#' and empty line sare ignored.
     * 
     * @param resourceName The name of the resource file to parse
     * @return the map containing the parsed key-value pairs
     */
    public static Map<String, List<String>> parsePropertiesFile(Path file)
    {
        Map<String, List<String>> map = new HashMap<>();
        try
        {
            List<String> lines = Files.readAllLines(file);
            for (String line : lines)
            {
                if (line.trim().isEmpty() || line.trim().startsWith("#")) continue;
                String[] parts = line.split("=", 2);
                if (parts.length == 2)
                {
                    String key = parts[0].trim();
                    List<String> values = Arrays.asList(parts[1].split(","));
                    map.put(key, values.stream().map(String::trim).filter(s -> !s.isEmpty()).toList());
                }
            }
        }
        catch(IOException e)
        {
            LOGGER.error("Error checking properties file existence: {}", e.getMessage());
        }
        return map;
    }
    
    /** 
     * Parses breadcrumb lines to extract cartridge dependencies a a list
     * 
     * @param breadcrumbLines List of breadcrumb lines representing cartridge dependencies
     * where in the dependencies are listed top down
     * as <cartridge> > <dependency1> > <dependency2> ...
     * @return Map of cartridge as key and a list of its its dependencies as value
    */
    public static Map<String, Set<String>> parseBreadcrumbDependencies(List<String> breadcrumbLines)
    {
        Map<String, Set<String>> cartridgeToAllDependencies = new HashMap<>();
        for (String line : breadcrumbLines)
        {
            if (line.trim().isEmpty() || line.trim().startsWith("#")) continue;
            String[] parts = Arrays.stream(line.split(">"))
                                   .map(String::trim)
                                   .filter(s -> !s.isEmpty())
                                   .toArray(String[]::new);
            if (parts.length < 2) continue; // No dependencies
            String topLevel = parts[0];
            Set<String> deps = cartridgeToAllDependencies.computeIfAbsent(topLevel, k -> new java.util.HashSet<>());
            // Add all dependencies except the top-level cartridge itself
            for (int i = 1; i < parts.length; i++)
            {
                deps.add(parts[i]);
            }
        }
        return cartridgeToAllDependencies;
    }

}
