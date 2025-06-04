package com.intershop.customization.migration.file;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Map;

import com.intershop.customization.migration.common.MigrationContext;
import com.intershop.customization.migration.common.MigrationPreparer;
import com.intershop.customization.migration.common.MigrationStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Copies files from the classpath resources to specified target locations in the file system.
 * <p>
 * The source, target, and (optional) log configurations are provided via a {@link MigrationStep} and are
 * typically defined in a YAML configuration. This class is used as part of a migration process
 * to prepare or update project files by copying predefined resources.
 * </p>
 *
 * <p>
 * Example YAML configuration:
 * <pre>
 * type: specs.intershop.com/v1beta/migrate
 * migrator: com.intershop.customization.migration.file.ClasspathResourceFileCopier
 * message: "refactor: copy files to root project"
 * options:
 *   source-map:
 *     "rewrite" : "gradle/rewrite.gradle"
 *   target-map:
 *     "rewrite" : "rewrite.gradle"
 *   log-map:
 *     "rewrite" : "Created rewrite.gradle in root project."
 * </pre>
 * </p>
 *
 * Implements {@link MigrationPreparer} for integration with the migration framework.
 */
public class ClasspathResourceFileCopier implements MigrationPreparer
{

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static final String YAML_KEY_SOURCE_MAP     = "source-map";
    private static final String YAML_KEY_TARGET_MAP     = "target-map";
    private static final String YAML_KEY_LOGGING_MAP    = "log-map";

    private Map<String, String> sourceConfiguration     = Collections.emptyMap();
    private Map<String, String> targetConfiguration     = Collections.emptyMap();
    private Map<String, String> logConfiguration        = Collections.emptyMap();

    @Override
    public void setStep(MigrationStep step)
    {
        this.sourceConfiguration    = step.getOption(YAML_KEY_SOURCE_MAP);
        this.targetConfiguration    = step.getOption(YAML_KEY_TARGET_MAP);
        this.logConfiguration       = step.getOption(YAML_KEY_LOGGING_MAP);
    }

    @Override
    public void migrateRoot(Path resource, MigrationContext context)
    {
        for (Map.Entry<String, String> sourceEntry : sourceConfiguration.entrySet())
        {
            String artifactName = sourceEntry.getKey();

            try (InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(sourceEntry.getValue()))
            {
                if (inputStream == null)
                {
                    logger.error("Resource '{}' not found in classpath.", sourceEntry.getValue());
                    context.recordFailure(artifactName, MigrationContext.OperationType.CREATE, resource, resource,
                            "Resource '" + sourceEntry.getValue() + "' not found in classpath.");
                    continue;
                }

                Path targetPath = resource.resolve(targetConfiguration.get(artifactName));
                if (Files.exists(targetPath))
                {
                    logger.warn("Target file '{}' already exists and will be overwritten by resource '{}'.",
                                    targetPath, sourceEntry.getValue());
                }

                Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
                context.recordSuccess(artifactName, MigrationContext.OperationType.CREATE, resource, targetPath);

                String logMessage = logConfiguration.get(artifactName);
                if (logMessage != null && !logMessage.isEmpty())
                {
                    logger.info(logMessage);
                }
            }
            catch (IOException e)
            {
                logger.error("Error copying file: {}", e.getMessage());
                context.recordFailure(artifactName, MigrationContext.OperationType.CREATE, resource, resource,
                        "Error copying file: " + e.getMessage());
            }
        }
    }
}
