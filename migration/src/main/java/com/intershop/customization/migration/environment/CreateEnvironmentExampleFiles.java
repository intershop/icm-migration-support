package com.intershop.customization.migration.environment;

import static com.intershop.customization.migration.common.MigrationContext.OperationType.CREATE;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.intershop.customization.migration.common.MigrationContext;
import com.intershop.customization.migration.common.MigrationPreparer;
import com.intershop.customization.migration.common.MigrationStep;
import com.intershop.customization.migration.common.Position;
import com.intershop.customization.migration.utils.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is used to parse version files (except: 'intershopBuild.version', '.ivy*.version', '.pom*.version')
 * and integrate the version information containing information about the artifact group, name and version
 * into the `versions/build.gradle.kts` file.
 * <p>
 * Migration to Kotlin is out of scope for this step and performed later in a separate step.
 * <p>
 * Example YAML configuration:
 * <pre>
 * type: specs.intershop.com/v1beta/migrate
 * migrator: com.intershop.customization.migration.gradle.MigrateVersionFiles
 * message: "refactor: transfer data from '*.version' files into 'versions/build.gradle.kts'"
 * </pre>
 */
public class CreateEnvironmentExampleFiles implements MigrationPreparer
{
    private final Logger LOGGER = LoggerFactory.getLogger(getClass());

    protected Path fileTemplatesDir;

    public static final String SETTINGS_GRADLE_KTS = "settings.gradle.kts";
    public static final String ROOT_PROJECT_NAME   = "rootProject.name";
    protected String rootProjectName = null;

    public static final String LINE_SEP = System.lineSeparator();

    public static final String ENVIRONMENT_BAT_EXAMPLE_TEMPLATE = "environment.bat.example.template";
    public static final String ENVIRONMENT_BAT_EXAMPLE          = "environment.bat.example";
    public static final String ICM_PROPERTIES_EXAMPLE_TEMPLATE  = "icm.properties.example.template";
    public static final String ICM_PROPERTIES_EXAMPLE           = "icm.properties.example";
    public static final String TEMPLATE_PLACEHOLDER_ROOT_PROJECT_NAME = "<rootProject.name in settings.gradle.kts>";

    public static final String CLEAN_BAT_TEMPLATE               = "clean.bat.template";
    public static final String CLEAN_BAT                        = "clean.bat";
    public static final String CLEAN_BAT_TEMPLATE_PLACEHOLDER_CARTRIDGENAME          = "{cartridgeName}";
    public static final String CLEAN_BAT_TEMPLATE_PLACEHOLDER_CARTRIDGENAME_LAST     = "{cartridgeName.last}";
    public static final String CLEAN_BAT_TEMPLATE_PLACEHOLDER_CARTRIDGENAME_NOT_LAST = "{cartridgeName.!last}";

    @Override
    public void setStep(MigrationStep step)
    {
        LOGGER.info("CreateEnvironmentExampleFiles.setStep START.");

        Path fileTemplatesDir = getFileTemplatesDir();
        this.fileTemplatesDir = fileTemplatesDir;

        LOGGER.info("CreateEnvironmentExampleFiles.setStep END.");
    }

    @Override
    public void migrateRoot(Path projectDir, MigrationContext context)
    {
        LOGGER.info("CreateEnvironmentExampleFiles.migrateRoot START.");

        List<String> cartridgeNames = new LinkedList<>();
        boolean existsEnvironmentBatExample = false;
        boolean existsIcmPropertiesExample  = false;
        boolean existsCleanBat              = false;

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
                    LOGGER.debug("Found settings.gradle.kts");
                    rootProjectName = getRootProjectName(dirOrFileInICMProjectRoot);
                }

                if (name.equalsIgnoreCase(ENVIRONMENT_BAT_EXAMPLE))
                {
                    existsEnvironmentBatExample = true;
                    LOGGER.debug("File '{}' already exists, content will be replaced.", name);
                }

                if (name.equalsIgnoreCase(ICM_PROPERTIES_EXAMPLE))
                {
                    existsIcmPropertiesExample = true;
                    LOGGER.debug("File '{}' already exists, content will be replaced.", name);
                }

                if (name.equalsIgnoreCase(CLEAN_BAT))
                {
                    existsCleanBat = true;
                    LOGGER.debug("File '{}' already exists, content will be replaced.", name);
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
            createOrReplaceEnvironmentBatExample(environmentBatExampleTemplate, environmentBatExample, existsEnvironmentBatExample, rootProjectName);
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
            createOrReplaceIcmPropertiesExample(icmPropertiesExampleTemplate, icmPropertiesExample, existsIcmPropertiesExample, rootProjectName);
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
            createOrReplaceCleanBAT(cleanBatTemplate, cleanBat, existsCleanBat, cartridgeNames);
        }
        else
        {
            LOGGER.error("File '{}' not found in '{}'.", CLEAN_BAT_TEMPLATE, fileTemplatesDir);
        }

        LOGGER.info("CreateEnvironmentExampleFiles.migrateRoot END.");
    }

    /**
     * Location of environment.bat.example.template, icm.properties.example.template and clean.bat.template files.
     * @return directory containing the example file templates
     */
    protected Path getFileTemplatesDir()
    {
        //Path fileTemplatesDir = new Path(projectDir, "versions");
        final Path fileTemplatesDir = Paths.get("src/main/resources/environment");
        if (!Files.exists(fileTemplatesDir) || !Files.isDirectory(fileTemplatesDir))
        {
            LOGGER.error("Directory \"src/main/resources/environment\" containing the example file templates not found at '{}'.", fileTemplatesDir);
        }

        return fileTemplatesDir;
    }

    protected String getRootProjectName(File settingsGradleKts)
    {
        String rootProjectName = null;

        try {
            List<String> linesStream = FileUtils.readAllLines(settingsGradleKts.toPath());
            for (String line : linesStream)
            {
                // line is in the format:  rootProject.name = "projectName"...
                String[] parts = line.split("=");
                if (parts.length >= 2
                    && parts[0].trim().equals(ROOT_PROJECT_NAME))
                {
                    String partAfterEquals = parts[1].trim();
                    if (partAfterEquals.length() > 1
                        && partAfterEquals.startsWith("\""))
                    {
                        int i = partAfterEquals.indexOf("\"", 1);
                        if (i == -1)
                        {
                            LOGGER.error("Unable to retrieve rootProject.name from line: '{}', syntax too complex, could not find ending \" of value.", line);
                            rootProjectName = null;
                        }
                        else
                        {
                            if (i == 1)
                            {
                                LOGGER.error("Unable to retrieve rootProject.name from line: '{}', value is an empty string.", line);
                            }

                            rootProjectName = partAfterEquals.substring(1, i);
                            LOGGER.info("Found root project name: '{}'", rootProjectName);
                        }
                    }
                    else
                    {
                        LOGGER.error("Unable to retrieve rootProject.name from line: '{}', syntax too complex, found 'rootProject.name' and '=', but no value surrounded by \".", line);
                        rootProjectName = null;
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("Exception reading settings.gradle.kts", e);
        }

        return rootProjectName;
    }

    protected void createOrReplaceEnvironmentBatExample(File environmentBatExampleTemplate, Path environmentBatExample, boolean existsEnvironmentBatExample, String rootProjectName)
    {
        LOGGER.info("CreateEnvironmentExampleFiles.createOrReplaceEnvironmentBatExample START.");

        try {
            List<String> linesStream = FileUtils.readAllLines(environmentBatExampleTemplate.toPath());

            FileUtils.writeString(environmentBatExample, migrateLinesRootProjectName(linesStream, rootProjectName));
        } catch (IOException e) {
            LOGGER.error("Exception reading or writing environment.bat.example / environment.bat.example.template", e);
        }

        LOGGER.info("CreateEnvironmentExampleFiles.createOrReplaceEnvironmentBatExample END.");
    }

    protected void createOrReplaceIcmPropertiesExample(File icmPropertiesExampleTemplate, Path icmPropertiesExample, boolean existsIcmPropertiesExample, String rootProjectName)
    {
        LOGGER.info("CreateEnvironmentExampleFiles.createOrReplaceIcmPropertiesExample START.");

        try {
            List<String> linesStream = FileUtils.readAllLines(icmPropertiesExampleTemplate.toPath());

            FileUtils.writeString(icmPropertiesExample, migrateLinesRootProjectName(linesStream, rootProjectName));
        } catch (IOException e) {
            LOGGER.error("Exception reading or writing icm.properties.example / icm.properties.example.template", e);
        }

        LOGGER.info("CreateEnvironmentExampleFiles.createOrReplaceIcmPropertiesExample END.");
    }

    protected void createOrReplaceCleanBAT(File cleanBatTemplate, Path cleanBat, boolean existsCleanBat, List<String> cartridgeNames)
    {
        LOGGER.info("CreateEnvironmentExampleFiles.createOrReplaceCleanBAT START.");

        try {
            List<String> linesStream = FileUtils.readAllLines(cleanBatTemplate.toPath());

            FileUtils.writeString(cleanBat, migrateLinesCleanBAT(linesStream, cartridgeNames));
        } catch (IOException e) {
            LOGGER.error("Exception reading or writing clean.bat / clean.bat.template", e);
        }

        LOGGER.info("CreateEnvironmentExampleFiles.createOrReplaceCleanBAT END.");
    }

    protected String migrateLinesRootProjectName(List<String> lines, String rootProjectName)
    {
        String result;

        if (rootProjectName == null)
        {
            LOGGER.warn("No rootProject.name found. Copying template without replacing lines containing <rootProject.name in settings.gradle.kts>.");
            result = String.join(LINE_SEP, lines) + LINE_SEP;
        }
        else
        {
            result = "";

            for (String line : lines)
            {
                if (line.contains(TEMPLATE_PLACEHOLDER_ROOT_PROJECT_NAME))
                {
                    // line contains one or more occurrences of "<rootProject.name in settings.gradle.kts>",
                    String replacedLine = line.replace(TEMPLATE_PLACEHOLDER_ROOT_PROJECT_NAME, rootProjectName);
                    result += replacedLine + LINE_SEP;
                }
                else
                {
                    result += line + LINE_SEP;
                }
            }
        }

        return result;
    }

    protected String migrateLinesCleanBAT(List<String> lines, List<String> cartridgeNames)
    {
        String result;

        if (cartridgeNames.isEmpty())
        {
            LOGGER.warn("No cartridges found in the project. Copying template without replacing lines containing {cartridgeName}.");
            result = String.join(LINE_SEP, lines) + LINE_SEP;
        }
        else
        {
            result = "";

            for (String line : lines)
            {
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
            }
        }

        return result;
    }
}
