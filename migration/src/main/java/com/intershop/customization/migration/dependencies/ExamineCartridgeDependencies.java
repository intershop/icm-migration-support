package com.intershop.customization.migration.dependencies;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.FileVisitOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import java.io.InputStream;
import java.util.*;
import javax.xml.parsers.*;
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
     * YAML key of the output file name to weite tre dependency tree to.<br/>
     * prints to console if thereis none.
     */
    private static final String YAML_KEY_TREE_OUTPUT_FILE = "treeOutputFile";
    private Path treeOutputFile = null; // default is no output file

    /**
     * file to keep the cartridge top level assignment results.<br/>
     */
    private static Path cartridgeAssignmentResultsFile = 
        Paths.get(System.getenv("TEMP")+"/cartridgeAssignmentResults.txt");

    /* ------------------------------------------------------------ */
    /* analysis                                                     */
    /* ------------------------------------------------------------ */

    static DependencyTree<Dependency> dependencyxTree;
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
     * main method to examine cartridge dependencies..<br/>
     * if not yet examined 1st a cartridge is assigned to the project.<br/>
     * Looping through the cartridges, their dependencies are (re-)assigned as they depend on each other....<br/>
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

            // -------------------------------------------------------
            // 1. build the dependency tree by build.grtadle.kts
            // -------------------------------------------------------
            if (null == rootDependencyEntry)
            {
                // if not yet initialized, create a new root entry
                // for the dependency tree
                Dependency rootDependency = new Dependency(projectPath.getFileName().toString(), null,
                                DependencyType.ROOT);
                dependencyxTree = new DependencyTree<Dependency>(rootDependency);
                rootCartridgeCrumbs = new ArrayList<>();
            }
            rootDependencyEntry = dependencyxTree.getRoot();
            Dependency dependency = new Dependency(cartridgeName, null, DependencyType.CARTRIDGE);
            DependencyEntry<Dependency> cartridgeEntry = new DependencyEntry<>(dependency);

            LOGGER.info("Adding cartridge {} to dependency tree", cartridgeName);
            rootDependencyEntry.addChild(cartridgeEntry);

            // scan build.gradle.kts in first level (cartridge) directories
            String fileToFind = "build.gradle.kts";
            List<String> cartridgeCrumbs = searchFirstLevelDirs(cartridgeEntry, cartridgeDir, fileToFind);
            if(cartridgeCrumbs.size() > 0)
            {
                
                // TODO verify undirect dependencies
                // verify geting the dependencies accross all cartridges 
                // otherise: need to log the m as well in create and append mode for analysis

                rootCartridgeCrumbs.addAll(cartridgeCrumbs);
                FullCycleCollector.hasCycles(rootCartridgeCrumbs);
            }

            // output the dependency tree
            if ("JSON".equals(treeFormat))
            {
                printJSON(dependencyxTree);
            }
            else
            {
                printTree(rootDependencyEntry, "");
            }

            // -------------------------------------------------------
            // check applications for cartridge dependencies
            // -------------------------------------------------------

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
                    LOGGER.info("application dependency tree - scanning {} ", appExtensionFileName);
                    if(Files.exists(appExtensionPath, LinkOption.NOFOLLOW_LINKS))
                    {
                        try (InputStream xmlInput = new FileInputStream(appExtensionFileName)) 
                        {
                            LOGGER.info("Scanning {} ...", appExtensionFileName);
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

                    LOGGER.info("application dependency tree - adding cartridge {} ", cartridgeName);

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
                            apDependency = new Dependency(app, "epp-extensiom.properties", DependencyType.APPLICATION);
                            apDependencyEntry = new DependencyEntry<Dependency>(apDependency);
                            appRootDependencyEntry.addChild(apDependencyEntry);
                        }

                        for(String cartridge: cartridges)
                        {
                            Dependency cartridgeDependency = new Dependency(cartridge, "epp-extensiom.properties", DependencyType.CARTRIDGE);
                            apDependencyEntry.addChild(new DependencyEntry<Dependency>(cartridgeDependency));
                    
                        }
                    });
                });
            } catch (IOException e) {
                LOGGER.error("Error listing .component files: " + e.getMessage());
            }

            // output the dependency tree
            if ("JSON".equals(treeFormat))
            {
                printJSON(appDependencyTree);
            }
            else
            {
                printTree(appRootDependencyEntry, "");
            }

            // -------------------------------------------------------
            // check applications for wrong asigned marker cartridges
            // -------------------------------------------------------
            HashSet<String> checkMarkerCartridgesResult =
            MarkerCartridgeAnalyzer.analyzeMarkerCartridges(rootCartridgeCrumbs);
            printMarkerCartridgeAssignments(checkMarkerCartridgesResult);

        }
    }

    /**
     * walks through cartridge directories to find and analyze the build,.gradle.kts file.<br/>
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

            Dependency dependency = new Dependency(cartridgName, buildFile.getName(), DependencyType.CARTRIDGE);
            try
            {
                // analyze the dependencies within the build file
                List<Dependency> denendencies = ktsAnalyzer.parseKtsFile(buildFile.toPath());
                if (denendencies != null && denendencies.size() > 0)
                {
                    for (Dependency dep : denendencies)
                    {
                        DependencyEntry<Dependency> child = new DependencyEntry<>(dep);
                        String subCarteidgeName = child.getValue().getName();
                        if(!subCarteidgeName.startsWith(KtsDependencyAnalyzer.MARK_EXCLUDED_DEPENDENCY) 
                        && isProjectCartridge(subCarteidgeName))
                        {
                            Path subDir = dir.getParent().resolve( subCarteidgeName );
                            Path  subbuildGradle = subDir.resolve("build.gradle.kts");
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
     * The format of these depenencies is <packageName>:<cartridgeName>:<version>
     * 
     * @param referenceName the reference name of the cartridge
     * @return true if it is a project cartridge, false otherwise
     */
    private static boolean isProjectCartridge(String referenceName) 
    {
        String cartridgeName = referenceName;

        // <packageName>:<cartridgeName>:<version>
        String reference[] = referenceName.split(":");
        if (reference.length > 1)
        {
            return  false; // this is not a project cartridge
        }
        return  true;
    }

    // ---------------------------------------------------------------
    // scann app-extension.componnt 
    // ---------------------------------------------------------------

    /**
     * Reads all <fulfill> tags with requirement="selectedCartridge".
     * For each, if of starts with "intershop.", adds the value to 
     * the application's cartridge list.
     * Returns a map: {application, [cartridge, cartridge, ...]}.
    *
     * @param xmlInput xontent of the app-extension.componnt 
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
            else 
            {
                // If the application is not an Intershop application, skip it
                continue;
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

    private void printMarkerCartridgeAssignments(
        HashSet<String> recentMarkerCartridgesResult
    ) 
    {
        List<String>  formerMarkerCartridgesResult = new ArrayList<>();
        try
        {
            if(Files.exists(cartridgeAssignmentResultsFile))
            {
                formerMarkerCartridgesResult
                = Files.readAllLines(cartridgeAssignmentResultsFile);
            }
            if (recentMarkerCartridgesResult.size() > 0)
            {
                for (String message : recentMarkerCartridgesResult)
                {
                    if(!formerMarkerCartridgesResult.contains(message))
                    {
                        formerMarkerCartridgesResult.add(message);
                        //LOGGER.info(message);
                    }
                }
            }
            LOGGER.info ("Writing {} marker cartridge assignments to file: {}",
                            formerMarkerCartridgesResult.size(), cartridgeAssignmentResultsFile.toString());
            Files.write(cartridgeAssignmentResultsFile, formerMarkerCartridgesResult,
                            java.nio.file.StandardOpenOption.CREATE, 
                            java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
         }
        catch (IOException e)
        {
            LOGGER.error("Error creating directory for cartridge assignment results file: {}, ",
                            cartridgeAssignmentResultsFile.getParent().toString(), e.getMessage());
            return;
        }       
    }

}
