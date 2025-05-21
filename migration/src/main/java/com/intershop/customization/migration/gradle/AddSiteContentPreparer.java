package com.intershop.customization.migration.gradle;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import com.intershop.customization.migration.common.MigrationPreparer;
import com.intershop.customization.migration.parser.DBInitPropertiesParser;
import com.intershop.customization.migration.utils.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is used to check if the project contains a 'sites' folder.
 * If so, it checks the 'dbinit.properties' file is extended with a
 * SiteContentPreparer entry.
 * <p>
 * Example YAML configuration:
 * <pre>
 * type: specs.intershop.com/v1beta/migrate
 * migrator: com.intershop.customization.migration.gradle.AddSiteContentPreparer
 * message: "refactor: inject SiteContentPreparer into 'dbinit.properties'"
 * </pre>
 */
public class AddSiteContentPreparer implements MigrationPreparer
{
    private final Logger LOGGER = LoggerFactory.getLogger(getClass());

    private static final String LINE_SEP = System.lineSeparator();

    @Override
    public void migrate(Path projectDir)
    {
        LOGGER.debug("starting SiteContent migration for project {}", projectDir);
        Optional<Path> sitesFolderOptional = getSitesFolder(projectDir);

        if (sitesFolderOptional.isPresent())
        {
            // sites folder found - get or create dbinit.properties
            Optional<Path> dbinitPropsOptional = getDBinitProperties(projectDir);

            if (dbinitPropsOptional.isPresent())
            {
                Path dbinitProperties = dbinitPropsOptional.get();
                try
                {
                    if (FileUtils.containsText(dbinitProperties, "com.intershop.site.dbinit.SiteContentPreparer"))
                    {
                        LOGGER.debug("SiteContentPreparer already registered in file '{}'. Nothing to do.", dbinitPropsOptional);
                        return;
                    }

                    List<String> lines = FileUtils.readAllLines(dbinitProperties);
                    DBInitPropertiesParser parser = new DBInitPropertiesParser(lines);
                    List<DBInitPropertiesParser.LineEntry> parsedLines = parser.getParsedLines();
                    DBInitPropertiesParser.PropertyEntry highestPreEntry = parser.getHighestIdEntry(DBInitPropertiesParser.GroupType.PRE);
                    DBInitPropertiesParser.PropertyEntry firstMainEntry = parser.getFirstEntry(DBInitPropertiesParser.GroupType.MAIN);

                    FileUtils.writeString(dbinitProperties, injectSiteContentPreparer(parsedLines, highestPreEntry, firstMainEntry));

                    LOGGER.warn("SiteContentPreparer was added to file '{}'. Please remove possible 'Copy' tasks from '{}'", dbinitPropsOptional, projectDir.resolve("build.gradle"));
                }
                catch(IOException e)
                {
                    LOGGER.error("Can't register SiteContentPreparer in file {}", dbinitPropsOptional, e);
                }
            }
        }
    }

    /**
     * Looks up and returns the sites folder of the cartridge. It checks the
     * old location first and then the new location.
     *
     * @param projectDir the project dir to check.
     * @return the sites folder of the cartridge, if present. If not, an empty
     *         optional is returned.
     */
    protected Optional<Path> getSitesFolder(Path projectDir)
    {
        Path oldSitesFolder = projectDir.resolve("staticfiles").resolve("share").resolve("sites");

        if (oldSitesFolder.toFile().isDirectory() && oldSitesFolder.toFile().exists())
        {
            LOGGER.debug("project contains 'sites' folder at '{}'", oldSitesFolder);
            return Optional.of(oldSitesFolder);
        }

        String cartridgeName = getResourceName(projectDir);
        Path cartridgeResources = projectDir.resolve("src")
                                            .resolve("main")
                                            .resolve("resources")
                                            .resolve("resources")
                                            .resolve(cartridgeName);
        Path newSitesFolder = cartridgeResources.resolve("sites");
        if (newSitesFolder.toFile().isDirectory() && newSitesFolder.toFile().exists())
        {
            LOGGER.debug("project contains 'sites' folder at '{}'", newSitesFolder);
            return Optional.of(newSitesFolder);
        }

        return Optional.empty();
    }

    /**
     * Looks up the 'dbinit.properties' file. The new location is preferred.
     * If the file is not found there, the old location is checked.
     * The method bases on the precondition that the 'sites' folder was
     * found and the SiteContentPreparer has to be present in the 'dbinit.properties'.
     * Means a new file will be created in the new location, if no other was found.
     *
     * @param projectDir the project dir to check.
     * @return the dbinit.properties file of the cartridge.
     */
    protected Optional<Path> getDBinitProperties(Path projectDir)
    {
        String cartridgeName = getResourceName(projectDir);
        Path cartridgeResources = projectDir.resolve("src")
                                            .resolve("main")
                                            .resolve("resources")
                                            .resolve("resources")
                                            .resolve(cartridgeName);
        Path newDBInitProperties = cartridgeResources.resolve("dbinit.properties");

        if (newDBInitProperties.toFile().isFile() && newDBInitProperties.toFile().exists())
        {
            LOGGER.debug("'dbinit.properties' to have SiteContentPreparer located at '{}'", newDBInitProperties);
            return Optional.of(newDBInitProperties);
        }

        Path oldDBinitProperties = projectDir.resolve("staticfiles").resolve("cartridge").resolve("dbinit.properties");

        if (oldDBinitProperties.toFile().isFile() && oldDBinitProperties.toFile().exists())
        {
            LOGGER.debug("'dbinit.properties' to have SiteContentPreparer located at '{}'", oldDBinitProperties);
            return Optional.of(oldDBinitProperties);
        }

        try
        {
            LOGGER.debug("No 'dbinit.properties' found. Creating new file at '{}'", newDBInitProperties);

            if (!cartridgeResources.toFile().exists())
            {
                LOGGER.debug("Source directory '{}' to store 'dbinit.properties' not present. Creating.", newDBInitProperties);
                Files.createDirectories(cartridgeResources);
            }

            return Optional.of(Files.createFile(newDBInitProperties));
        }
        catch(IOException e)
        {
            LOGGER.error("Error while creating file '{}': ", newDBInitProperties, e);
            return Optional.empty();
        }
    }

    /**
     * Adds the SiteContentPreparer to the dbinit.properties file at the first possible position.
     * The preparer is added to the 'pre' namespace with the first available class ID.
     * <p>
     * Assumption: The check if the SiteContentPreparer is already registered is done beforehand.
     *
     * @param parsedLines parsed content of the dbinit.properties file
     * @param highestPreEntry the highest entry in the file with prefix 'pre.Class'
     * @param firstMainEntry the first main entry in the file (prefix 'Class')
     * @return new content to be written to the dbinit.properties file
     */
    protected String injectSiteContentPreparer(List<DBInitPropertiesParser.LineEntry> parsedLines,
                    DBInitPropertiesParser.PropertyEntry highestPreEntry,
                    DBInitPropertiesParser.PropertyEntry firstMainEntry)
    {
        StringBuilder result = new StringBuilder();
        boolean added = false;

        for (DBInitPropertiesParser.LineEntry line : parsedLines)
        {
            if (line instanceof DBInitPropertiesParser.PropertyEntry propertyEntry)
            {
                if (!added && highestPreEntry != null && propertyEntry == highestPreEntry)
                {
                    // add last line of the pre section
                    result.append(propertyEntry.text()).append(LINE_SEP);
                    appendSiteContentPreparerEntry(result, highestPreEntry.id()+1);
                    added = true;
                }
                else if (!added && propertyEntry == firstMainEntry)
                {
                    // add the SiteContentPreparer first
                    appendSiteContentPreparerEntry(result, 0);
                    result.append(propertyEntry.text()).append(LINE_SEP);
                    added = true;
                }
                else
                {
                    // not yet added, but appropriate line not yet found
                    result.append(line.text()).append(LINE_SEP);
                }
            }
            else
            {
                // handle all other lines
                result.append(line.text()).append(LINE_SEP);
            }
        }

        return result.toString();
    }

    /**
     * Adds the SiteContentPreparer entry to the dbinit.properties file.
     * @param result the StringBuilder to append the entry to
     * @param id the ID to use for the SiteContentPreparer entry
     */
    private void appendSiteContentPreparerEntry(StringBuilder result, Integer id)
    {
        // add the SiteContentPreparer
        result.append(LINE_SEP)
              .append("# Prepare sites-folder")
              .append(LINE_SEP);
        result.append("pre.Class")
              .append(id)
              .append("=com.intershop.site.dbinit.SiteContentPreparer")
              .append(LINE_SEP)
              .append(LINE_SEP);
    }
}
