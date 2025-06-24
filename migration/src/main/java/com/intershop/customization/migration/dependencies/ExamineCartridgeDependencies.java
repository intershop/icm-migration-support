package com.intershop.customization.migration.dependencies;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.FileVisitOption;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intershop.customization.migration.common.MigrationContext;
import com.intershop.customization.migration.common.MigrationPreparer;
import com.intershop.customization.migration.common.MigrationStep;
import com.intershop.customization.migration.pfconfigurationfs.MigrateConfigResources;

// import nonapi.io.github.classgraph.utils.StringUtils;
import org.apache.commons.lang3.StringUtils;

public class ExamineCartridgeDependencies implements MigrationPreparer
{
    /**
     * YAML key for tree format option JSON and TEXT are supported.<br/>
     */
    private static final String YAML_KEY_TREE_FORMAT = "treeFormat";
    private String treeFormat = "TEXT"; // default format

    /**
     * YAML key of the output file name to weite tre dependency tree to.<br/>
     * prints to console if thereis none.
     */
    private static final String YAML_KEY_TREE_OUTPUT_FILE = "treeOutputFile";
    private Path treeOutputFile = null; // default is no output file

    static DependencyTree<Dependency> dependencyxTree;
    static DependencyEntry<Dependency> rootDependencyEntry;

    public static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(MigrateConfigResources.class);

    @Override
    public void setStep(MigrationStep step)
    {
        String treeFormat = step.getOption(YAML_KEY_TREE_FORMAT);
        if ("TEXT".equalsIgnoreCase(treeFormat) || "JSON".equalsIgnoreCase(treeFormat))
        {
            this.treeFormat = treeFormat;
        }
        String treeOutputFile = step.getOption(YAML_KEY_TREE_OUTPUT_FILE);
        if (!StringUtils.isEmpty(treeOutputFile))
        {
            try
            {
                this.treeOutputFile = java.nio.file.Paths.get(treeOutputFile);
            }
            catch(Exception e)
            {
                LOGGER.debug("Invalid tree output file path: {}. Using default (no output file).", treeOutputFile);
            }
        }
    }

    /**
     * main ethod to examine cartidge dependencies..<br/>
     * if not yet ecaminded 1st a carteidge is assigned to the prject.<br/>
     * Loopuing throgh the carteidges, their dependencies are (re-)assigned as tghey depend on each other....<br/>
     * 
     */
    @Override
    public void migrate(Path cartridgeDir, MigrationContext context)
    {

        if (null == cartridgeDir || !Files.exists(cartridgeDir))
        {
            LOGGER.error("Project path does not exist:{} ", cartridgeDir.toFile());
            return;
        }
        Path projectPath = cartridgeDir.getName(2);
        Path cartridgePath = cartridgeDir.getName(3);
        String cartridgeName = cartridgePath.toString();

        if (toBeExaminded(cartridgePath))
        {
            if (null == rootDependencyEntry)
            {
                // if not yet initialized, create a new root entry
                // for the dependency tree
                Dependency rootDependency = new Dependency(projectPath.getFileName().toString(), null,
                                DependencyType.ROOT);
                dependencyxTree = new DependencyTree<Dependency>(rootDependency);
            }
            rootDependencyEntry = dependencyxTree.getRoot();
            Dependency dependency = new Dependency(cartridgeName, null, DependencyType.CARTRIDGE);
//            if (DependencyTree.findElement(rootDependencyEntry, dependency) == null)
//            {
                // if not yet in the tree, add it
                DependencyEntry<Dependency> cartridgeEntry = new DependencyEntry<>(dependency);
                rootDependencyEntry.addChild(cartridgeEntry);
                LOGGER.info("Adding cartridge {} to dependency tree", cartridgeName);

                // scan build.grale.kts in first level (artridge) directories
                String fileToFind = "build.gradle.kts";
                searchFirstLevelDirs(cartridgeEntry, cartridgeDir, fileToFind);
//            }
//            else
//            {
//                LOGGER.info("Cartridge {} already in dependency tree", cartridgeName);
//            }

            // output the dependency tree
            if ("JSON".equals(treeFormat))
            {
                printJSON(dependencyxTree);
            }
            else
            {
                printTree(rootDependencyEntry, "");
            }

        }

    }

    /**
     * walks through cartridge directories to find and analyze the build,.gradle.kts file.<br/>
     * 
     * @param cartridgeEntry the cartridge entry to add dependencies to
     * @param startDir the directory to start searching from
     * @param targetFile the name of the build file to search for
     */
    private static void searchFirstLevelDirs(DependencyEntry<Dependency> cartridgeEntry, Path startDir,
                    String targetFile)
    {
        try (Stream<Path> stream = Files.walk(startDir, 0, FileVisitOption.FOLLOW_LINKS))
        {
            stream.filter(Files::isDirectory)
                  .filter(dir -> toBeExaminded(dir)) // Exclude the start directory itself
                  .forEach(dir -> analyzeBuildFile(cartridgeEntry, dir, targetFile));
        }
        catch(IOException e)
        {
            LOGGER.error("Error searching directories: " + e.getMessage());
        }
    }

    /**
     * list of excluded cartridge directories Theys are part of the standard project setup, the cartridte
     * pf_configuration_fs is part of the ICM standard softwqare replacing the 7.10.x version of the pf_configuration
     * framewortk.
     * 
     * @param dir the carteidge directory to check
     * @return true of the cartridge is not a default one or pf_configuration ant this is subject to migrtion.<br/>
     */
    private static boolean toBeExaminded(Path dir)
    {
        String dirName = dir.toString();
        return !(dirName.contains("pf_configuration_fs") || dirName.contains("my_cartridge")
                        || dirName.contains("my_geb_test") || dirName.contains("versions")
                        || dirName.contains("versions_test"));
    }

    /**
     * analyzes the build file in the given directory.<br/>
     * if it exists, it is parsed and the dependencies therein are added to the cartridge entry in the dependency tree.
     * 
     * @param cartridgeEntry the cartridge entry to add dependencies to
     * @param dir the directory to search for the build file
     * @param targetFile the name of the build file to search for
     */
    private static void analyzeBuildFile(DependencyEntry<Dependency> cartridgeEntry, Path dir, String targetFile)
    {
        if (Files.exists(dir.resolve(targetFile)))
        {
            File buildFile = dir.resolve(targetFile).toFile();
            String cartridgName = dir.getFileName().toString();
            KtsDependencyAnalyzer ktsAnalyzer = new KtsDependencyAnalyzer();

            Dependency dependency = new Dependency(cartridgName, buildFile.getName(), DependencyType.CARTRIDGE);

            Dependency existingEntry = DependencyTree.findElement(rootDependencyEntry, dependency);
            try
            {
                if (null == existingEntry)
                {
                    DependencyEntry<Dependency> dependencyEntry = new DependencyEntry<>(dependency);
                    cartridgeEntry.addChild(dependencyEntry);

                    // analyze the dependencies within the build file
                    List<Dependency> denendencies = ktsAnalyzer.parseKtsFile(buildFile.toPath());
                    if (denendencies != null && denendencies.size() > 0)
                    {
                        for (Dependency dep : denendencies)
                        {
                            DependencyEntry<Dependency> child = new DependencyEntry<>(dep);

                            // recurse
                            String dubCarteidgeName = child.getValue().getName();
                            if(! dubCarteidgeName.contains(":"))
                            {
                                Path subDir = dir.getParent().resolve( dubCarteidgeName );
                                Path  subbuildGradle = subDir.resolve("build.gradle.kts");
                                analyzeBuildFile(child, subDir, subbuildGradle.toString());
                            }

                            dependencyEntry.addChild(child);
                        }
                    }
                }
                else
                {
                }
            }
            catch(Exception e)
            {
                LOGGER.error("exceptiin when searching " + targetFile + " in directory: " + dir + ": "
                                + e.getMessage());
                e.printStackTrace();
            }
        }
        else
        {
            LOGGER.error("File " + targetFile + " not found in directory: " + dir);
        }
    }

    // --------------------------------------------------------------
    // print metoods
    // --------------------------------------------------------------

    /**
     * prints the dependency tree donwards starig from an entry
     * 
     * @param node the node of the dependency tree to start at
     * @param insertion the prefix to use for each line
     * 
     */
    public <T> void printTree(DependencyEntry<Dependency> node, String insertion)
    {
        if (node == null)
        {
            return;
        }
        String line = insertion + node.getValue().getName();

        printOut(line);

        // Recursively print each child with an updated prefix
        for (DependencyEntry<Dependency> child : node.getChildren())
        {
            printTree(child, insertion + "    ");
        }
    }

    /**
     * prints the dependency tree as JSON to the output file or console.<br/>
     * 
     * @param dependencyxTree the dependency tree to print
     */

    public <T> void printJSON(DependencyTree<Dependency> dependencyxTree)
    {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(dependencyxTree);

        printOut(json);
    }

    /**
     * print the prepared tontent to the output file or console.<br/>
     * 
     * @param content the text or JSON content to print
     */
    private void printOut(String content)
    {
        if (null == this.treeOutputFile)
        {
            // if no output file is set, print to console
            System.out.println(content);
        }
        else
        {
            // if an output file is set, write to it
            try
            {
                Files.writeString(treeOutputFile, content + System.lineSeparator(),
                                java.nio.file.StandardOpenOption.CREATE, 
                                java.nio.file.StandardOpenOption.APPEND);
            }
            catch(IOException e)
            {
                LOGGER.error("Error writing to output file {}: {}, ", treeOutputFile.getFileName().toString(),
                                e.getMessage());
            }
        }
    }

}
