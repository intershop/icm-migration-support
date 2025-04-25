package com.intershop.customization.migration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.LoggerFactory;

import com.intershop.customization.migration.common.MigrationPreparer;

public class MoveArtifacts implements MigrationPreparer
{
    public void migrate(Path cartridgeDir)
    {
        Path staticFilesFolder = cartridgeDir.resolve("staticfiles");
        Path staticCartridgeFolder = staticFilesFolder.resolve("cartridge");
        Path cartridgeName = cartridgeDir.getName(cartridgeDir.getNameCount() - 1);
        Path sourceMain = cartridgeDir.resolve("src/main");

        if (!staticCartridgeFolder.toFile().exists())
        {
            LoggerFactory.getLogger(getClass()).debug("Can't find cartridges static folder {}.", staticCartridgeFolder);
            return;
        }
        LoggerFactory.getLogger(getClass()).info("Processing cartridges {}.", cartridgeName);

        try
        {
            Set<Path> toRemove = new HashSet<>();

            Files.walk(staticCartridgeFolder)
                 .filter(p -> p.getNameCount() > staticFilesFolder.getNameCount() + 1)
                 .map(p -> p.subpath(1 + staticFilesFolder.getNameCount(), p.getNameCount()))
                 .filter(this::shouldMigrate)
                 .forEach(p -> {
                     try
                     {
                         Path source = staticCartridgeFolder.resolve(p);
                         if (!source.toFile().isDirectory())
                         {
                             Path targetFile = getTarget(cartridgeName, p, sourceMain);
                             targetFile.toFile().getParentFile().mkdirs();
                             Files.move(source, targetFile);
                             LoggerFactory.getLogger(getClass()).debug("Moved file from {} to {}.", source, targetFile);
                            }
                         else
                         {
                             toRemove.add(source);
                         }
                     }
                     catch(Exception e)
                     {
                         throw new RuntimeException(e);
                     }
                 });

            toRemove.stream().filter(this::isEmpty).forEach(this::delete);

        }
        catch(IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    private Set<String> getArtifactTypes()
    {
        return Set.of("dbprepare", "pipelines", "webforms", "extensions", "pagelets", "localizations", "queries",
                        "impex", "config", "templates");
    }

    private Path getTarget(Path cartridgeName, Path source, Path sourceMain)
    {
        return switch(source.getName(0).toString())
        {
            case "lib" -> sourceMain.resolve("resources").resolve(source.subpath(1, source.getNameCount()));
            case "templates" -> sourceMain.resolve("isml")
                                          .resolve(cartridgeName)
                                          .resolve(source.subpath(1, source.getNameCount()));
            default -> sourceMain.resolve("resources/resources").resolve(cartridgeName).resolve(source);
        };
    }

    private boolean shouldMigrate(Path path)
    {
        if (getArtifactTypes().contains(path.getName(0).toString()))
        {
            return !path.getFileName().toString().endsWith(".jar") &&
                   !path.getFileName().toString().endsWith(".zip");
        }
        if (getArtifactTypes().contains("dbprepare") && 1 == path.getNameCount())
        {
            String fileName = path.getFileName().toString();
            return fileName.endsWith(".properties") &&
                            (fileName.startsWith("migration") ||
                             fileName.startsWith("dbinit"));
        }
        return false;
    }

    private boolean isEmpty(Path p)
    {
        if (!p.toFile().isDirectory())
        {
            return false;
        }
        File[] children = p.toFile().listFiles();
        assert children != null;
        for (File child : children)
        {
            if (!isEmpty(child.toPath()))
            {
                return false;
            }
        }
        return true;
    }

    private void delete(Path p)
    {
        try
        {
            if (p.toFile().isDirectory())
            {
                File[] children = p.toFile().listFiles();
                assert children != null;
                for (File child : children)
                {
                    delete(child.toPath());
                }
            }
            Files.deleteIfExists(p);
        }
        catch(IOException e)
        {
            throw new RuntimeException(e);
        }
    }
}
