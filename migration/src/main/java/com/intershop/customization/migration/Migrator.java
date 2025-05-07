package com.intershop.customization.migration;

import java.io.File;
import java.util.Arrays;
import java.util.Optional;

import com.intershop.customization.migration.common.MigrationPreparer;
import com.intershop.customization.migration.common.MigrationStep;
import com.intershop.customization.migration.common.MigrationStepFolder;
import com.intershop.customization.migration.git.GitInitializationException;
import com.intershop.customization.migration.git.GitRepository;

import org.slf4j.LoggerFactory;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class Migrator
{
    public static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(Migrator.class);
    private static final int POS_TASK = 0;
    private static final int POS_PATH = 1;
    private static final int POS_STEPS = 2;

    private static final String OPTION_NO_AUTO_COMMIT = "--noAutoCommit";

    private final File projectPath;
    private final File migrationStepFolder;
    private Optional<GitRepository> gitRepository = Optional.empty();

    /**
     * Initializes the migrator
     * @param projectPath directory of project to migrate
     * @param migrationStepFolder folder containing the migration step descriptions
     */
    public Migrator(File projectPath, File migrationStepFolder)
    {
        this.projectPath = projectPath;
        this.migrationStepFolder = migrationStepFolder;
    }

    /**
     * @param args the array of command line arguments
     * <li>"project" as task</li>
     * <li>directory to project app_sf_responsive</li>
     * <li>directory to migration steps like src/main/resources/001_migration_7.10-11.0.8</li>
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

                Migrator migrator = new Migrator(projectPath, new File(args[POS_STEPS]));
                // TODO also check if the project is a git repository?
                migrator.initializeGitRepository(Arrays.stream(args)
                                                       .noneMatch(o -> o.equalsIgnoreCase(OPTION_NO_AUTO_COMMIT)));

                if ("project".equals(args[POS_TASK]))
                {
                    LOGGER.info("Convert project at {}.", projectPath);
                    migrator.migrateProject(projectPath);
                }
                else if ("projects".equals(args[POS_TASK]))
                {
                    LOGGER.info("Convert projects at {}.", projectPath);
                    migrator.migrateProjects();
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
     * @param autoCommit if true, the git repository will be initialized and changes will be committed automatically
     */
    public void initializeGitRepository(boolean autoCommit)
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
    protected void migrateProjects()
    {
        MigrationStepFolder steps = MigrationStepFolder.valueOf(migrationStepFolder.toPath());
        // TODO How to distinct between root project and cartridges? - Not all Preparer are suitable for both!
        for (MigrationStep step: steps.getSteps())
        {
            MigrationPreparer migrator = step.getMigrator();

            migrator.migrate(projectPath.toPath());
            gitRepository.ifPresent(r -> commitChanges(r, migrator));
        }

        File[] files = projectPath.listFiles();
        if (files == null)
        {
            return;
        }

        for (File fileOrDir: files)
        {
          if (fileOrDir.isDirectory() && !fileOrDir.getName().startsWith(".") && (new File(fileOrDir, "build.gradle")).exists())
            {
                migrateProject(fileOrDir);
            }
        }
    }

    /**
     * Migrate on project
     * @param projectDir the project to migrate
     */
    protected void migrateProject(File projectDir)
    {
        MigrationStepFolder steps = MigrationStepFolder.valueOf(migrationStepFolder.toPath());
        for(MigrationStep step: steps.getSteps())
        {
            MigrationPreparer migrator = step.getMigrator();

            migrator.migrate(projectDir.toPath());
            gitRepository.ifPresent(r -> commitChanges(r, migrator));
        }
    }

    /**
     * Commit changes to the git repository if there are any uncommited changes in the repository.
     * @param repository repository instance to commit changes
     * @param migrator current migration preparer
     */
    protected void commitChanges(GitRepository repository, MigrationPreparer migrator)
    {
        if (repository.hasUncommittedChanges())
        {
            String commitMessage = migrator.getCommitMessage();
            String sha = repository.commit(commitMessage);
            LOGGER.debug("Commited changes of migration step to git repository at '{}' with message '{}'.", sha, commitMessage);
        }
    }
}
