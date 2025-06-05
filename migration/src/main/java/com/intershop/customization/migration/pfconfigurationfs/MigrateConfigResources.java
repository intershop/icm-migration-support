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

    private static final String YAML_KEY_CONFIGURATION_XML = "configuration-xml";


    public static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(MigrateConfigResources.class);

    /* w.i.p.
    @Override
    public void setStep(MigrationStep step)
    {
        this.configurationXML = step.getOption(YAML_KEY_CONFIGURATION_XML);
    }
    */

       /**
     * Migrates a resource with context tracking.
     * It allows recording success, failures, and other metrics.
     *
     * @param resource Path to the resource that needs to be migrated
     * @param context The migration context for tracking operations and their results
     */

    @Override
    public void migrate(Path cartridgeDir, MigrationContext context)
    {
        Path staticFilesFolder = cartridgeDir.resolve("staticfiles");
        Path staticCartridgeFolder = staticFilesFolder.resolve("cartridge");
        Path staticshareFolder = staticFilesFolder.resolve("share");
        Path staticSitesFolder = staticFilesFolder.resolve("sites");
        Path cartridgeName = cartridgeDir.getName(cartridgeDir.getNameCount() - 1);
        ConfigurationXMLBuilder configurationXMLBuilder = new ConfigurationXMLBuilder(cartridgeName.getFileName().toString());
        Path sourceMain = cartridgeDir.resolve("src/main");
        String migrationSubject = cartridgeName.getFileName().toString();

        if (!staticCartridgeFolder.toFile().exists())
        {
            LOGGER.debug("Can't find cartridges static folder {}.", staticCartridgeFolder);
            return;
        }
        LOGGER.info("Processing cartridges {} in {}.", cartridgeName, staticCartridgeFolder.toFile().getAbsolutePath());

        try
        {
            List<Path> toBeMigrated = List.of(staticCartridgeFolder, staticshareFolder, staticSitesFolder, sourceMain);
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

    private Path getTarget(Path cartridgeName, Path source, Path sourceMain)
    {
        String fileName = source.getName(0).toString();
        Path targetPath = sourceMain.resolve("resources/resources").resolve(cartridgeName);
        if (targetPath.toString()
                      .contains("resources" + java.io.File.separator + "resources" + java.io.File.separator
                                      + cartridgeName))
        {
            switch(fileName)
            {
                case "domains":
                    // staticfiles/share/system/config/domains ->
                    // src/main/resources/resources/{cartridgeName}/config/domains
                    Path targetSubDomains = source.subpath(2, source.getNameCount());
                    targetPath = targetPath.resolve(targetSubDomains);
                    break;
                case "system":
                    // staticfiles/share/system/config
                    // -> src/main/resources/resources/{cartridgeName}/config
                    Path targetSubConfig = source.subpath(1, source.getNameCount());
                    targetPath = targetPath.resolve(targetSubConfig);
                    break;
                case "cartridge":
                    // staticfiles/share/sites
                    // -> src/main/resources/resources/{cartridgeName}/sites
                    Path targetSubSites = source.subpath(2, source.getNameCount());
                    targetPath = targetPath.resolve(targetSubSites);
                    break;
                default:
                    // others -> src/main/resources/resources/{cartridgeName}
                    Path targetSub = source.subpath(3, source.getNameCount());
                    targetPath = targetPath.resolve(targetSub);
            }
        }
        return targetPath;
    }

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
