package com.intershop.customization.migration.pfconfigurationfs;

import static com.intershop.customization.migration.common.MigrationContext.OperationType.MODIFY;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.slf4j.LoggerFactory;

import com.intershop.customization.migration.common.MigrationContext;
import com.intershop.customization.migration.common.MigrationPreparer;
import com.intershop.customization.migration.utils.FileUtils;

// import ch.qos.logback.core.net.ssl.TrustManagerFactoryFactoryBean;

public class MigrateConfigResources implements MigrationPreparer
{


    public static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(MigrateConfigResources.class);


    /**
     * Migrates a resource with context tracking.
     * It allows recording success, failures, and other metrics.<br/>
     * Actually the directory src/main/resources/resources/{cartridgeName}/config 
     * is left for each carttridge as the .resource files were already moved there by tasks before.
     *
     * @param resource Path to the resource that needs to be migrated,
     * @param context The migration context for tracking operations and their results
     */

    @Override
    public void migrate(Path cartridgeDir, MigrationContext context)
    {
        Path staticFilesFolder = cartridgeDir.resolve("staticfiles");
        Path staticshareFolder = staticFilesFolder.resolve("share");
        Path cartridgeName = cartridgeDir.getName(cartridgeDir.getNameCount() - 1);
        ConfigurationXMLBuilder configurationXMLBuilder = new ConfigurationXMLBuilder(cartridgeName.getFileName().toString());
        Path sourceMain = cartridgeDir.resolve("src/main");
        String migrationSubject = cartridgeName.getFileName().toString();

        LOGGER.info("Processing cartridge {} in {}.", cartridgeName, cartridgeDir.toFile().getAbsolutePath());

        try
        {
            List<Path> toBeMigrated = List.of( staticshareFolder, sourceMain);
            for (Path path : toBeMigrated)
            {

                if (path.toFile().isDirectory())
                {
                    LOGGER.debug("Processing  files {}.", path);
                }
                else
                {
                    LOGGER.warn("Can't find  files {}.", path);
                    continue;
                }
                Files.walk(path)
                     .filter(p -> p.getNameCount() > staticFilesFolder.getNameCount() + 1)
                     .map(p -> p.subpath(1 + staticFilesFolder.getNameCount(), p.getNameCount()))
                     .filter(this::shouldMigrate)
                     .forEach(p -> {
                         try
                         {
                             Path source = path.resolve(p);
                             if (!source.toFile().isDirectory())
                             {
                                 Path targetFile = getTarget(cartridgeName, p, sourceMain);
                                 String targetFileName = targetFile.getFileName().toString();

                                 targetFile.toFile().getParentFile().mkdirs();

                                 String targetType = "";
                                 // resource files must be cinverted @see CfgResourceConverter
                                 if ((targetFileName.endsWith(".resource")) && (-1 < targetFileName.lastIndexOf('_')))
                                 {
                                     targetType = targetFileName.substring(targetFileName.lastIndexOf('_') + 1,
                                                     targetFileName.lastIndexOf(".resource"));
                                 }
                                 if (!targetType.isEmpty())
                                 {
                                     // convert resource file to properties file
                                     String targetName = targetFile.toFile().getAbsolutePath();
                                     targetName = targetName.replace(".resource", ".properties");
                                     Path target = Paths.get(targetName);
                                     
                                     if( convertResourceFile(targetType, source, target))
                                     {
                                        Files.delete(source);

                                        String domainName = target.getParent().getFileName().toString();
                                        configurationXMLBuilder.addLine(targetType, domainName, targetName );
                                        context.recordSuccess(migrationSubject, MODIFY, source, target);
                                     }
                                     else
                                     {
                                         context.recordFailure(migrationSubject, MODIFY, source, targetFile,
                                                 "conversion of resource file failed.");
                                     }
                                 }
                                 else
                                 {
                                     // other than resouirce files are just moved to their new location
                                     context.recordFailure(migrationSubject, MODIFY, source, targetFile, "target Type is unkonwn.");
                                 }
                             }
                         }
                         catch(Exception e)
                         {
                             context.recordFailure(migrationSubject, MODIFY, path, p, e.getMessage());
                             throw new RuntimeException(e);
                         }
                     });
            }
            if(configurationXMLBuilder.getGeneratedEntriesCount() > 0)
            {
                LOGGER.info("Generating configuration.xml for cartridge {}.", cartridgeName);
                Path configurationXMFilePath = cartridgeDir.resolve("src/main/resources/resources")
                        .resolve(cartridgeName).resolve("config").resolve("configuration.xml");
                if(!configurationXMFilePath.toFile().exists())
                {
                    Files.createFile(configurationXMFilePath);
                }
                FileUtils.writeLines(configurationXMFilePath, 
                    configurationXMLBuilder.generateConfigXML());          
            }
            else
            {
                LOGGER.info("No configuration.xml needed for cartridge {}.", cartridgeName);
            }

        }
        catch(IOException e)
        {
            throw new RuntimeException(e);
        }
    }
    /**
     * resolve the target path to place the converted resource (= property) file.
     * 
     * @param cartridgeName - the cartridge name
     * @param source - the source file path
     * @param sourceMain the src/main path
     * @return targetPath the target path to write the resulting property  file to
     */
    private Path getTarget(Path cartridgeName, Path source, Path sourceMain)
    {
        Path targetPath = sourceMain.resolve("resources"+java.io.File.separator+"resources").resolve(cartridgeName);
        // others -> src/main/resources/resources/{cartridgeName}
        Path targetSub = source.subpath(1, source.getNameCount());
        targetPath = targetPath.resolve(targetSub);
        return targetPath;
    }

    /** returns true if the file is a .resource file to be migrated
     * @param path containing the file name to be verified
     */
    private boolean shouldMigrate(Path path)
    {
        return path.getFileName().toString().endsWith(".resource");
    }

    /**
     * Convert resource files to properties files.
     **/
    private boolean convertResourceFile(String resurceCfgType, Path source, Path target)
    {
        boolean success = true;
        CfgResourceConverter converter = new CfgResourceConverter(resurceCfgType, source, target);
        try {
            converter.convertResource();
        } catch (IOException e) {
            success = false;
            LOGGER.error("Error reading file: " + source, e);
            e.printStackTrace();
        }
        return success;

    }

}
