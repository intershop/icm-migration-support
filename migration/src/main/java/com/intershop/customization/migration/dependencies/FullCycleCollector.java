package com.intershop.customization.migration.dependencies;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.LoggerFactory;

import com.intershop.customization.migration.pfconfigurationfs.MigrateConfigResources;

/**
 * FullCycleCollector is a utility class that detects cycles in a directed graph
 * represented by a list of strings. Each string should be in the format "from > to",
 * where "from" is the starting node and "to" is the destination node.
 * 
 * This class provides methods to build a directed graph from the input strings,
 * detect cycles using depth-first search, and normalize cycles to avoid duplicates.
 *
 * <p/> It is useful for analyzing dependencies in a migration context, where cycles
 * can indicate circular dependencies between components or cartridges.<br/>
 * The <code>cartridgeCrumbs<code> collected by the <code>analyzeBuildFile(9<code>) 
 * method of the class @see KtsDependencyAnalyzer are gathered thiy way when building
 * a dependency tree.
 *  * 
 * The main method `hasCycles` returns true if cycles are found, and false otherwise.
 * 
 * createy with the help of Copoilot and ChatGPT.
 *  */
public class FullCycleCollector 
{

    public static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(MigrateConfigResources.class);

    /**
     * dependency bread crumbs for analysis<br/>
     * NOTE: The environment variable <code>TEMP</code> must be declard, 
     * it is usually is set by the system.<br/>
     * if not, the current path the tool is called from is used.
     */
    public static final String BREADCRUMBS_FILE_NAME = "dependencyBreadCrumb.txt";
    private static  Path dependencyBreadCrumbsFile = Paths.get(BREADCRUMBS_FILE_NAME);
    {
        if(! System.getenv("TEMP").isEmpty())
        {
        Path dependencyBreadCrumbsFile = 
            Paths.get(System.getenv("TEMP")+File.separator +BREADCRUMBS_FILE_NAME);
        }
        else {
            // current path
            Path dependencyBreadCrumbsFile = 
                Paths.get("").toAbsolutePath().normalize().resolve(BREADCRUMBS_FILE_NAME);
        }
    }

    /**
     * Detects cycles in a directed graph represented by a list of strings.
     * Each string should be in the format "from > to", where "from" is the starting node
     * 
     * @param lines List of strings representing edges in the graph.
     * @return  true if cycles are found, false otherwise.
     */
    public static boolean hasCycles(List<String> lines)
    {
        Map<String, List<String>> graph = buildGraph(lines);
        Set<List<String>> cycles = new java.util.HashSet<>();

        for (String start : graph.keySet()) {
            Set<String> visited = new java.util.HashSet<>();
            List<String> path = new ArrayList<>();
            findCycles(start, start, visited, path, graph, cycles);
        }

        if (cycles.isEmpty()) 
		{
            LOGGER.info("No cycles found.");
        } 
		else 
		{
            LOGGER.info("Cycles found:");
            for (List<String> cycle : cycles) 
			{
                LOGGER.info(String.join(" -> ", cycle));
            }
        }

        return !cycles.isEmpty();
    }

    /**
     * Builds a directed graph from the given lines.
     * Each line should be in the format "from > to".
     *
     * @param lines List of strings representing edges in the graph.
     * @return A map representing the directed graph.
     */
    public static Map<String, List<String>> buildGraph(List<String> lines) 
	{
        Map<String, List<String>> graph = new HashMap<>();
        for (String line : lines) {
            String[] parts = line.split(">");
            if (parts.length >= 2) {
                String from = parts[0].trim();
                String to = parts[1].trim();
                graph.computeIfAbsent(from, k -> new ArrayList<>()).add(to);
            }
        }
        return graph;
    }

    /**
     * Recursively finds cycles in the graph starting from the given node.
     * This method uses depth-first search to explore paths and detect cycles.
     * 
     * @param start The starting node of the cycle.
     * @param current The current node being visited.
     * @param visited A set of visited nodes to avoid revisiting.
     * @param path The current path being explored.
     * @param graph The directed graph represented as a map.
     * @param cycles A set to store found cycles.
     */
    public static void findCycles(	String start, 
									String current, 
									Set<String> visited, 
									List<String> path,
									Map<String, List<String>> graph, 
									Set<List<String>> cycles) 
	{
        visited.add(current);
        path.add(current);

        for (String neighbor : graph.getOrDefault(current, Collections.emptyList())) {
            if (neighbor.equals(start)) {
                // Found a cycle, add a copy of the path + start
                List<String> cycle = new ArrayList<>(path);
                cycle.add(start);
                // Normalize cycle to avoid duplicates (start from smallest lexicographically)
                List<String> normalized = normalizeCycle(cycle);
                cycles.add(normalized);
            } else if (!visited.contains(neighbor)) {
                findCycles(start, neighbor, visited, path, graph, cycles);
            }
        }

        path.removeLast();
        visited.remove(current);
    }

    /**
     * Checks if there is are mutual dependencies between two cartridges across all 
     * dependency paths<br/>
     * back ground: cartridges are considered as nodes in a directed graph. Dependencies
     * are represented as edges in the graph.<br/>
     * 
     * @param dependencyPaths a list of cartridge dependency paths "cartridge1 > cartridge2 > ..."
     * @return true if there is a mutual dependency between the two cartridges, false otherwise.
     */
    public static boolean hasMutualGraphDependency(List<String> dependencyPaths)
    {
        // list to hold all cartridge names from the dependency paths
        LinkedHashSet<String> allCartridges = new LinkedHashSet<>();
        for (String crumb : dependencyPaths) {
            String[] cartridges = crumb.split("\\s*>\\s*");
            allCartridges.addAll(Arrays.asList(cartridges));
        }
        List<String> uniqueCartridges = new ArrayList<>(allCartridges);

        // go through all cartridges and check if there is a mutual dependency
        boolean mutual = false;
        for (int i = 0; i < uniqueCartridges.size(); i++) {
            for (int j = i + 1; j < uniqueCartridges.size(); j++) {
                String node1 = uniqueCartridges.get(i);
                String node2 = uniqueCartridges.get(j);
                if(!node1.equals(node2))
                {
                    if (hasMutualGraphDependency(dependencyPaths, node1, node2)) {
                        LOGGER.info("Mutual dependency found between {} and {}", node1, node2);
                        mutual = true;
                    }
                }
            }
        }
        return mutual;
    }

     /** * Normalizes a cycle by rotating it to start from the lexicographically smallest node.
     * This ensures that cycles are represented in a consistent manner, avoiding duplicates.
     * For example, the cycle ["A", "B", "C"] will be normalized to ["A", "B", "C"],
     * while ["B", "C", "A"] will also be normalized to ["A", "B", "C"].
     * This method is useful for ensuring that cycles are stored in a canonical form,
     * making it easier to compare and detect unique cycles.
     *
     * @param cycle List of strings representing a cycle.
     * @return   A normalized list of strings representing the cycle,
     */
    protected static List<String> normalizeCycle(List<String> cycle)
       {
        int n = cycle.size();
        int minIdx = 0;
        for (int i = 1; i < n; i++) {
            if (cycle.get(i).compareTo(cycle.get(minIdx)) < 0) {
                minIdx = i;
            }
        }
        List<String> normalized = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            normalized.add(cycle.get((minIdx + i) % n));
        }
        return normalized;
    }

    /**     
     * removes the dependency bread crumbs file.<br/>
     * This method is used to clean up the file after the migration process is complete.<br
     */
    static void remobeBreadCrumbsFile() 
    {
        try 
        {
            if(Files.exists(dependencyBreadCrumbsFile)) 
            {
                Files.delete(dependencyBreadCrumbsFile);
            }
        } catch (IOException e) 
        {
            LOGGER.error("Error deleting dependency bread crumbs file: {}", e.getMessage());
        }
    }

    /**
     *  loads the saved bread crumbs from the file.<br/>
     *  If the file does not exist, an empty list is returned.<br/>
     * @return a list of saved bread crumbs, which are the cartridge names in the dependency path.<br/>
     */
    static List<String> loadSavedBreadCrumbs() 
    {
        ArrayList<String> cartridgeCrumbs = new ArrayList<>();
        try 
        {
            if(!Files.exists(dependencyBreadCrumbsFile)) 
            {
                Files.createFile(dependencyBreadCrumbsFile);
            }
            else
            {
                cartridgeCrumbs = new ArrayList<>(Files.readAllLines(dependencyBreadCrumbsFile));
            }
        } catch (IOException e) 
        {
            LOGGER.error("Error reading dependency bread crumbs file: {}- returning empty list, nothing to work with.", e.getMessage());
        }
        cartridgeCrumbs.removeIf(String::isEmpty); // Remove empty lines

        return cartridgeCrumbs.stream().sorted().distinct().toList();
    }

    /**
     * stores the cartridge crumbs in the dependency bread crumbs file.<br/>
     * The crumbs are the names of the cartridges in the dependency path.<br/>
     * If null or empty, nothing is stored.<br/>
     * 
     * @param cartridgeCrumbs   the list of cartridge names in the dependency path.<br/>
     */
    static void storeCartridgeAssignmentsCrumbs(List<String> cartridgeCrumbs) {
        // dependencyBreadCrumbsFile
        if (cartridgeCrumbs != null && !cartridgeCrumbs.isEmpty())
        {
            for(String line : cartridgeCrumbs)
            {
                try
                {
                    Files.writeString(dependencyBreadCrumbsFile, line + System.lineSeparator(),
                                    java.nio.file.StandardOpenOption.CREATE, 
                                    java.nio.file.StandardOpenOption.APPEND);
                }
                catch(IOException e)
                {
                    LOGGER.error("Error writing to dependency bread crumbs file {}: {}, ", 
                                    dependencyBreadCrumbsFile.getFileName().toString(), e.getMessage());
                }
            }
        }
    }

    /**
     * Checks if there is a path from the start cartridge to 
     * the target cartridge in the graph.
     * 
     * @param graph the cartridge dependency path list (directed graph) represented as a map
     * @param start the starting cartridge name 
     * @param target the target cartridge name
     */
    protected static boolean isReachable(
        Map<String, List<String>> graph, 
        String start, 
        String target
    )
    {
        if (start.equals(target)) return true;
        Set<String> visited = new java.util.HashSet<>();
        java.util.Deque<String> stack = new java.util.ArrayDeque<>();
        stack.push(start);
        while (!stack.isEmpty()) 
        {
            String node = stack.pop();
            if (node.equals(target)) return true;
            if (visited.add(node)) 
            {
                for (String neighbor : graph.getOrDefault(node, List.of())) {
                    stack.push(neighbor);
                }
            }
        }
        return false;
    }

    /**
     * Checks if cartridge 1 is reachable from cartridge 2 and vice versa in the dependency parh.
     * 
     * @param paths the list of cartridge dependency paths
     * @param node1 the name of the first cartridge
     * @param node2 the name of the second cartridge
     */
    protected static boolean hasMutualGraphDependency(List<String> paths, String node1, String node2)
    {
        // Build the graph from all paths
        Map<String, List<String>> graph = new HashMap<>();
        for (String path : paths) 
        {
            String[] nodes = path.split("\\s*>\\s*");
            for (int i = 0; i < nodes.length - 1; i++) 
            {
                String from = nodes[i];
                String to = nodes[i + 1];
                graph.computeIfAbsent(from, k -> new ArrayList<>()).add(to);
            }
        }
        return isReachable(graph, node1, node2) && isReachable(graph, node2, node1);
    }

}
