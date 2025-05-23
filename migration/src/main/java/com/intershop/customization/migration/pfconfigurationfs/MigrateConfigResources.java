package com.intershop.customization.migration.pfconfigurationfs;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.slf4j.LoggerFactory;

import com.intershop.customization.migration.common.MigrationPreparer;
import com.intershop.customization.migration.common.MigrationStep;

public class MigrateConfigResources implements MigrationPreparer
{

    private static final String YAML_KEY_CONFIGURATION_XML = "configuration-xml";

    private String configurationXML = "";


    public static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(MigrateConfigResources.class);

    /* w.i.p.
    @Override
    public void setStep(MigrationStep step)
    {
        this.configurationXML = step.getOption(YAML_KEY_CONFIGURATION_XML);
    }
    */

    @Override
    public void migrate(Path cartridgeDir)
    {
        Path staticFilesFolder = cartridgeDir.resolve("staticfiles");
        Path staticCartridgeFolder = staticFilesFolder.resolve("cartridge");
        Path staticshareFolder = staticFilesFolder.resolve("share");
        Path staticSitesFolder = staticFilesFolder.resolve("sites");
        Path cartridgeName = cartridgeDir.getName(cartridgeDir.getNameCount() - 1);
        Path sourceMain = cartridgeDir.resolve("src/main");

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
                                     convertResourceFile(targetType, source, target);
                                     Files.delete(source);
                                 }
                                 else
                                 {
                                     // other than resouirce files are just moved to their new location
                                     LOGGER.warn("file {} not yet handled.", source);
                                 }
                             }
                         }
                         catch(Exception e)
                         {
                             throw new RuntimeException(e);
                         }
                     });
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
    private void convertResourceFile(String resurceCfgType, Path source, Path target)
    {
        CfgResourceConverter converter = new CfgResourceConverter(resurceCfgType, source, target);
        converter.convertResource();

    }

}
