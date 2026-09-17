package com.intershop.customization.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exit-code cover for {@link Migrator}.
 * <p>
 * The main consumer of this tool is a script or an agent that decides what happened from the exit code. A run that
 * did not happen must not report success, which an earlier version did: it logged a git validation failure and then
 * fell off the end of {@code main} with status 0.
 */
class MigratorTest
{
    @Test
    void missingArgumentsAreAUsageError()
    {
        assertEquals(Migrator.EXIT_USAGE, Migrator.run(new String[] { "project" }));
    }

    @Test
    void aProjectPathThatIsNotADirectoryIsReported(@TempDir Path tempDir)
    {
        Path notADirectory = tempDir.resolve("nope");
        assertEquals(Migrator.EXIT_BAD_PROJECT_PATH,
                        Migrator.run(new String[] { "project", notADirectory.toString(), tempDir.toString() }));
    }

    @Test
    void anUnknownTaskIsAUsageError(@TempDir Path tempDir)
    {
        assertEquals(Migrator.EXIT_USAGE,
                        Migrator.run(new String[] { "frobnicate", tempDir.toString(), tempDir.toString() }));
    }

    /**
     * The regression that matters: a dirty working tree aborts the migration, so it must not exit 0.
     */
    @Test
    void aDirtyRepositoryFailsWithItsOwnExitCode(@TempDir Path tempDir) throws IOException, GitAPIException
    {
        Path project = Files.createDirectories(tempDir.resolve("project"));
        try (Git git = Git.init().setDirectory(project.toFile()).call())
        {
            Files.writeString(project.resolve("tracked.txt"), "one");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("init").setSign(false).call();

            // Leave something uncommitted.
            Files.writeString(project.resolve("uncommitted.txt"), "two");
        }

        Path report = tempDir.resolve("report");
        int exit = Migrator.run(new String[] { "project", project.toString(), tempDir.toString(),
                        "--report=" + report });

        assertEquals(Migrator.EXIT_GIT_VALIDATION, exit,
                        "a dirty working tree must not report success");
    }

    @Test
    void theReportDirectoryDefaultsInsideTheToolsOwnBuildDirectory()
    {
        // Never inside the migrated project: auto-commit stages with 'git add .' and would sweep the report
        // into the project's history.
        assertEquals(Path.of("build/migration-report"), Migrator.reportDirectory(new String[] { "projects", "a", "b" }));
    }

    @Test
    void theReportDirectoryCanBeOverridden()
    {
        assertEquals(Path.of("/tmp/elsewhere"),
                        Migrator.reportDirectory(new String[] { "projects", "a", "b", "--report=/tmp/elsewhere" }));
    }

    @Test
    void aCleanRunWritesAnOperationLogAndReportsSuccess(@TempDir Path tempDir) throws IOException, GitAPIException
    {
        Path project = Files.createDirectories(tempDir.resolve("mycartridge"));
        Files.createDirectories(project.resolve("staticfiles/cartridge/pipelines"));
        Files.writeString(project.resolve("staticfiles/cartridge/pipelines/Test.pipeline"), "<pipeline/>");
        Files.writeString(project.resolve("build.gradle"), "dependencies { }\n");

        try (Git git = Git.init().setDirectory(project.toFile()).call())
        {
            git.add().addFilepattern(".").call();
            git.commit().setMessage("init").setSign(false).call();
        }

        Path steps = Files.createDirectories(tempDir.resolve("steps"));
        Files.copy(Path.of("src/main/resources/migration/001_migration_7x10_to_11/020_MoveFolder.yml"),
                        steps.resolve("020_MoveFolder.yml"));

        Path report = tempDir.resolve("report");
        int exit = Migrator.run(new String[] { "project", project.toString(), steps.toString(),
                        "--report=" + report });

        assertEquals(Migrator.EXIT_OK, exit);

        Path log = report.resolve("operations.jsonl");
        assertTrue(Files.isRegularFile(log), "the run must leave a structured operation log");
        String content = Files.readString(log);
        assertTrue(content.contains("\"event\":\"step-start\""), content);
        assertTrue(content.contains("\"step\":\"020_MoveFolder\""),
                        "operations must be attributed to the step that produced them");
        assertTrue(content.contains("\"event\":\"step-end\""), content);
    }
}
