package com.intershop.customization.migration;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.intershop.customization.migration.common.MigrationContext;
import com.intershop.customization.migration.common.MigrationPreparer;
import com.intershop.customization.migration.common.MigrationStep;
import com.intershop.customization.migration.common.MigrationStepFolder;
import com.intershop.customization.migration.common.OperationLog;
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
    private static final String OPTION_REPORT = "--report=";
    private static final String DEFAULT_REPORT_DIR = "build/migration-report";
    private static final int MAX_SEARCH_DEPTH_FOR_GIT_REPO = 1;

    /**
     * Exit codes. Distinct on purpose: the caller is usually a script or an agent that has to decide what happened
     * without parsing the log, and "it failed" is not an actionable answer.
     */
    static final int EXIT_OK = 0;
    static final int EXIT_USAGE = 1;
    static final int EXIT_BAD_PROJECT_PATH = 2;
    static final int EXIT_GIT_VALIDATION = 3;
    static final int EXIT_CRITICAL_ERROR = 4;
    static final int EXIT_FAILED_OPERATIONS = 5;

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

    public MigrationContext getContext()
    {
        return context;
    }

    /**
     * @param args the array of command line arguments
     * <li>"project" as task</li>
     * <li>directory to project app_sf_responsive</li>
     * <li>directory to migration steps like src/main/resources/001_migration_7x10_to_11</li>
     * <li>optional flags like "--noAutoCommit" and "--report=&lt;dir&gt;"</li>
     */
    public static void main(String[] args)
    {
        System.exit(run(args));
    }

    /**
     * Runs a migration and returns the process exit code.
     * <p>
     * Split out of {@link #main(String[])} so that every path returns a code rather than falling off the end. An
     * earlier version logged a git validation failure and then exited 0, which is the worst possible outcome for an
     * automated caller: it reports success for a migration that never ran.
     *
     * @param args the command line arguments
     * @return the exit code, see the {@code EXIT_*} constants
     */
    static int run(String[] args)
    {
        if (args.length < POS_STEPS + 1)
        {
            LOGGER.error("Missing parameter '{}'.", args.length);
            return EXIT_USAGE;
        }

        File projectPath = new File(args[POS_PATH]);
        if (!projectPath.exists() || !projectPath.isDirectory())
        {
            LOGGER.error("Project path '{}' is not a directory.", projectPath);
            return EXIT_BAD_PROJECT_PATH;
        }

        Migrator migrator = new Migrator(new File(args[POS_STEPS]));
        try (OperationLog log = OperationLog.open(reportDirectory(args)))
        {
            migrator.getContext().attachLog(log);

            migrator.initializeGitRepository(Arrays.stream(args)
                                                   .noneMatch(o -> o.equalsIgnoreCase(OPTION_NO_AUTO_COMMIT)),
                            projectPath);
            try
            {
                migrator.validateGitRepository();
            }
            catch(GitValidationException gve)
            {
                LOGGER.error("Validation of git repository failed: {}", gve.getMessage());
                return EXIT_GIT_VALIDATION;
            }

            boolean prepared;
            if ("project".equals(args[POS_TASK]))
            {
                LOGGER.info("Convert project at {}.", projectPath);
                prepared = migrator.migrateProject(projectPath);
            }
            else if ("projects".equals(args[POS_TASK]))
            {
                LOGGER.info("Convert projects at {}.", projectPath);
                prepared = migrator.migrateProjects(projectPath);
            }
            else
            {
                LOGGER.error("Unknown task '{}'. Expected 'project' or 'projects'.", args[POS_TASK]);
                return EXIT_USAGE;
            }

            if (!prepared)
            {
                return EXIT_CRITICAL_ERROR;
            }
            if (migrator.getContext().hasFailedOperations())
            {
                LOGGER.error("Migration completed with {} failed operation(s). See the summary above"
                                + " and the operation log for details.",
                                migrator.getContext().countByStatus(MigrationContext.OperationStatus.FAILED));
                return EXIT_FAILED_OPERATIONS;
            }
            return EXIT_OK;
        }
        catch(Exception e)
        {
            LOGGER.error("Unexpected error during migration", e);
            return EXIT_USAGE;
        }
        finally
        {
            // Closes the repository this migrator actually opened. An earlier version closed a separate local
            // variable that was never assigned, so the repository was never released.
            migrator.closeGitRepository();
        }
    }

    /**
     * Determines where the structured operation log is written. Defaults inside the tool's own build directory rather
     * than the migrated project, so that the auto-commit, which stages with {@code git add .}, cannot sweep the report
     * into the project's history.
     *
     * @param args the command line arguments
     * @return the directory to write the report to
     */
    static Path reportDirectory(String[] args)
    {
        return Arrays.stream(args)
                .filter(a -> a.startsWith(OPTION_REPORT))
                .map(a -> a.substring(OPTION_REPORT.length()))
                .findFirst()
                .map(Paths::get)
                .orElseGet(() -> Paths.get(DEFAULT_REPORT_DIR));
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

    public void closeGitRepository()
    {
        gitRepository.ifPresent(GitRepository::close);
    }

    /**
     * Migrate on root project
     *
     * @return {@code false} if preparation reported a critical error, {@code true} otherwise
     */
    protected boolean migrateProjects(File rootProject)
    {
        MigrationStepFolder steps = MigrationStepFolder.valueOf(migrationStepFolder.toPath());
        List<MigrationStep> allSteps = steps.getSteps();

        if (!prepareMigrate(rootProject, true, allSteps))
        {
            return false;
        }

        for (int index = 0; index < allSteps.size(); index++)
        {
            MigrationStep step = allSteps.get(index);
            context.beginStep(index, step.getName(), step.getMessage());
            MigrationPreparer migrator = step.getMigrator();

            migrator.migrateRoot(rootProject.toPath(), context);

            File[] files = rootProject.listFiles();
            if (files == null)
            {
                context.endStep(null);
                LOGGER.info(context.generateSummaryReport());
                return true;
            }
            for (File cartridgeDir : files)
            {
                if (cartridgeDir.isDirectory() && !cartridgeDir.getName().startsWith(".")
                        && ((new File(cartridgeDir, "build.gradle")).exists() || (new File(cartridgeDir, "build.gradle.kts")).exists()))
                {
                    migrator.migrate(cartridgeDir.toPath(), context);
                }
            }
            context.endStep(gitRepository.map(r -> commitChanges(r, step)).orElse(null));
        }

        LOGGER.info(context.generateSummaryReport());
        return true;
    }

    /**
     * Migrate one project (cartridge)
     * @param projectDir the project to migrate
     *
     * @return {@code false} if preparation reported a critical error, {@code true} otherwise
     */
    protected boolean migrateProject(File projectDir)
    {
        MigrationStepFolder steps = MigrationStepFolder.valueOf(migrationStepFolder.toPath());
        List<MigrationStep> allSteps = steps.getSteps();

        if (!prepareMigrate(projectDir, false, allSteps))
        {
            return false;
        }

        for (int index = 0; index < allSteps.size(); index++)
        {
            MigrationStep step = allSteps.get(index);
            context.beginStep(index, step.getName(), step.getMessage());
            MigrationPreparer migrator = step.getMigrator();

            migrator.migrate(projectDir.toPath(), context);
            context.endStep(gitRepository.map(r -> commitChanges(r, step)).orElse(null));
        }

        LOGGER.info(context.generateSummaryReport());
        return true;
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
     *
     * @return the commit SHA, or {@code null} if the step changed nothing
     */
    protected String commitChanges(GitRepository repository, MigrationStep step)
    {
        if (repository.isClean())
        {
            return null;
        }
        String commitMessage = step.getMessage();
        String sha = repository.commit(commitMessage);
        LOGGER.info("Commited changes of migration step to git repository at '{}' with message '{}'.", sha, commitMessage);
        return sha;
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
