package com.intershop.customization.migration.gradle;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.intershop.customization.migration.common.MigrationPreparer;

/**
 * This class is used to
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

    private static final Charset CHARSET_BUILD_GRADLE = Charset.defaultCharset();
    private static final String LINE_SEP = System.lineSeparator();

    @Override
    public void migrate(Path projectDir)
    {
        LOGGER.debug("starting SiteContent migration for project {}", projectDir);
        Optional<Path> sitesFolderOptional = getSitesFolder(projectDir);

        if (sitesFolderOptional.isPresent())
        {
            // sites folder found - get or create dbinit.properties
            Path dbinitProperties = getDBinitProperties(projectDir);

            try (Stream<String> linesStream = Files.lines(dbinitProperties, CHARSET_BUILD_GRADLE))
            {
                List<String> lines = linesStream.toList();
                if (lines.stream().anyMatch(l -> l.contains("com.intershop.site.dbinit.SiteContentPreparer")))
                {
                    LOGGER.debug("SiteContentPreparer already registered in file '{}'. Nothing to do.", dbinitProperties);
                    return;
                }

                Files.writeString(dbinitProperties, injectSiteContentPreparer(lines), CHARSET_BUILD_GRADLE);
            }
            catch(IOException e)
            {
                LOGGER.error("Can't register SiteContentPreparer in file " + dbinitProperties, e);
            }

            // TODO remove possible enries in build.gradle (copy tasks)
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
            LOGGER.debug("project contains 'sites' folder at '{}'", oldSitesFolder);
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
    protected Path getDBinitProperties(Path projectDir)
    {
        String cartridgeName = getResourceName(projectDir);
        Path cartridgeResources = projectDir.resolve("src")
                                            .resolve("main")
                                            .resolve("resources")
                                            .resolve("resources")
                                            .resolve(cartridgeName);
        Path newDBInitProperties = cartridgeResources.resolve("dbinit.properties");

        // TODO open: check file attributes (writable)
        if (newDBInitProperties.toFile().isFile() && newDBInitProperties.toFile().exists())
        {
            LOGGER.debug("'dbinit.properties' to have SiteContentPreparer located at '{}'", newDBInitProperties);
            return newDBInitProperties;
        }

        Path oldDBinitProperties = projectDir.resolve("staticfiles").resolve("cartridge").resolve("dbinit.properties");

        if (oldDBinitProperties.toFile().isFile() && oldDBinitProperties.toFile().exists())
        {
            LOGGER.debug("'dbinit.properties' to have SiteContentPreparer located at '{}'", oldDBinitProperties);
            return oldDBinitProperties;
        }

        try
        {
            LOGGER.debug("No 'dbinit.properties' found. Creating new file at '{}'", newDBInitProperties);
            // TODO open: check permission to create file (writable)
            return Files.createFile(newDBInitProperties);
        }
        catch(IOException e)
        {
            throw new RuntimeException("Could not create file " + newDBInitProperties, e);
        }
    }

    /**
     * Adds the SiteContentPreparer to the dbinit.properties file at the first
     * possible position. The preparer is added to the 'pre' namespace with the
     * first available class ID.
     * <p>
     * Assumption: The pre classes are in the file before the normal preparer
     * classes. All registered preparers are in ascending order. The check if
     * the SiteContentPreparer is already registered is done beforehand.
     *
     * @param lines current lines of the dbinit.properties file
     * @return string to be written to the dbinit.properties file
     */
    protected String injectSiteContentPreparer(List<String> lines)
    {
        StringBuilder result = new StringBuilder();
        int preparerClassID = 0;
        boolean added = false;

        for (String line : lines)
        {
            // skip empty lines, comments and other preparers in the 'pre' namespace
            String trimmedLine = line.trim();
            // TODO skip empty lines too - but current approach causes issues when doing so
            if (trimmedLine.startsWith("#") || line.trim().startsWith("pre.Class"))
            {
                if (line.trim().startsWith("pre.Class"))
                {
                    String[] pair = line.split("=");
                    String classID = pair[0].trim().replace("pre.Class", "");
                    if (classID.matches("\\d+"))
                    {
                        preparerClassID = Integer.parseInt(classID);
                        if (preparerClassID > 0)
                        {
                            preparerClassID++;
                        }
                    }
                }
                result.append(line).append(LINE_SEP);
                continue;
            }

            if (!added)
            {
                // add the new preparer
                result.append("# Prepare sites-folder")
                      .append(LINE_SEP);
                result.append("pre.Class").append(preparerClassID)
                      .append("=com.intershop.site.dbinit.SiteContentPreparer")
                      .append(LINE_SEP)
                      .append(LINE_SEP);
                added = true;
            }
            else
            {
                // add the rest of the lines
                result.append(line).append(LINE_SEP);
            }
        }

        return result.toString();
    }
}
