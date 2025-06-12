package com.intershop.customization.migration;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.intershop.customization.migration.common.MigrationContext;
import com.intershop.customization.migration.common.MigrationPreparer;
import com.intershop.customization.migration.common.MigrationStep;
import com.intershop.customization.migration.common.MigrationStepFolder;
import com.intershop.customization.migration.git.GitInitializationException;
import com.intershop.customization.migration.git.GitRepository;
import com.intershop.customization.migration.git.GitValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class Migrator
{
    public static final Logger LOGGER = LoggerFactory.getLogger(Migrator.class);
    private static final int POS_TASK = 0;
    private static final int POS_PATH = 1;
    private static final int POS_STEPS = 2;

    private static final String OPTION_NO_AUTO_COMMIT = "--noAutoCommit";
    private static final int MAX_SEARCH_DEPTH_FOR_GIT_REPO = 1;

    private final File migrationStepFolder;
    private Optional<GitRepository> gitRepository = Optional.empty();
    private final MigrationContext context = new MigrationContext();

    /**
     * Initializes the migrator
     * @param migrationStepFolder folder containing the migration step descriptions
     */
    public Migrator(File migrationStepFolder)
    {
        this.migrationStepFolder = migrationStepFolder;
    }

    /**
     * @param args the array of command line arguments
     * <li>"project" as task</li>
     * <li>directory to project app_sf_responsive</li>
     * <li>directory to migration steps like src/main/resources/001_migration_7x10_to_11</li>
     * <li>optional flags like "noAutoCommit"</li>
     */
    public static void main(String[] args)
    {
        Optional<GitRepository> gitRepository = Optional.empty();
        try
        {
            if (args.length >= POS_STEPS + 1)
            {
                File projectPath = new File(args[POS_PATH]);
                if (!projectPath.exists() || !projectPath.isDirectory())
                {
                    LOGGER.error("Project path '{}' is not a directory.", projectPath);
                    System.exit(2);
                }

                Migrator migrator = new Migrator(new File(args[POS_STEPS]));
                migrator.initializeGitRepository(Arrays.stream(args)
                                                       .noneMatch(o -> o.equalsIgnoreCase(OPTION_NO_AUTO_COMMIT)), projectPath);

                migrator.validateGitRepository();

                if ("project".equals(args[POS_TASK]))
                {
                    LOGGER.info("Convert project at {}.", projectPath);
                    migrator.migrateProject(projectPath);
                }
                else if ("projects".equals(args[POS_TASK]))
                {
                    LOGGER.info("Convert projects at {}.", projectPath);
                    migrator.migrateProjects(projectPath);
                }
            }
            else
            {
                LOGGER.error("Missing parameter '{}'.", args.length);
                System.exit(1);
            }
        }
        catch (GitValidationException gve)
        {
            LOGGER.error("Validation of git repository failed: {}", gve.getMessage());
        }
        catch (Exception e)
        {
            LOGGER.error("Unexpected error during migration", e);
            System.exit(1);
        }
        finally
        {
            gitRepository.ifPresent(GitRepository::close);
        }
    }

    /**
     * Initializes the git repository for the project.
     *
     * @param autoCommit if true, the git repository will be initialized and changes will be committed automatically
     * @param projectPath the path to the project directory
     */
    public void initializeGitRepository(boolean autoCommit, File projectPath)
    {
        if (autoCommit)
        {
            try
            {
                LOGGER.debug("Initializing Git repository for '{}' ...", projectPath);
                this.gitRepository = Optional.of(new GitRepository(projectPath, MAX_SEARCH_DEPTH_FOR_GIT_REPO));
            }
            catch(GitInitializationException e)
            {
                LOGGER.error("Unexpected error while initializing Git repository. Auto commit will be disabled!", e);
                this.gitRepository = Optional.empty();
            }
        }
        else
        {
            LOGGER.info("Auto commit is disabled. Please check the changes in {}.", projectPath);
            this.gitRepository = Optional.empty();
        }
    }

    /**
     * Migrate on root project
     */
    protected void migrateProjects(File rootProject)
    {
        MigrationStepFolder steps = MigrationStepFolder.valueOf(migrationStepFolder.toPath());
        List<MigrationStep> allSteps = steps.getSteps();

        if (!prepareMigrate(rootProject, true, allSteps))
        {
            return;
        }

        for (MigrationStep step: allSteps)
        {
            MigrationPreparer migrator = step.getMigrator();

            migrator.migrateRoot(rootProject.toPath(), context);

            File[] files = rootProject.listFiles();
            if (files == null)
            {
                return;
            }
            for (File cartridgeDir : files)
            {
                if (cartridgeDir.isDirectory() && !cartridgeDir.getName().startsWith(".")
                        && ((new File(cartridgeDir, "build.gradle")).exists() || (new File(cartridgeDir, "build.gradle.kts")).exists()))
                {
                    migrator.migrate(cartridgeDir.toPath(), context);
                }
            }
            gitRepository.ifPresent(r -> commitChanges(r, step));
        }

        LOGGER.info(context.generateSummaryReport());
    }

    /**
     * Migrate one project (cartridge)
     * @param projectDir the project to migrate
     */
    protected void migrateProject(File projectDir)
    {
        MigrationStepFolder steps = MigrationStepFolder.valueOf(migrationStepFolder.toPath());
        List<MigrationStep> allSteps = steps.getSteps();

        if (!prepareMigrate(projectDir, false, allSteps))
        {
            return;
        }

        for(MigrationStep step : allSteps)
        {
            MigrationPreparer migrator = step.getMigrator();

            migrator.migrate(projectDir.toPath(), context);
            gitRepository.ifPresent(r -> commitChanges(r, step));
        }

        LOGGER.info(context.generateSummaryReport());
    }

    /**
     * Prepares the migration by executing all preparers for each migration step. This method is called before the
     * actual migration process starts.
     *
     * @param projectDir the project directory to prepare for migration
     * @param isRoot {@code true} if the project is a root project, {@code false} otherwise
     * @param allSteps the list of all migration steps to be executed
     * @return {@code true} if preparation was successful, {@code false} if there were critical errors
     */
    protected boolean prepareMigrate(File projectDir, boolean isRoot, List<MigrationStep> allSteps)
    {
        for (MigrationStep step : allSteps)
        {
            MigrationPreparer migrator = step.getMigrator();
            if (isRoot)
            {
                migrator.prepareMigrateRoot(projectDir.toPath(), context);
            }
            else
            {
                migrator.prepareMigrate(projectDir.toPath(), context);
            }
        }

        if (context.hasCriticalError())
        {
            LOGGER.error("Migration preparation aborted due to critical errors:\n  - {}",
                    String.join("\n  - ", context.getCriticalErrors()));
            return false;
        }

        LOGGER.info("Migration preparation completed successfully.");
        return true;
    }

    /**
     * Commit changes to the git repository if there are any uncommited changes or new files in the repository.
     * @param repository repository instance to commit changes
     * @param step current migration step
     */
    protected void commitChanges(GitRepository repository, MigrationStep step)
    {
        if (!repository.isClean())
        {
            String commitMessage = step.getMessage();
            String sha = repository.commit(commitMessage);
            LOGGER.info("Commited changes of migration step to git repository at '{}' with message '{}'.", sha, commitMessage);
        }
    }

    public void validateGitRepository() throws GitValidationException
    {
        if (gitRepository.isEmpty())
        {
            return; // No git repository initialized, nothing to validate
        }

        // Check if the git repository is clean
        if (!gitRepository.get().isClean())
        {
            String message = String.format(
                            "Git repository at '%s' is not clean. Please commit or discard any changes before running the migration.",
                            gitRepository.get().getRepositoryDirectory());
            throw new GitValidationException(message);
        }

        String message = String.format("Git repository at '%s' is clean and ready for migration.",
                        gitRepository.get().getRepositoryDirectory());
        LOGGER.debug(message);
    }
}
