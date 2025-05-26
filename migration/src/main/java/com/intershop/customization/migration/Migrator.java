package com.intershop.customization.migration;

import java.io.File;
import java.util.Arrays;
import java.util.Optional;

import com.intershop.customization.migration.common.MigrationContext;
import com.intershop.customization.migration.common.MigrationPreparer;
import com.intershop.customization.migration.common.MigrationStep;
import com.intershop.customization.migration.common.MigrationStepFolder;
import com.intershop.customization.migration.git.GitInitializationException;
import com.intershop.customization.migration.git.GitRepository;

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

    private final File migrationStepFolder;
    private Optional<GitRepository> gitRepository = Optional.empty();
    private MigrationContext context = new MigrationContext();

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
        catch(Exception e)
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
                this.gitRepository = Optional.of(new GitRepository(projectPath));
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

        for (MigrationStep step: steps.getSteps())
        {
            MigrationPreparer migrator = step.getMigrator();
            migrator.migrateRoot(rootProject.toPath(), context);

            File[] files = rootProject.listFiles();
            if (files == null)
            {
                return;
            }
            for (File cartridgeDir: files)
            {
                if (cartridgeDir.isDirectory() && !cartridgeDir.getName().startsWith(".") && (new File(cartridgeDir, "build.gradle")).exists())
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
        for(MigrationStep step: steps.getSteps())
        {
            MigrationPreparer migrator = step.getMigrator();

            migrator.migrate(projectDir.toPath(), context);
            gitRepository.ifPresent(r -> commitChanges(r, step));
        }

        LOGGER.info(context.generateSummaryReport());
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
}
