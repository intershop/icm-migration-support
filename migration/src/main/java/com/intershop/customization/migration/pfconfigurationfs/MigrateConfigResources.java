package com.intershop.customization.migration.pfconfigurationfs;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.slf4j.LoggerFactory;

import com.intershop.customization.migration.common.MigrationPreparer;

public class MigrateConfigResources implements MigrationPreparer
{

    public final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(getClass());

    public static final String RESOURCES = "resources";

    @Override
    public void migrate(Path cartridgeDir)
    {
        Path staticFilesFolder = cartridgeDir.resolve("staticfiles");
        Path staticCartridgeFolder = staticFilesFolder.resolve("cartridge");
        Path staticShareFolder = staticFilesFolder.resolve("share");
        Path staticSitesFolder = staticFilesFolder.resolve("sites");
        String cartridgeName = cartridgeDir.getName(cartridgeDir.getNameCount() - 1).toString();
        Path targetSourceMain = cartridgeDir.resolve("src/main");

        if (!staticCartridgeFolder.toFile().exists())
        {
            LOGGER.debug("Can't find cartridges static folder {}.", staticCartridgeFolder);
            return;
        }
        LOGGER.info("Processing cartridge '{}' in {}.", cartridgeName, staticCartridgeFolder.toFile().getAbsolutePath());

        List<Path> toBeMigrated = List.of(staticCartridgeFolder, staticShareFolder, staticSitesFolder);
        for (Path path : toBeMigrated)
        {
            if (path.toFile().isDirectory() )
            {
                LOGGER.debug("Processing files in '{}'.", path);
            }
            else
            {
                LOGGER.warn("'{}' is no directory - skipping.", path);
                continue;
            }

            try (Stream<Path> files = Files.walk(path))
            {
                files.filter(p -> p.getNameCount() > staticFilesFolder.getNameCount() + 1)
                     .map(p -> p.subpath(1 + staticFilesFolder.getNameCount(), p.getNameCount()))
                     .filter(this::shouldMigrate)
                     .forEach(p -> migrate(targetSourceMain, cartridgeName, p));
            }
            catch(IOException e)
            {
                throw new RuntimeException("Error while processing files", e);
            }
        }
    }

    private void migrate(Path targetSourceMain, String cartridgeName, Path sourceFile)
    {
        try
        {
            Path targetFile = getTarget(cartridgeName, sourceFile, targetSourceMain);
            String targetFileName = targetFile.getFileName().toString();

            targetFile.toFile().getParentFile().mkdirs();

            // resource files must be converted @see CfgResourceConverter
            String targetType = "";
            if (targetFileName.endsWith("transport.resource"))
            {
                targetType = "transport";
            }
            else if (targetFileName.endsWith("application.resource"))
            {
                targetType = "application";
            }
            else if (targetFileName.endsWith("usr.resource"))
            {
                targetType = "user";
            }
            if (!targetType.isEmpty())
            {
                // convert resource file to properties file
                String targetName = targetFile.toFile().getAbsolutePath();
                targetName = targetName.replace(".resource", ".properties");
                Path target = Paths.get(targetName);
                convertResourceFile(targetType, sourceFile, target);
                Files.delete(sourceFile);
                LOGGER.debug("Converted file {} ==>  {}.", sourceFile, target);
            }
            else
            {
                // other than resource files are just moved to their new location
                LOGGER.warn("file {} not yet handled.", sourceFile);
            }
        }
        catch(Exception e)
        {
            throw new RuntimeException("Error while converting file: " + sourceFile,e);
        }
    }

    private Path getTarget(String cartridgeName, Path source, Path sourceMain)
    {
        String fileName = source.getName(0).toString();
        Path targetPath = sourceMain.resolve(RESOURCES).resolve(RESOURCES).resolve(cartridgeName);

        switch(fileName)
        {
            case "system":
                // staticfiles/share/system/config -> src/main/resources/resources/{cartridgeName}/config
                Path targetSubConfig = source.subpath(1, source.getNameCount());
                targetPath = targetPath.resolve(targetSubConfig);
                break;
            case "domains":
                // staticfiles/share/system/config/domains -> src/main/resources/resources/{cartridgeName}/config/domains
                Path targetSubDomains = source.subpath(2, source.getNameCount());
                targetPath = targetPath.resolve(targetSubDomains);
                break;
            case "cartridge":
                // staticfiles/share/sites -> src/main/resources/resources/{cartridgeName}/sites
                Path targetSubSites = source.subpath(2, source.getNameCount());
                targetPath = targetPath.resolve(targetSubSites);
                break;
            default:
                // all others -> src/main/resources/resources/{cartridgeName}
                Path targetSub = source.subpath(3, source.getNameCount());
                targetPath = targetPath.resolve(targetSub);
        }

        return targetPath;
    }

    private boolean shouldMigrate(Path path)
    {
        return path.getFileName().toString().endsWith(".resource") && !path.toFile().isDirectory();
    }

    /**
     * Convert resource files to properties files.
     **/
    private void convertResourceFile(String resourceCfgType, Path source, Path target)
    {
        CfgResourceConverter converter = new CfgResourceConverter(resourceCfgType, source, target);
        converter.convertTransportResource();
    }
}
