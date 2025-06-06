package com.intershop.customization.migration.gradle;

import static com.intershop.customization.migration.common.MigrationContext.OperationType.MODIFY;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.BiFunction;

import com.intershop.customization.migration.common.MigrationContext;
import com.intershop.customization.migration.utils.OsCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.intershop.customization.migration.common.MigrationPreparer;

/**
 * This migration step calls a customized version of the "GradleKotlinConverter"
 * to convert build.gradle files to build.gradle.kts files.
 * <p>
 * Migrated source files are deleted.
 * <p>
 * Example YAML configuration:
 * <pre>
 * type: specs.intershop.com/v1beta/migrate
 * migrator: com.intershop.customization.migration.gradle.ConvertToKotlin
 * message: "refactor: convert 'build.gradle' files to 'build.gradle.kts'"
 * </pre>
 */
public class ConvertToKotlin implements MigrationPreparer
{
    public static final String GRADLE_KOTLIN_CONVERTER = "kotlin/gradlekotlinconverter.kts";
    private final Logger LOGGER = LoggerFactory.getLogger(getClass());

    @Override
    public void prepareMigrate(Path resource, MigrationContext context)
    {
        String kotlinVersion = getKotlinRuntimeVersion();
        if (kotlinVersion == null)
        {
            context.recordCriticalError("Kotlin runtime environment is not available.");
        }
    }

    @Override
    public void migrate(Path resource, MigrationContext context)
    {
        String resourceName = getResourceName(resource);
        LOGGER.info("Migrating project build files of '{}' to Kotlin", resourceName);

        String kotlinVersion = getKotlinRuntimeVersion();
        LOGGER.debug("Kotlin runtime version found: {}", kotlinVersion);

        try
        {
            String scriptOutput = executeKotlinScript(resource);
            context.recordSuccess(resourceName, MODIFY, resource, resource);
            LOGGER.debug("Script output collected: {}", scriptOutput);
        }
        catch (IOException | InterruptedException e)
        {
            LOGGER.error("Error while executing Kotlin script: {}", e.getMessage(), e);
            context.recordFailure(resourceName, MODIFY, resource, resource,
                    "Error while executing Kotlin script: " + e.getMessage());
        }
    }

    /**
     * Returns the name of the executable depending on the operating system.
     *
     * @return "kotlin" for Unix-based OS or "kotlin.bat" for Windows.
     */
    private String getKotlinExecutable()
    {
        return OsCheck.isWindows() ? "kotlin.bat" : "kotlin";
    }

    /**
     * Returns the version of the found Kotlin runtime.
     *
     * @return the version of the Kotlin runtime, or <code>null</code> if it could not be determined.
     */
    private String getKotlinRuntimeVersion()
    {
        try
        {
            return executeKotlinProcess(
                            new String[] { getKotlinExecutable(), "-version" },
                            (exitCode, output) -> {
                                if (exitCode == 0)
                                {
                                    return output;
                                }
                                else
                                {
                                    LOGGER.error("Kotlin runtime check exited with non-zero code: {}", exitCode);
                                    return null;
                                }
                            }
            );
        }
        catch (IOException | InterruptedException e)
        {
            LOGGER.error("Error while checking Kotlin runtime: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Executes the Kotlin script to convert the given Gradle build file of the given path.
     *
     * @param resource the path of the parent directory to the Gradle build file to convert
     * @return the collected output
     * @throws IOException unhandled possible IO exception
     * @throws InterruptedException unhandled possible interruption exception
     */
    private String executeKotlinScript(Path resource) throws IOException, InterruptedException
    {
        Path scriptPath = getKotlinScriptPath();
        return executeKotlinProcess(
                        new String[] { getKotlinExecutable(), scriptPath.toString(), resource.toString(), "skipintro", "deleteInputFile" },
                        (exitCode, output) -> {
                            if (exitCode != 0)
                            {
                                LOGGER.error("Kotlin script exited with non-zero code: {}", exitCode);
                            }
                            return output;
                        }
        );
    }

    /**
     * Convenience method to execute a Kotlin process with the given command and process the output.
     *
     * @param command array of command line arguments to execute the Kotlin process
     * @param outputProcessor a bi-function that processes the output of the Kotlin process.
     * @return the result of the handed in bi-function
     * @param <T> expected return type of the output processor
     * @throws IOException unhandled possible IO exception
     * @throws InterruptedException unhandled possible interruption exception
     */
    private <T> T executeKotlinProcess(String[] command, BiFunction<Integer, String, T> outputProcessor) throws IOException, InterruptedException
    {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();
        StringBuilder output = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream())))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                output.append(line).append(System.lineSeparator());
            }
        }

        int exitCode = process.waitFor();
        return outputProcessor.apply(exitCode, output.toString().trim());
    }

    /**
     * Helper function to resolve the script in the classpath and convert to a usable the path
     * to hand over to the Kotlin executable.
     *
     * @return temporary path a copy of the Kotlin script
     * @throws IOException unhandled possible IO exception
     */
    private Path getKotlinScriptPath() throws IOException
    {
        try (InputStream scriptStream = getClass().getClassLoader().getResourceAsStream(GRADLE_KOTLIN_CONVERTER))
        {
            if (scriptStream == null)
            {
                throw new IOException("Kotlin script '" + GRADLE_KOTLIN_CONVERTER + "' not found in classpath.");
            }

            Path tempScript = Files.createTempFile("gradle_kotlin_converter", ".kts");
            Files.copy(scriptStream, tempScript, StandardCopyOption.REPLACE_EXISTING);
            tempScript.toFile().deleteOnExit(); // Remove the temp file on exit
            return tempScript;
        }
    }
}
