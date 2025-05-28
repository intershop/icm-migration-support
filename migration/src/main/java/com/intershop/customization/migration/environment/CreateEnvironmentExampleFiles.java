package com.intershop.customization.migration.environment;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.intershop.customization.migration.common.MigrationContext;
import com.intershop.customization.migration.common.MigrationPreparer;
import com.intershop.customization.migration.common.MigrationStep;
import com.intershop.customization.migration.utils.FileUtils;

/**
 * This class creates example files in the project root with content specific for this environment.
 * <ul>
 *   <li>environment.bat.example created in project root from environment.bat.example.template, using rootProject.name in settings.gradle.kts
 *   <li>icm.properties.example created in project root from icm.properties.example.template, using rootProject.name in settings.gradle.kts
 *   <li>clean.bat created in project root from clean.bat.template, using all cartridges existing in project root
 * </ul>
 */
public class CreateEnvironmentExampleFiles implements MigrationPreparer
{
    private final Logger LOGGER = LoggerFactory.getLogger(getClass());

    protected Path fileTemplatesDir;

    public static final String LINE_SEP = System.lineSeparator();

    public static final String SETTINGS_GRADLE_KTS = "settings.gradle.kts";  // settings.gradle.kts file in project root
    public static final String ROOT_PROJECT_NAME   = "rootProject.name";     // rootProject.name key in settings.gradle.kts, with value e.g. "edezz-icm"
    protected String rootProjectName = null;
    public static final String GRADLE_PROPERTIES = "gradle.properties";  // gradle.properties file in project root
    public static final String DOCKER_REGISTRY   = "dockerRegistry";     // dockerRegistry key in gradle.properties, with value e.g. "ishedezzacr.azurecr.io"
    protected String dockerRegistry = null;

    public static final String ENVIRONMENT_BAT_EXAMPLE_TEMPLATE = "environment.bat.example.template";
    public static final String ENVIRONMENT_BAT_EXAMPLE          = "environment.bat.example";
    public static final String ICM_PROPERTIES_EXAMPLE_TEMPLATE  = "icm.properties.example.template";
    public static final String ICM_PROPERTIES_EXAMPLE           = "icm.properties.example";
    public static final String TEMPLATE_PLACEHOLDER_ROOT_PROJECT_NAME = "<rootProject.name in settings.gradle.kts>";
    public static final String TEMPLATE_PLACEHOLDER_DOCKER_REGISTRY   = "<ishprjxxacr>";

    public static final String CLEAN_BAT_TEMPLATE               = "clean.bat.template";
    public static final String CLEAN_BAT                        = "clean.bat";
    public static final String CLEAN_BAT_TEMPLATE_PLACEHOLDER_CARTRIDGENAME          = "{cartridgeName}";
    public static final String CLEAN_BAT_TEMPLATE_PLACEHOLDER_CARTRIDGENAME_LAST     = "{cartridgeName.last}";
    public static final String CLEAN_BAT_TEMPLATE_PLACEHOLDER_CARTRIDGENAME_NOT_LAST = "{cartridgeName.!last}";

    @Override
    public void setStep(MigrationStep step)
    {
        LOGGER.debug("START executing method  setStep");

        this.fileTemplatesDir = getFileTemplatesDir();

        LOGGER.debug("END   executing method  setStep");
    }

    @Override
    public void migrateRoot(Path projectDir, MigrationContext context)
    {
        LOGGER.debug("START executing method  migrateRoot");

        List<String> cartridgeNames = new ArrayList<>(100);

        File icmProjectRoot = projectDir.toFile();
        File[] files = icmProjectRoot.listFiles();
        if (files != null)
        {
            for (File dirOrFileInICMProjectRoot: files)
            {
                String name = dirOrFileInICMProjectRoot.getName();

                // dirOrFileInICMProjectRoot is a cartridge if:
                //     1. It is a directory
                //     2. Does not start with a dot
                //     3. Contains a build.gradle or build.gradle.kts file
                // A "src" folder is not required!
                if (dirOrFileInICMProjectRoot.isDirectory()
                    && !name.startsWith(".")
                    && ((new File(dirOrFileInICMProjectRoot, "build.gradle")).exists() || (new File(dirOrFileInICMProjectRoot, "build.gradle.kts")).exists()))
                {
                    cartridgeNames.add(name);
                    LOGGER.debug("Found cartridge: '{}'", name);
                }

                if (name.equals(SETTINGS_GRADLE_KTS))
                {
                    LOGGER.debug("Found {}", name);
                    rootProjectName = getValueFromPropertiesOrKTSFile(dirOrFileInICMProjectRoot, ROOT_PROJECT_NAME);
                    LOGGER.info("{} read from {}: '{}'", ROOT_PROJECT_NAME, name, rootProjectName);
                }

                if (name.equals(GRADLE_PROPERTIES))
                {
                    LOGGER.debug("Found {}", name);
                    dockerRegistry = getValueFromPropertiesOrKTSFile(dirOrFileInICMProjectRoot, DOCKER_REGISTRY);
                    LOGGER.info("{} read from {}: '{}'", DOCKER_REGISTRY, name, dockerRegistry);
                }

                if (name.equalsIgnoreCase(ENVIRONMENT_BAT_EXAMPLE))
                {
                    LOGGER.warn("File '{}' already exists, content will be replaced.", name);
                }

                if (name.equalsIgnoreCase(ICM_PROPERTIES_EXAMPLE))
                {
                    LOGGER.warn("File '{}' already exists, content will be replaced.", name);
                }

                if (name.equalsIgnoreCase(CLEAN_BAT))
                {
                    LOGGER.warn("File '{}' already exists, content will be replaced.", name);
                }
            }
        }

        //
        // environment.bat.example
        //

        File environmentBatExampleTemplate = new File(fileTemplatesDir.toFile(), ENVIRONMENT_BAT_EXAMPLE_TEMPLATE);
        if (environmentBatExampleTemplate.exists() && environmentBatExampleTemplate.isFile())
        {
            Path environmentBatExample = icmProjectRoot.toPath().resolve(ENVIRONMENT_BAT_EXAMPLE);
            createOrReplaceEnvironmentBatExample(environmentBatExampleTemplate, environmentBatExample, rootProjectName, dockerRegistry);
        }
        else
        {
            LOGGER.error("File '{}' not found in '{}'.", ENVIRONMENT_BAT_EXAMPLE_TEMPLATE, fileTemplatesDir);
        }

        //
        // icm.properties.example
        //

        File icmPropertiesExampleTemplate = new File(fileTemplatesDir.toFile(), ICM_PROPERTIES_EXAMPLE_TEMPLATE);
        if (icmPropertiesExampleTemplate.exists() && icmPropertiesExampleTemplate.isFile())
        {
            Path icmPropertiesExample = icmProjectRoot.toPath().resolve(ICM_PROPERTIES_EXAMPLE);
            createOrReplaceIcmPropertiesExample(icmPropertiesExampleTemplate, icmPropertiesExample, rootProjectName);
        }
        else
        {
            LOGGER.error("File '{}' not found in '{}'.", ICM_PROPERTIES_EXAMPLE_TEMPLATE, fileTemplatesDir);
        }

        //
        // clean.bat
        //

        File cleanBatTemplate = new File(fileTemplatesDir.toFile(), CLEAN_BAT_TEMPLATE);
        if (cleanBatTemplate.exists() && cleanBatTemplate.isFile())
        {
            Path cleanBat = icmProjectRoot.toPath().resolve(CLEAN_BAT);
            createOrReplaceCleanBAT(cleanBatTemplate, cleanBat, cartridgeNames);
        }
        else
        {
            LOGGER.error("File '{}' not found in '{}'.", CLEAN_BAT_TEMPLATE, fileTemplatesDir);
        }

        LOGGER.debug("END   executing method  migrateRoot");
    }

    /**
     * Directory containing environment.bat.example.template, icm.properties.example.template, clean.bat.template files.
     * @return directory containing the example file templates
     */
    protected Path getFileTemplatesDir()
    {
        final Path fileTemplatesDir = Paths.get("src/main/resources/environment");
        if (!Files.exists(fileTemplatesDir) || !Files.isDirectory(fileTemplatesDir))
        {
            LOGGER.error("Directory src/main/resources/environment, containing the example file templates, not found. Absolute path: '{}'.", fileTemplatesDir.toAbsolutePath().toString());
        }

        return fileTemplatesDir;
    }

    /**
     * Retrieves the value associated with a given key from a properties or KTS file.
     * The method supports two formats:
     * <ul>
     *   <li><code>key = value</code></li>
     *   <li><code>key = "value"...</code></li>
     * </ul>
     * If the value is enclosed in quotes, the quotes are stripped.
     * If the value is empty, an INFO log is written.
     * 
     * @TODO: Rework method to support more formats.
     *
     * @param propertiesOrKTSFile The file containing the key-value pairs, usually a properties, KTS or Groovy/Gradle file.
     * @param key The key whose associated value is to be retrieved.
     * @return The value associated with the key, or <code>null</code> if the key was not found or could not be parsed.
     */
    protected String getValueFromPropertiesOrKTSFile(File propertiesOrKTSFile, String key)
    {
        String value = null;

        try {
            List<String> linesStream = FileUtils.readAllLines(propertiesOrKTSFile.toPath());
            for (String line : linesStream)
            {
                String[] parts = line.split("=");
                if (parts.length >= 2
                    && parts[0].trim().equals(key))
                {
                    // line is in the format:  key = value
                    //      or in the format:  key = "value" ...
                    String partAfterEquals = parts[1].trim();
                    if (partAfterEquals.length() > 1
                        && partAfterEquals.startsWith("\""))
                    {
                        // line is in the format:  key = "value" ...
                        int i = partAfterEquals.indexOf("\"", 1);
                        if (i == -1)
                        {
                            LOGGER.error("Unable to retrieve value for {}, syntax too complex, could not find ending quote \" of value starting with quote \", (line: '{}', file: {}).", key, line, propertiesOrKTSFile.getName());
                            value = null;
                        }
                        else
                        {
                            if (i == 1)
                            {
                                LOGGER.info("Value for {} is empty (\"\"), (line: '{}', file: {}).", key, line, propertiesOrKTSFile.getName());
                            }

                            value = partAfterEquals.substring(1, i);
                            LOGGER.debug("Value for {} is '{}', (line: '{}', file: {}).", key, value, line, propertiesOrKTSFile.getName());
                        }
                    }
                    else
                    {
                        // line is in the format:  key = value
                        value = partAfterEquals.trim();
                        if (value.length() == 0)
                        {
                            LOGGER.info("Value for {} is empty, (line: '{}', file: {}).", key, line, propertiesOrKTSFile.getName());
                        }

                        LOGGER.debug("Value for {} is '{}', (line: '{}', file: {}).", key, value, line, propertiesOrKTSFile.getName());
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("Exception reading propertiesOrKTSFile " + propertiesOrKTSFile.getName(), e);
        }

        return value;
    }

    /**
     * Creates or replaces the environment.bat.example file in project root,
     * based on a template file.
     *
     * @param environmentBatExampleTemplate The template file containing placeholders.
     * @param environmentBatExample         The path to the output file where the processed content will be written.
     *                                      If the file already exists, its content will be replaced.
     * @param rootProjectName               rootProject.name from settings.gradle.kts, used to replace placeholders <rootProject.name in settings.gradle.kts>.
     *                                      If null, the placeholder will remain in the output file.
     * @param dockerRegistry                dockerRegistry configured in gradle.properties, used to replace placeholders <ishprjxxacr>.
     *                                      If null, the placeholder will remain in the output file.
     */
    protected void createOrReplaceEnvironmentBatExample(File environmentBatExampleTemplate, Path environmentBatExample, String rootProjectName, String dockerRegistry)
    {
        LOGGER.debug("START executing method  createOrReplaceEnvironmentBatExample");

        try {
            if (rootProjectName == null)
            {
                LOGGER.warn("No {} found. {} will contain placeholders {}.", ROOT_PROJECT_NAME, environmentBatExample.getFileName(), TEMPLATE_PLACEHOLDER_ROOT_PROJECT_NAME);
            }

            if (dockerRegistry == null)
            {
                LOGGER.warn("No {} found. {} will contain placeholders {}.", DOCKER_REGISTRY, environmentBatExample.getFileName(), TEMPLATE_PLACEHOLDER_DOCKER_REGISTRY);
            }

            String resultingFileContent = "";

            List<String> lines = FileUtils.readAllLines(environmentBatExampleTemplate.toPath());
            for (String line : lines)
            {
                if (rootProjectName != null)
                {
                    // replace all occurrences in line
                    line = line.replace(TEMPLATE_PLACEHOLDER_ROOT_PROJECT_NAME, rootProjectName);
                }

                if (dockerRegistry != null)
                {
                    // replace all occurrences in line
                    line = line.replace(TEMPLATE_PLACEHOLDER_DOCKER_REGISTRY, dockerRegistry);
                }

                resultingFileContent += line + LINE_SEP;
            }
    
            FileUtils.writeString(environmentBatExample, resultingFileContent);
            LOGGER.info("Content of file '{}' successfully written.", environmentBatExample.toAbsolutePath().toString());
        } catch (IOException e) {
            LOGGER.error("Exception reading or writing " + environmentBatExample.getFileName() + " / " + environmentBatExampleTemplate.getName(), e);
        }

        LOGGER.debug("END   executing method  createOrReplaceEnvironmentBatExample");
    }

    /**
     * Creates or replaces the icm.properties.example file in project root,
     * based on a template file.
     *
     * @param icmPropertiesExampleTemplate The template file containing placeholders.
     * @param icmPropertiesExample         The path to the output file where the processed content will be written.
     *                                     If the file already exists, its content will be replaced.
     * @param rootProjectName              rootProject.name from settings.gradle.kts, used to replace placeholders <rootProject.name in settings.gradle.kts>.
     *                                     If null, the placeholder will remain in the output file.
     */
    protected void createOrReplaceIcmPropertiesExample(File icmPropertiesExampleTemplate, Path icmPropertiesExample, String rootProjectName)
    {
        LOGGER.debug("START executing method  createOrReplaceIcmPropertiesExample");

        try {
            if (rootProjectName == null)
            {
                LOGGER.warn("No {} found. {} will contain placeholders {}.", ROOT_PROJECT_NAME, icmPropertiesExample.getFileName(), TEMPLATE_PLACEHOLDER_ROOT_PROJECT_NAME);
            }

            String resultingFileContent = "";

            List<String> lines = FileUtils.readAllLines(icmPropertiesExampleTemplate.toPath());
            for (String line : lines)
            {
                if (rootProjectName != null)
                {
                    // replace all occurrences in line
                    line = line.replace(TEMPLATE_PLACEHOLDER_ROOT_PROJECT_NAME, rootProjectName);
                }

                resultingFileContent += line + LINE_SEP;
            }
    
            FileUtils.writeString(icmPropertiesExample, resultingFileContent);
            LOGGER.info("Content of file '{}' successfully written.", icmPropertiesExample.toAbsolutePath().toString());
        } catch (IOException e) {
            LOGGER.error("Exception reading or writing " + icmPropertiesExample.getFileName() + " / " + icmPropertiesExampleTemplate.getName(), e);
        }

        LOGGER.debug("END   executing method  createOrReplaceIcmPropertiesExample");
    }

    /**
     * Creates or replaces the clean.bat file in project root,
     * based on a template file.
     *
     * @param cleanBatTemplate The template file containing placeholders.
     * @param cleanBat         The path to the output file where the processed content will be written.
     *                         the file already exists, its content will be replaced.
     * @param cartridgeNames   List of all cartridge directory names that should be cleaned by clean.bat.
     */
    protected void createOrReplaceCleanBAT(File cleanBatTemplate, Path cleanBat, List<String> cartridgeNames)
    {
        LOGGER.debug("START executing method  createOrReplaceCleanBAT");

        try {
            if (cartridgeNames.isEmpty())
            {
                LOGGER.warn("No cartridges found in the project. {} will contain placeholders like {}.", cleanBat.getFileName(), CLEAN_BAT_TEMPLATE_PLACEHOLDER_CARTRIDGENAME);
            }

            String resultingFileContent = "";

            List<String> lines = FileUtils.readAllLines(cleanBatTemplate.toPath());
            for (String line : lines)
            {
                if (!cartridgeNames.isEmpty())
                {
                    String resultingLines = replacePlaceholderWithOneLinePerCartridge(line, cartridgeNames);
                    resultingFileContent += resultingLines;
                }
                else
                {
                    resultingFileContent += line + LINE_SEP;
                }
            }
    
            FileUtils.writeString(cleanBat, resultingFileContent);
            LOGGER.info("Content of file '{}' successfully written.", cleanBat.toAbsolutePath().toString());
        } catch (IOException e) {
            LOGGER.error("Exception reading or writing " + cleanBat.getFileName() + " / " + cleanBatTemplate.getName(), e);
        }

        LOGGER.debug("END   executing method  createOrReplaceCleanBAT");
    }

    /**
     * Replaces placeholders in the given line with cartridge names, possibly creating multiple lines, one per cartridge.
     * Supported placeholders:
     * <ul>
     *   <li><code>{cartridgeName}</code>: Replaces the placeholder with one line per cartridge.
     *   <li><code>{cartridgeName.last}</code>: Replaces the placeholder with a single line for the last cartridge in the list.
     *   <li><code>{cartridgeName.!last}</code>: Replaces the placeholder with one line per cartridge, excluding the last one in the list.
     * </ul>
     *
     * @param line           The input line containing placeholders to be replaced.
     * @param cartridgeNames List of all cartridge directory names that should be cleaned by clean.bat.
     * @return A string with placeholders replaced according to the rules described above.
     */
    protected String replacePlaceholderWithOneLinePerCartridge(String line, List<String> cartridgeNames)
    {
        String result = "";

        if (line.contains(CLEAN_BAT_TEMPLATE_PLACEHOLDER_CARTRIDGENAME))
        {
            // line contains one or more occurrences of "{cartridgeName}",
            // create one line per cartridge
            for (String cartridgeName : cartridgeNames)
            {
                String replacedLine = line.replace(CLEAN_BAT_TEMPLATE_PLACEHOLDER_CARTRIDGENAME, cartridgeName);
                result += replacedLine + LINE_SEP;
            }
        }
        else if (line.contains(CLEAN_BAT_TEMPLATE_PLACEHOLDER_CARTRIDGENAME_LAST))
        {
            // line contains one or more occurrences of "{cartridgeName.last}",
            // create one line for the last cartridge
            String cartridgeName = cartridgeNames.getLast();
            String replacedLine = line.replace(CLEAN_BAT_TEMPLATE_PLACEHOLDER_CARTRIDGENAME_LAST, cartridgeName);
            result += replacedLine + LINE_SEP;
        }
        else if (line.contains(CLEAN_BAT_TEMPLATE_PLACEHOLDER_CARTRIDGENAME_NOT_LAST))
        {
            // line contains one or more occurrences of "{cartridgeName.!last}",
            // create one line per cartridge, except for the last cartridge
            int size = cartridgeNames.size();
            int number = 0;  // first cartridge has number=1, last cartridge has number=size
            for (String cartridgeName : cartridgeNames)
            {
                number++;
                if (number < size)
                {
                    String replacedLine = line.replace(CLEAN_BAT_TEMPLATE_PLACEHOLDER_CARTRIDGENAME_NOT_LAST, cartridgeName);
                    result += replacedLine + LINE_SEP;
                }
            }
        }
        else
        {
            result += line + LINE_SEP;
        }

        return result;
    }
}
