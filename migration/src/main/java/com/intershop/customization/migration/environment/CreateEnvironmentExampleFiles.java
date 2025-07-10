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
import com.intershop.customization.migration.utils.FileUtils;

/**
 * This class creates example files in the project root with content specific for this environment.
 * <ul>
 *   <li>environment.bat.example and environment.sh.example created in project root from environment.bat.example.template, using rootProject.name in settings.gradle.kts and dockerRegistry as well as adoOrganizationName in gradle.properties
 *   <ul>
 *     <li>Placeholder &lt;rootProject.name in settings.gradle.kts&gt; will be replaced by e.g. "prjzz-icm"
 *     <li>Placeholder &lt;ishprjxxacr&gt; will be replaced by e.g. "ishprjzzacr.azurecr.io"
 *     <li>Placeholder &lt;adoOrganizationName&gt; will be replaced by e.g. "ish-prjzz"
 *   </ul>
 *   <li>icm.properties.example created in project root from icm.properties.example.template, using rootProject.name in settings.gradle.kts
 *   <ul>
 *     <li>Placeholder &lt;rootProject.name in settings.gradle.kts&gt; will be replaced by e.g. "prjzz-icm"
 *   </ul>
 *   <li>clean.bat and clean.sh created in project root from clean.bat.template, using all cartridges existing in project root
 *   <ul>
 *     <li>{cartridgeName} will be replaced by one line per cartridge
 *     <li>{cartridgeName.last} will be replaced by the last cartridge in the list
 *     <li>{cartridgeName} will be replaced by one line per cartridge, except for the last cartridge in the list
 *   </ul>
 * </ul>
 */
public class CreateEnvironmentExampleFiles implements MigrationPreparer
{
    private static final Logger LOGGER = LoggerFactory.getLogger(CreateEnvironmentExampleFiles.class);

    public static final String LINE_SEP = System.lineSeparator();

    public static final String SETTINGS_GRADLE_KTS = "settings.gradle.kts";  // settings.gradle.kts file in project root
    public static final String ROOT_PROJECT_NAME   = "rootProject.name";     // rootProject.name key in settings.gradle.kts, with value e.g. "prjzz-icm"
    public static final String GRADLE_PROPERTIES = "gradle.properties";  // gradle.properties file in project root
    public static final String DOCKER_REGISTRY   = "dockerRegistry";     // dockerRegistry key in gradle.properties, with value e.g. "ishprjzzacr.azurecr.io"
    public static final String ADO_ORGANIZATION  = "adoOrganizationName";  // adoOrganizationName key in gradle.properties, with value e.g. "ish-prjzz"

    public static final String ENVIRONMENT_BAT_EXAMPLE_TEMPLATE = "environment.bat.example.template";
    public static final String ENVIRONMENT_BAT_EXAMPLE          = "environment.bat.example";
    public static final String ENVIRONMENT_SH_EXAMPLE_TEMPLATE  = "environment.sh.example.template";
    public static final String ENVIRONMENT_SH_EXAMPLE           = "environment.sh.example";
    public static final String ICM_PROPERTIES_EXAMPLE_TEMPLATE  = "icm.properties.example.template";
    public static final String ICM_PROPERTIES_EXAMPLE           = "icm.properties.example";
    public static final String CLEAN_BAT_TEMPLATE               = "clean.bat.template";
    public static final String CLEAN_BAT                        = "clean.bat";
    public static final String CLEAN_SH_TEMPLATE                = "clean.sh.template";
    public static final String CLEAN_SH                         = "clean.sh";
    public static final String TEMPLATE_PLACEHOLDER_ROOT_PROJECT_NAME      = "<rootProject.name in settings.gradle.kts>";
    public static final String TEMPLATE_PLACEHOLDER_DOCKER_REGISTRY        = "<ishprjxxacr>";
    public static final String TEMPLATE_PLACEHOLDER_ADO_ORGANIZATION       = "<adoOrganizationName>";
    public static final String TEMPLATE_PLACEHOLDER_CARTRIDGENAME          = "{cartridgeName}";
    public static final String TEMPLATE_PLACEHOLDER_CARTRIDGENAME_LAST     = "{cartridgeName.last}";
    public static final String TEMPLATE_PLACEHOLDER_CARTRIDGENAME_NOT_LAST = "{cartridgeName.!last}";

    protected Path resource;
    protected String resourceName;
    protected MigrationContext context;

    protected Path fileTemplatesDir;

    protected List<String> cartridgeNames = null;

    protected String rootProjectName     = null;
    protected String dockerRegistry      = null;
    protected String adoOrganizationName = null;

    protected Path environmentBatExample = null;
    protected Path environmentShExample  = null;
    protected Path icmPropertiesExample  = null;
    protected Path cleanBat              = null;
    protected Path cleanSh               = null;

    @Override
    public void prepareMigrateRoot(Path resource, MigrationContext context)
    {
        LOGGER.debug("START executing method  prepareMigrateRoot");

        this.resource = resource;
        this.resourceName = getResourceName(resource);
        this.context = context;

        this.fileTemplatesDir = getFileTemplatesDir();

        this.cartridgeNames = new ArrayList<>(100);

        try
        {
            List<Path> files = FileUtils.listTopLevelFiles(resource, null, null);
            for (Path path : files)
            {
                File dirOrFileInICMProjectRoot = path.toFile();
                
                String name = dirOrFileInICMProjectRoot.getName();

                // dirOrFileInICMProjectRoot is a cartridge if:
                //     1. It is a directory
                //     2. Name does not start with a dot
                //     3. Name does not consist of white space and dots only (this is just for safety, as it should not happen)
                //     4. Contains a build.gradle or build.gradle.kts file
                //     5. Is not "bin" (sometimes created by IStudio, containing a build.gradle.kts file)
                // A "src" folder is not required!
                if (dirOrFileInICMProjectRoot.isDirectory()
                    && !name.startsWith(".")
                    && !name.matches("(\\.|\\s)*")
                    && ((new File(dirOrFileInICMProjectRoot, "build.gradle")).exists() || (new File(dirOrFileInICMProjectRoot, "build.gradle.kts")).exists())
                    && !name.equals("bin"))
                {
                    this.cartridgeNames.add(name);
                    LOGGER.debug("Found cartridge: '{}'", name);
                }

                if (name.equals(SETTINGS_GRADLE_KTS))
                {
                    LOGGER.debug("Found {}", name);
                    this.rootProjectName = getValueFromPropertiesOrKTSFile(dirOrFileInICMProjectRoot, ROOT_PROJECT_NAME);
                    LOGGER.info("{} read from {}: '{}'", ROOT_PROJECT_NAME, name, this.rootProjectName);
                }

                if (name.equals(GRADLE_PROPERTIES))
                {
                    LOGGER.debug("Found {}", name);
                    this.dockerRegistry = getValueFromPropertiesOrKTSFile(dirOrFileInICMProjectRoot, DOCKER_REGISTRY);
                    LOGGER.info("{} read from {}: '{}'", DOCKER_REGISTRY, name, this.dockerRegistry);
                    this.adoOrganizationName = getValueFromPropertiesOrKTSFile(dirOrFileInICMProjectRoot, ADO_ORGANIZATION);
                    LOGGER.info("{} read from {}: '{}'", ADO_ORGANIZATION, name, this.adoOrganizationName);
                }

                if (name.equalsIgnoreCase(ENVIRONMENT_BAT_EXAMPLE))
                {
                    this.environmentBatExample = dirOrFileInICMProjectRoot.toPath();
                    LOGGER.warn("File '{}' already exists, content will be replaced.", name);
                }

                if (name.equalsIgnoreCase(ENVIRONMENT_SH_EXAMPLE))
                {
                    this.environmentShExample = dirOrFileInICMProjectRoot.toPath();
                    LOGGER.warn("File '{}' already exists, content will be replaced.", name);
                }

                if (name.equalsIgnoreCase(ICM_PROPERTIES_EXAMPLE))
                {
                    this.icmPropertiesExample = dirOrFileInICMProjectRoot.toPath();
                    LOGGER.warn("File '{}' already exists, content will be replaced.", name);
                }

                if (name.equalsIgnoreCase(CLEAN_BAT))
                {
                    this.cleanBat = dirOrFileInICMProjectRoot.toPath();
                    LOGGER.warn("File '{}' already exists, content will be replaced.", name);
                }

                if (name.equalsIgnoreCase(CLEAN_SH))
                {
                    this.cleanSh = dirOrFileInICMProjectRoot.toPath();
                    LOGGER.warn("File '{}' already exists, content will be replaced.", name);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Exception reading project root " + resource.toString(), e);
            context.recordFailure(resourceName, MigrationContext.OperationType.MOVE, resource, null, "Exception reading project root " + resource.toString() + ": " + e.toString());
        }

        LOGGER.debug("END   executing method  prepareMigrateRoot");
    }

    @Override
    public void migrateRoot(Path resource, MigrationContext context)
    {
        LOGGER.debug("START executing method  migrateRoot");

        if (this.cartridgeNames == null)
        {
            //LOGGER.error("Method prepareMigrateRoot not successfully executed before executing migrateRoot!");
            prepareMigrateRoot(resource, context);
        }

        // Generate environment.bat.example
        generateExampleFile(ENVIRONMENT_BAT_EXAMPLE_TEMPLATE, this.environmentBatExample, ENVIRONMENT_BAT_EXAMPLE);

        // Generate environment.sh.example
        generateExampleFile(ENVIRONMENT_SH_EXAMPLE_TEMPLATE, this.environmentShExample, ENVIRONMENT_SH_EXAMPLE);

        // Generate icm.properties.example
        generateExampleFile(ICM_PROPERTIES_EXAMPLE_TEMPLATE, this.icmPropertiesExample, ICM_PROPERTIES_EXAMPLE);

        // Generate clean.bat
        generateCleanFile(CLEAN_BAT_TEMPLATE, this.cleanBat, CLEAN_BAT);

        // Generate clean.sh
        generateCleanFile(CLEAN_SH_TEMPLATE, this.cleanSh, CLEAN_SH);

        LOGGER.debug("END   executing method  migrateRoot");
    }

    /**
     * Directory containing the environment.bat.example.template, environment.sh.example.template, icm.properties.example.template, clean.bat.template, clean.sh.template files.
     * @return directory containing the template file
     */
    protected static Path getFileTemplatesDir()
    {
        final Path fileTemplatesDir = Paths.get("src/main/resources/environment");
        if (!Files.exists(fileTemplatesDir) || !Files.isDirectory(fileTemplatesDir))
        {
            LOGGER.error("Directory src/main/resources/environment, containing the template file, not found. Absolute path: '{}'.", fileTemplatesDir.toAbsolutePath().toString());
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
    protected static String getValueFromPropertiesOrKTSFile(File propertiesOrKTSFile, String key)
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
     * Creates or replaces the *.example file in project root, based on a file template *.example.template.
     * Records the result of the operation in the migration context.
     *
     * @param exampleFileTemplateName The file name of the tmeplate file.
     * @param exampleFile             The path to the output file where the processed content will be written into.
     *                                If the file already exists, its content will be replaced.
     * @param exampleFileName         The file name of the output file.
     */
    protected void generateExampleFile(String exampleFileTemplateName, Path exampleFile, String exampleFileName)
    {
        File exampleTemplate = new File(this.fileTemplatesDir.toFile(), exampleFileTemplateName);
        if (exampleTemplate.exists() && exampleTemplate.isFile())
        {
            if (exampleFile == null)
            {
                // In this case the file does not exist yet.
                exampleFile = this.resource.resolve(exampleFileName);
            }

            createOrReplaceExampleFile(exampleTemplate, exampleFile, this.rootProjectName, this.dockerRegistry, this.adoOrganizationName);
            this.context.recordSuccess(this.resourceName, MigrationContext.OperationType.MOVE, exampleTemplate.toPath(), exampleFile);
        }
        else
        {
            LOGGER.error("File '{}' not found in '{}'.", exampleFileTemplateName, this.fileTemplatesDir);
            this.context.recordFailure(this.resourceName, MigrationContext.OperationType.MOVE, this.fileTemplatesDir, null, "Source/template file not found: " + exampleFileTemplateName);
        }
    }

    /**
     * Creates or replaces the clean.bat/.sh file in project root, based on a file template clean.bat/.sh.template.
     * Records the result of the operation in the migration context.
     *
     * @param cleanFileTemplateName The file name of the tmeplate file.
     * @param cleanFile             The path to the output file where the processed content will be written into.
     *                              If the file already exists, its content will be replaced.
     * @param cleanFileName         The file name of the output file.
     */
    protected void generateCleanFile(String cleanFileTemplateName, Path cleanFile, String cleanFileName)
    {
        File cleanTemplate = new File(this.fileTemplatesDir.toFile(), cleanFileTemplateName);
        if (cleanTemplate.exists() && cleanTemplate.isFile())
        {
            if (cleanFile == null)
            {
                // In this case the file does not exist yet.
                cleanFile = this.resource.resolve(cleanFileName);
            }

            createOrReplaceCleanFile(cleanTemplate, cleanFile, this.cartridgeNames);
            this.context.recordSuccess(this.resourceName, MigrationContext.OperationType.MOVE, cleanTemplate.toPath(), cleanFile);
        }
        else
        {
            LOGGER.error("File '{}' not found in '{}'.", cleanFileTemplateName, this.fileTemplatesDir);
            this.context.recordFailure(this.resourceName, MigrationContext.OperationType.MOVE, this.fileTemplatesDir, null, "Source/template file not found: " + cleanFileTemplateName);
        }
    }

    /**
     * Creates or replaces the *.example file in project root, based on a file template *.example.template.
     *
     * @param exampleFileTemplate    The template file containing placeholders.
     * @param exampleFile            The path to the output file where the processed content will be written into.
     *                               If the file already exists, its content will be replaced.
     * @param rootProjectName        rootProject.name from settings.gradle.kts, used to replace placeholders &lt;rootProject.name in settings.gradle.kts&gt;.
     *                               If <code>null</code>, the placeholder will remain in the output file.
     * @param dockerRegistry         dockerRegistry configured in gradle.properties, used to replace placeholders &lt;ishprjxxacr&gt;.
     *                               If <code>null</code>, the placeholder will remain in the output file.
     * @param adoOrganizationName    adoOrganizationName configured in gradle.properties, used to replace placeholders &lt;adoOrganizationName&gt;.
     *                               If <code>null</code>, the placeholder will remain in the output file.
     */
    protected static void createOrReplaceExampleFile(File exampleFileTemplate, Path exampleFile, String rootProjectName, String dockerRegistry, String adoOrganizationName)
    {
        LOGGER.debug("START executing method  createOrReplaceExampleFile  ({})", exampleFile.getFileName());

        try {
            if (rootProjectName == null)
            {
                LOGGER.warn("No {} found. {} might still contain placeholders {}.", ROOT_PROJECT_NAME, exampleFile.getFileName(), TEMPLATE_PLACEHOLDER_ROOT_PROJECT_NAME);
            }

            if (dockerRegistry == null)
            {
                LOGGER.warn("No {} found. {} might still contain placeholders {}.", DOCKER_REGISTRY, exampleFile.getFileName(), TEMPLATE_PLACEHOLDER_DOCKER_REGISTRY);
            }

            if (adoOrganizationName == null)
            {
                LOGGER.warn("No {} found. {} might still contain placeholders {}.", ADO_ORGANIZATION, exampleFile.getFileName(), TEMPLATE_PLACEHOLDER_ADO_ORGANIZATION);
            }

            StringBuilder resultingFileContent = new StringBuilder(20000);

            List<String> lines = FileUtils.readAllLines(exampleFileTemplate.toPath());
            for (String line : lines)
            {
                if (rootProjectName != null)
                {
                    line = line.replace(TEMPLATE_PLACEHOLDER_ROOT_PROJECT_NAME, rootProjectName);  // replaces all occurrences in line
                }

                if (dockerRegistry != null)
                {
                    line = line.replace(TEMPLATE_PLACEHOLDER_DOCKER_REGISTRY, dockerRegistry);  // replaces all occurrences in line
                }

                if (adoOrganizationName != null)
                {
                    line = line.replace(TEMPLATE_PLACEHOLDER_ADO_ORGANIZATION, adoOrganizationName);  // replaces all occurrences in line
                }

                resultingFileContent.append(line).append(LINE_SEP);
            }
    
            String resultingFileContentString = resultingFileContent.toString();
            FileUtils.writeString(exampleFile, resultingFileContentString);
            LOGGER.info("Content of file '{}' successfully written.", exampleFile.toAbsolutePath().toString());
        } catch (IOException e) {
            LOGGER.error("Exception reading or writing " + exampleFile.getFileName() + " / " + exampleFileTemplate.getName(), e);
        }

        LOGGER.debug("END   executing method  createOrReplaceExampleFile  ({})", exampleFile.getFileName());
    }

    /**
     * Creates or replaces the clean.bat file in project root,
     * based on a template file.
     *
     * @param cleanFileTemplate The template file containing placeholders.
     * @param cleanFile         The path to the output file where the processed content will be written.
     *                          the file already exists, its content will be replaced.
     * @param cartridgeNames    List of all cartridge directory names that should be cleaned by clean.bat.
     */
    protected static void createOrReplaceCleanFile(File cleanFileTemplate, Path cleanFile, List<String> cartridgeNames)
    {
        LOGGER.debug("START executing method  createOrReplaceCleanFile  ({})", cleanFile.getFileName());

        try {
            if (cartridgeNames.isEmpty())
            {
                LOGGER.warn("No cartridges found in the project. {} will contain placeholders like {}.", cleanFile.getFileName(), TEMPLATE_PLACEHOLDER_CARTRIDGENAME);
            }

            StringBuilder resultingFileContent = new StringBuilder(20000);

            List<String> lines = FileUtils.readAllLines(cleanFileTemplate.toPath());
            for (String line : lines)
            {
                if (!cartridgeNames.isEmpty())
                {
                    String resultingLines = replacePlaceholderWithOneLinePerCartridge(line, cartridgeNames);
                    resultingFileContent.append(resultingLines);
                }
                else
                {
                    resultingFileContent.append(line).append(LINE_SEP);
                }
            }
    
            String resultingFileContentString = resultingFileContent.toString();
            FileUtils.writeString(cleanFile, resultingFileContentString);
            LOGGER.info("Content of file '{}' successfully written.", cleanFile.toAbsolutePath().toString());
        } catch (IOException e) {
            LOGGER.error("Exception reading or writing " + cleanFile.getFileName() + " / " + cleanFileTemplate.getName(), e);
        }

        LOGGER.debug("END   executing method  createOrReplaceCleanFile  ({})", cleanFile.getFileName());
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
    protected static String replacePlaceholderWithOneLinePerCartridge(String line, List<String> cartridgeNames)
    {
        StringBuilder result = new StringBuilder(20000);

        if (line.contains(TEMPLATE_PLACEHOLDER_CARTRIDGENAME))
        {
            // line contains one or more occurrences of "{cartridgeName}",
            // create one line per cartridge
            for (String cartridgeName : cartridgeNames)
            {
                String replacedLine = line.replace(TEMPLATE_PLACEHOLDER_CARTRIDGENAME, cartridgeName);
                result.append(replacedLine).append(LINE_SEP);
            }
        }
        else if (line.contains(TEMPLATE_PLACEHOLDER_CARTRIDGENAME_LAST))
        {
            // line contains one or more occurrences of "{cartridgeName.last}",
            // create one line for the last cartridge
            String cartridgeName = cartridgeNames.getLast();
            String replacedLine = line.replace(TEMPLATE_PLACEHOLDER_CARTRIDGENAME_LAST, cartridgeName);
            result.append(replacedLine).append(LINE_SEP);
        }
        else if (line.contains(TEMPLATE_PLACEHOLDER_CARTRIDGENAME_NOT_LAST))
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
                    String replacedLine = line.replace(TEMPLATE_PLACEHOLDER_CARTRIDGENAME_NOT_LAST, cartridgeName);
                    result.append(replacedLine).append(LINE_SEP);
                }
            }
        }
        else
        {
            result.append(line).append(LINE_SEP);
        }

        String resultString = result.toString();
        return resultString;
    }
}
