package com.intershop.customization.migration.dependencies;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.FileVisitOption;
import java.nio.file.Path;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.intershop.customization.migration.common.MigrationContext;
import com.intershop.customization.migration.common.MigrationPreparer;

public class ExamineCartridgeDependencies  implements MigrationPreparer
{

    static DependencyTree<Dependency> dependencyxTree;
    static DependencyEntry <Dependency> rootDependencyEntry;


    /** main ethod to examine cartidge dependencies..<br/>
     * if not yet ecaminded 1st a carteidge is assigned to the prject.<br/>
     * Loopuing throgh the carteidges, their 
     * dependencies are (re-)assigned as tghey depend on each other....<br/>
     * 
     */
    @Override
    public void migrate(Path cartridgeDir, MigrationContext context) {

        // Path cartridgePath = cartridgeDir.getName(cartridgeDir.getNameCount() - 1);
        Path cartridgePath = cartridgeDir.getName(3);
        String cartridgeName = cartridgePath.toString();

        Dependency dependency = new Dependency(
            cartridgeName,
            null, 
            DependencyType.CARTRIDGE);
        dependencyxTree = new DependencyTree<Dependency>(dependency);
        rootDependencyEntry = dependencyxTree.getRoot();

        // scan build.grale.kts in first level (artridge) directories
        String fileToFind = "build.gradle.kts";
        searchFirstLevelDirs(cartridgeDir, fileToFind);

        Gson gson = new Gson();
        String json = gson.toJson(dependencyxTree);

        // just goes to STDOUT  - so far...
        System.out.println(json);
    }
    private static void searchFirstLevelDirs(Path startDir, String targetFile) 
    {
        try (Stream<Path> stream = Files.walk(startDir, 0, FileVisitOption.FOLLOW_LINKS) )
        {
            stream
                .filter(Files::isDirectory)
                .filter(dir -> toBeExaminded(dir)) // Exclude the start directory itself
                .forEach(dir -> analyzeBuildFile(dir, targetFile));
        } catch (IOException e) 
        {
            System.err.println("Error searching directories: " + e.getMessage());
        }
    }

    private static boolean toBeExaminded(Path dir) {
        String dirName = dir.toString();
        return ! (dirName.contains("pf_configuration_fs") 
                || dirName.contains("my_cartridge") 
                || dirName.contains("my_geb_test") 
                || dirName.contains("versions")
                || dirName.contains("versions_test"));
    }

    private static void analyzeBuildFile(Path dir, String targetFile) {
        if(Files.exists(dir.resolve(targetFile))) {
            File buildFile = dir.resolve(targetFile).toFile();
            String cartridgName = dir.getFileName().toString();  

            Dependency dependency = new Dependency(
                cartridgName,
                buildFile.getName(),
                DependencyType.CARTRIDGE);

            Dependency existingEntry = DependencyTree.findElement(rootDependencyEntry, dependency);
            try {
                if(null == existingEntry) {
                    DependencyEntry<Dependency> dependencyEntry = new DependencyEntry<>(dependency);
                    dependencyEntry.addChild(dependencyEntry);
                } else {
                }
            } catch (Exception e) {
                System.out.println("exceptiin when searching " + targetFile + " in directory: " + dir + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        else {
            System.out.println("File " + targetFile + " not found in directory: " + dir);
        }       
    }

}
