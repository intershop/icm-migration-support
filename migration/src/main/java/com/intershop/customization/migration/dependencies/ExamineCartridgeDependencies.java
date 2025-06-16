package com.intershop.customization.migration.dependencies;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.FileVisitOption;
import java.nio.file.Path;
import java.util.stream.Stream;

import com.intershop.customization.migration.common.MigrationContext;
import com.intershop.customization.migration.common.MigrationPreparer;

public class ExamineCartridgeDependencies  implements MigrationPreparer
{

    DependencyTree<Dependency> dependencyxTree;


    @Override
    public void migrate(Path cartridgeDir, MigrationContext context) {

        Path cartridgePath = cartridgeDir.getName(cartridgeDir.getNameCount() - 1);
        String cartridgeName = cartridgePath.toString();

        Dependency depemdemcy = new Dependency(
            cartridgeName,
            null, 
            DependencyType.ROOT);
        dependencyxTree = new DependencyTree<Dependency>(depemdemcy);

        // scan build.grale.kts in first level (artridge) directories
        String fileToFind = "build.grale.kts";
        searchFirstLevelDirs(cartridgeDir, fileToFind);
    }
    private static void searchFirstLevelDirs(Path startDir, String targetFile) 
    {
        try (Stream<Path> stream = Files.walk(startDir, 1, FileVisitOption.FOLLOW_LINKS) )
        {
            stream
                .filter(Files::isDirectory)
                .forEach(dir -> analyzeBuildFile(dir, targetFile));
        } catch (IOException e) 
        {
            System.err.println("Error searching directories: " + e.getMessage());
        }
    }

    private static void analyzeBuildFile(Path dir, String targetFile) {
        if(Files.exists(dir.resolve(targetFile))) {
            System.out.println("Found " + targetFile + " in directory: " + dir);
            File buildFile = dir.resolve(targetFile).toFile();
            System.out.println("Found " + targetFile + " in directory: " + dir);
            
        } else {
            System.out.println("Did not find " + targetFile + " in directory: " + dir);
        }
    }

}
