package com.intershop.customization.migration.dependencies;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.FileVisitOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import java.util.*;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intershop.customization.migration.common.MigrationContext;
import com.intershop.customization.migration.common.MigrationPreparer;
import com.intershop.customization.migration.common.MigrationStep;
import com.intershop.customization.migration.pfconfigurationfs.MigrateConfigResources;

// import nonapi.io.github.classgraph.utils.StringUtils;

public class ExamineCartridgeDependencies implements MigrationPreparer
{
    /**
     * Name of the build file to examine for each cartridge, declaring its dependencies.
     */
    private static final String BUILD_FILE_NAME = "build.gradle.kts";

    /**
     * YAML key for tree format option JSON and TEXT are supported.<br/>
     */
    private static final String YAML_KEY_TREE_FORMAT = "treeFormat";
    private String treeFormat = "TEXT"; // default format

    /**
     * YAML key of the output file name to write the dependency tree to.<br/>
     * prints to console if there is none.
     */
    private static final String YAML_KEY_TREE_OUTPUT_FILE = "treeOutputFile";
    private Path treeOutputFile = null; // default is no output file

    /* ------------------------------------------------------------ */
    /* analysis                                                     */
    /* ------------------------------------------------------------ */

    static DependencyTree<Dependency> dependencyTree;
    static DependencyEntry<Dependency> rootDependencyEntry;
    static List<String> rootCartridgeCrumbs;

    static Map<String, List<String>> appCartridgeMap;
    static DependencyTree<Dependency> appDependencyTree;
    static DependencyEntry<Dependency> appRootDependencyEntry;

    public static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(MigrateConfigResources.class);


    @Override
    public void setStep(MigrationStep step)
    {
        String treeFormat = step.getOption(YAML_KEY_TREE_FORMAT);
        if ("TEXT".equalsIgnoreCase(treeFormat) || "JSON".equalsIgnoreCase(treeFormat))
        {
            this.treeFormat = treeFormat;
        }
        else
        {
            LOGGER.warn("Invalid tree format: {}. Using default (TEXT).", treeFormat);
        }
        String treeOutputFile = step.getOption(YAML_KEY_TREE_OUTPUT_FILE);
        if (!treeOutputFile.isEmpty())
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
     * main method to examine cartridge dependencies.<br/>
     * if not yet examined 1st a cartridge is assigned to the project.<br/>
     * Looping through the cartridges, their dependencies are (re-)assigned as they depend on each other.<br/>
     */
    @Override
    public void migrate(Path cartridgeDir, MigrationContext context)
    {

        if (null == cartridgeDir || !Files.exists(cartridgeDir))
        {
            LOGGER.error("Project path does not exist:{} ", (null == cartridgeDir ) ? cartridgeDir : "NULL");
            return;
        }
        Path projectPath = cartridgeDir.getName(2);
        Path cartridgePath = cartridgeDir.getName(3);
        String cartridgeName = cartridgePath.toString();

        if (toBeExaminded(cartridgePath))
        {

            LOGGER.info("");
            LOGGER.info("-----------------------------------------------------------------");
            LOGGER.info("-- 1. build the dependency tree by {}/{}", cartridgeName, BUILD_FILE_NAME);
            LOGGER.info("-----------------------------------------------------------------");

            if (null == rootDependencyEntry)
            {
                // if not yet initialized, create a new root entry
                // for the dependency tree
                Dependency rootDependency = new Dependency(projectPath.getFileName().toString(), null,
                                DependencyType.ROOT);
                dependencyTree = new DependencyTree<Dependency>(rootDependency);
                rootCartridgeCrumbs =  new ArrayList<>();
            }
            rootDependencyEntry = dependencyTree.getRoot();
            Dependency dependency = new Dependency(cartridgeName, null, DependencyType.CARTRIDGE);
            DependencyEntry<Dependency> cartridgeEntry = new DependencyEntry<>(dependency);

            rootDependencyEntry.addChild(cartridgeEntry);

            LOGGER.info("-- 2. check for dependency cycle                               --");
            LOGGER.info("-----------------------------------------------------------------");
            // scan build.gradle.kts in first level (cartridge) directories
            List<String> cartridgeCrumbs = searchFirstLevelDirs(cartridgeEntry, cartridgeDir, BUILD_FILE_NAME);
            if(!cartridgeCrumbs.isEmpty())
            {
                rootCartridgeCrumbs.addAll(cartridgeCrumbs);
                FullCycleCollector.hasCycles(cartridgeCrumbs);
                FullCycleCollector.storeCartridgeAssignmentsCrumbs(cartridgeCrumbs);
            }
            else
            {
                LOGGER.info("No cartridge dependencies found in {}", cartridgeDir);
            }

            // output the dependency tree
            if ("JSON".equals(treeFormat))
            {
                printJSON(dependencyTree);
            }
            else
            {
                printTree(rootDependencyEntry, "");
            }
            
            FullCycleCollector.storeCartridgeAssignmentsCrumbs(rootCartridgeCrumbs);

            LOGGER.info("-- 3. check applications for cartridge dependencies            --");
            LOGGER.info("-----------------------------------------------------------------");

            Path componentsDir = cartridgeDir
                .resolve("src/main/resources/resources")
                .resolve(cartridgeName)
                .resolve("components");

            try (Stream<Path> stream = Files.list(componentsDir)) 
            {
                stream.filter(p -> p.toString().endsWith(".component") 
                               && ( p.toString().contains(File.separator + "apps.")
                                    ||  p.toString().contains(File.separator + "app-")
                                   )
                               )
                .forEach(appExtensionPath ->
                 {

                    // Do something with each .component file
                    String appExtensionFileName = appExtensionPath.toString() ;
                    if(Files.exists(appExtensionPath, LinkOption.NOFOLLOW_LINKS))
                    {
                        try (InputStream xmlInput = new FileInputStream(appExtensionFileName)) 
                        {
                            appCartridgeMap = parseAppextensionXML(xmlInput);
                        }
                        catch(Exception e)
                        {
                            appCartridgeMap = new HashMap<>();
                            LOGGER.error("Exception when scanning {} - ", appExtensionFileName
                                            + e.getMessage());
                        }
                    }   
                    else
                    {
                        appCartridgeMap = new HashMap<>();
                    }

                    // add the application cartridge map to the dependency tree
                    if (null == appDependencyTree)
                    {
                        // if not yet initialized, create a new root entry
                        // for the dependency tree
                        Dependency appRootDependency = new Dependency(projectPath.getFileName().toString(), null,
                                        DependencyType.APPLICATION);
                        appDependencyTree = new DependencyTree<Dependency>(appRootDependency);
                    }
                    appRootDependencyEntry = appDependencyTree.getRoot();

                    // application dependency tree - adding cartridge;
                    appCartridgeMap.forEach((app, cartridges) -> 
                    {
                        Dependency apDependency = null;;
                        DependencyEntry<Dependency> apDependencyEntry = null;
                        for(DependencyEntry<Dependency> entry: appRootDependencyEntry.getChildren())
                        {
                            if(entry.getValue().getName().equals(app))
                            {
                                apDependency = entry.getValue();
                                apDependencyEntry = entry;
                                break;
                            }
                        }
                        if(null == apDependency)
                        {
                            apDependency = new Dependency(app, "app-extension.component", DependencyType.APPLICATION);
                            apDependencyEntry = new DependencyEntry<Dependency>(apDependency);
                            appRootDependencyEntry.addChild(apDependencyEntry);
                        }

                        for(String cartridge: cartridges)
                        {
                            Dependency cartridgeDependency = new Dependency(cartridge, "app-extension.component", DependencyType.CARTRIDGE);
                            apDependencyEntry.addChild(new DependencyEntry<Dependency>(cartridgeDependency));
                    
                        }
                    });
                });
            } catch (IOException e) {
                LOGGER.error("Error listing .component files: " + e.getMessage());
            }

            // output the dependency tree
            // in post processing because it is complete, there...

        }
    }
    /**
     * pre migration processing of the cartridge dependencies.<br/>
     */
    @Override
    public void preMigrate(File rootProject)
    {
        FullCycleCollector.remobeBreadCrumbsFile();
        MarkerCartridgeAnalyzer.removeMarkerCartridgeAssignmentsFile();
    }

    /**
     * post migration processing of the cartridge dependencies.<br/>
     * This method is called after all cartridges have beenanalyzed.<br/>
     * It checks for <br/>
     *  - wrong assigned marker carteidgesand<br/>
     *  - cycles in the cartridge dependencies across all cartridgess.<br/>
     * 
     * @param rootProject the root project directory to post process
     */
    @Override
    public void postMigrate(File rootProject)
    {
        LOGGER.info("----------------------------------------------------------------");
        LOGGER.info("-- cartridge dependency - post rocessing                      --");
        LOGGER.info("----------------------------------------------------------------");

        LOGGER.info("-- p.1 top level application dependencies                      --");
        LOGGER.info("-----------------------------------------------------------------");
        if ("JSON".equals(treeFormat))
        // output the dependency tree
        {
            printJSON(appDependencyTree);
        }
        else
        {
            printTree(appRootDependencyEntry, "");
        }
            
        LOGGER.info("-- p.2 check applications for wrong assigned marker cartridges --");
        LOGGER.info("-----------------------------------------------------------------");
        rootCartridgeCrumbs = FullCycleCollector.loadSavedBreadCrumbs();
        FullCycleCollector.hasCycles(rootCartridgeCrumbs);

        LOGGER.info("-- p.3 check for dependency cycles across all cartridges       --");
        LOGGER.info("-----------------------------------------------------------------");
        HashSet<String> checkMarkerCartridgesResult =
        MarkerCartridgeAnalyzer.analyzeMarkerCartridges(rootCartridgeCrumbs);
        MarkerCartridgeAnalyzer.printMarkerCartridgeAssignments(checkMarkerCartridgesResult);
    }

    /**
     * walks through cartridge directories to find and analyze the build.gradle.kts file.<br/>
     * 
     * @param cartridgeEntry the cartridge entry to add dependencies to
     * @param startDir the directory to start searching from
     * @param targetFile the name of the build file to search for
     */
    private static List<String> searchFirstLevelDirs(
        DependencyEntry<Dependency> cartridgeEntry, 
        Path startDir,
        String targetFile)
    {
        List<String> cartridgeCrumbs = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(startDir, 0, FileVisitOption.FOLLOW_LINKS))
        {
            stream.filter(Files::isDirectory)
                  .filter(dir -> toBeExaminded(dir)) // Exclude the start directory itself
                  .forEach(dir -> cartridgeCrumbs.add( analyzeBuildFile(cartridgeEntry, dir, targetFile, "")));
        }
        catch(IOException e)
        {
            LOGGER.error("Error searching directories: " + e.getMessage());
        }
        return cartridgeCrumbs;
    }

    /**
     * list of excluded cartridge directories They are part of the standard project setup, the cartridge
     * pf_configuration_fs is part of the ICM standard software replacing the 7.10.x version of the pf_configuration
     * framework.
     * 
     * @param dir the cartridge directory to check
     * @return true of the cartridge is not a default one or pf_configuration and this is subject to migration.<br/>
     */
    private static boolean toBeExaminded(Path dir)
    {
        String dirName = dir.toString();
        return !(dirName.contains("pf_configuration_fs") 
                  || dirName.contains("migration")
                  || dirName.contains("my_cartridge")
                  || dirName.contains("my_geb_test") 
                  || dirName.contains("versions")
                  || dirName.contains("versions_test"));
    }

    /**
     * analyzes the build file in the given directory.<br/>
     * if it exists, it is parsed and the dependencies therein are added to 
     * the cartridge entry in the dependency tree.<br/>
     * Cartridges with no source code in the project
     * e.g. &quot;com.intershop.platform:core&quot; are not recursed into.<br/>
     * 
     * @param cartridgeEntry the cartridge entry to add dependencies to
     * @param dir the directory to search for the build file
     * @param targetFile the name of the build file to search for
     * @param cartridgeCrumbs the cartridge names are getting added down the recursion to detect
     *  a double ond - This way circular references are to be detected.
     * @return the cartridge crumbs, which are the names of the cartridges in the dependency path.<br/>
     */
    private static String analyzeBuildFile(
        DependencyEntry<Dependency> cartridgeEntry, 
        Path dir, 
        String targetFile,
        String cartridgeCrumbs)
    {
        if (Files.exists(dir.resolve(targetFile)))
        {
            File buildFile = dir.resolve(targetFile).toFile();
            String cartridgName = dir.getFileName().toString();
            if(cartridgName.startsWith(KtsDependencyAnalyzer.MARK_EXCLUDED_DEPENDENCY))
            {
                // return the current crumbs without adding the cartridge
                // just to be listed
                cartridgeCrumbs += " > " + cartridgName;
                return cartridgeCrumbs; 
            }

            if(cartridgeCrumbs.contains(cartridgName))
            {
                // return the current crumbs without adding the cartridge
                // needed to check for that cycle
                cartridgeCrumbs += " > " + cartridgName;
                return cartridgeCrumbs; 
            }
            else
            {
                if("".equals(cartridgeCrumbs))
                {
                    cartridgeCrumbs = cartridgName;
                }
                 else {
                    cartridgeCrumbs += " > " + cartridgName;
                }
            }
            KtsDependencyAnalyzer ktsAnalyzer = new KtsDependencyAnalyzer();

            try
            {
                // analyze the dependencies within the build file
                List<Dependency> denendencies = ktsAnalyzer.parseKtsFile(buildFile.toPath());
                if (denendencies != null && !denendencies.isEmpty())
                {
                    for (Dependency dep : denendencies)
                    {
                        DependencyEntry<Dependency> child = new DependencyEntry<>(dep);
                        String subCarteidgeName = child.getValue().getName();
                        if(!subCarteidgeName.startsWith(KtsDependencyAnalyzer.MARK_EXCLUDED_DEPENDENCY) 
                        && isProjectCartridge(subCarteidgeName))
                        {
                            Path subDir = dir.getParent().resolve( subCarteidgeName );
                            Path  subbuildGradle = subDir.resolve(BUILD_FILE_NAME);
                            cartridgeCrumbs = analyzeBuildFile(child, subDir, subbuildGradle.toString(), cartridgeCrumbs);
                        }
                        cartridgeEntry.addChild(child);
                    }
                }
            }
            catch(Exception e)
            {
                LOGGER.error("Exception when searching " + targetFile + " in directory: " + dir + ": "
                                + e.getMessage());
            }
        }
        else
        {
            if(! dir.getFileName().toString().contains(KtsDependencyAnalyzer.MARK_EXCLUDED_DEPENDENCY))
            {
                LOGGER.error("File " + targetFile + " not found in directory: " + dir);
            }
        }
        return cartridgeCrumbs;
    }

    /**
     * checks if the referende is a project cartridge.<br/>
     * A project cartridge is a cartridge that is not part of the ICM standard software and
     * has no package name in its reference name. 
     * The format of these depenencies is<br/>
     *   - non-project cartridge: <packageName>:<cartridgeName>[:<version>]<br/>
     *   - project cartridge: [:]<cartridgeName>:<version><br/>
     * 
     * @param referenceName the reference name of the cartridge
     * @return true if it is a project cartridge, false otherwise
     */
    private static boolean isProjectCartridge(String referenceName) 
    {
        return  !(referenceName.indexOf(":")>0);
    }

    // ---------------------------------------------------------------
    // scan app-extension.component  
    // ---------------------------------------------------------------

    /**
     * Reads all <fulfill> tags with requirement="selectedCartridge".
     * For each, if it starts with "intershop.", adds the value to 
     * the application's cartridge list.
     * Returns a map: {application, [cartridge, cartridge, ...]}.
    *
     * @param xmlInput content of the app-extension.component 
     * @return a map of applications with a list of their cartridges
     * @throws Exception
     */
    private static Map<String, List<String>> parseAppextensionXML(
    InputStream xmlInput) 
    throws Exception 
    {
        Map<String, List<String>> appCartridgeMap = new HashMap<>();

        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setNamespaceAware(true);
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(xmlInput);

        NodeList instance = doc.getElementsByTagNameNS("*", "instance");
        for (int i = 0; i < instance.getLength(); i++) 
        {
            Element instanceElement = (Element) instance.item(i);
            String application = instanceElement.getAttribute("name");
            if (application != null && application.startsWith("intershop.")) 
            {
                NodeList fulfillNodes = instanceElement.getElementsByTagNameNS("*", "fulfill");

                for (int j = 0; j < fulfillNodes.getLength(); j++) 
                {
                    Element fillFillElement = (Element) fulfillNodes.item(j);
                    String requirement = fillFillElement.getAttribute("requirement");
                    if ("selectedCartridge".equals(requirement)) 
                    {
                        String cartridge = fillFillElement.getAttribute("value");
                        if (cartridge != null && !cartridge.isEmpty()) 
                        {
                            // Add the cartridge to the application's list
                            appCartridgeMap.computeIfAbsent(application, k -> new ArrayList<>()).add(cartridge);
                        }
                    }
                }
                // Initialize the list for this application if not already present
                appCartridgeMap.putIfAbsent(application, new ArrayList<>());
            } 
        }

        NodeList fulfillNodes = doc.getElementsByTagNameNS("*", "fulfill");
        for (int i = 0; i < fulfillNodes.getLength(); i++) 
        {
            Element fulfill = (Element) fulfillNodes.item(i);
            String requirement = fulfill.getAttribute("requirement");
            if (!"selectedCartridge".equals(requirement)) continue;

            String application = fulfill.getAttribute("of");
            String cartridge = fulfill.getAttribute("value");
            if (application != null 
            && application.startsWith("intershop.") 
            && cartridge != null && !cartridge.isEmpty()) 
            {
                appCartridgeMap.computeIfAbsent(application, k -> new ArrayList<>()).add(cartridge);
            }
        }
        return appCartridgeMap;
    }

    // ---------------------------------------------------------------
    // print/ handle results
    // ---------------------------------------------------------------

    /**
     * prints the dependency tree downwards starting from an entry
     * 
     * @param node the node of the dependency tree to start at
     * @param insertion the prefix to use for each lineHashSet<String> recentMarkerCartridgesResult
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
