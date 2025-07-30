package com.intershop.customization.migration.dependencies;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
                List<String> normalized = cycle.stream().sorted().toList();
                cycles.add(normalized);
            } else if (!visited.contains(neighbor)) {
                findCycles(start, neighbor, visited, path, graph, cycles);
            }
        }

        path.removeLast();
        visited.remove(current);
    }

}
