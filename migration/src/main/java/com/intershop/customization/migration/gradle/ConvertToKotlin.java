package com.intershop.customization.migration.gradle;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.BiFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.intershop.customization.migration.common.MigrationPreparer;

public class ConvertToKotlin implements MigrationPreparer
{
    public static final String GRADLEKOTLINCONVERTER = "kotlin/gradlekotlinconverter.kts";
    public static final String KOTLIN = "kotlin";
    private final Logger LOGGER = LoggerFactory.getLogger(getClass());

    public void migrate(Path resource)
    {
        LOGGER.debug("Starting Kotlin migration for project '{}'", getResourceName(resource));

        String kotlinVersion = getKotlinRuntimeVersion();
        if (kotlinVersion == null)
        {
            LOGGER.error("Kotlin runtime environment is not available. Aborting migration auf 'build.gradle' files to 'build.gradle.kts'.");
            return;
        }

        LOGGER.info("Kotlin runtime version found: {}", kotlinVersion);

        try
        {
            String scriptOutput = executeKotlinScript(resource);
            LOGGER.debug("Script output collected: {}", scriptOutput);
        }
        catch (IOException | InterruptedException e)
        {
            LOGGER.error("Error while executing Kotlin script: {}", e.getMessage(), e);
        }
    }

    private String getKotlinRuntimeVersion()
    {
        try
        {
            return executeKotlinProcess(
                            new String[] { KOTLIN, "-version" },
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

    private String executeKotlinScript(Path resource) throws IOException, InterruptedException
    {
        Path scriptPath = getKotlinScriptPath();
        return executeKotlinProcess(
                        new String[] { KOTLIN, scriptPath.toString(), resource.toString(), "skipintro", "deleteInputFile" },
                        (exitCode, output) -> {
                            if (exitCode != 0)
                            {
                                LOGGER.error("Kotlin script exited with non-zero code: {}", exitCode);
                            }
                            return output;
                        }
        );
    }

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

    private Path getKotlinScriptPath() throws IOException
    {
        try (InputStream scriptStream = getClass().getClassLoader().getResourceAsStream(GRADLEKOTLINCONVERTER))
        {
            if (scriptStream == null)
            {
                throw new IOException("Kotlin script '" + GRADLEKOTLINCONVERTER + "' not found in classpath.");
            }

            Path tempScript = Files.createTempFile("gradlekotlinconverter", ".kts");
            Files.copy(scriptStream, tempScript, StandardCopyOption.REPLACE_EXISTING);
            tempScript.toFile().deleteOnExit(); // Löscht die Datei nach Programmende
            return tempScript;
        }
    }
}