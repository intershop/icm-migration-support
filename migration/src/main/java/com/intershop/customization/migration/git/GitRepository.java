package com.intershop.customization.migration.git;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.eclipse.jgit.api.Git;

public class GitRepository implements Closeable
{
    private static final Logger LOGGER = LoggerFactory.getLogger(GitRepository.class);

    private final File repositoryDirectory;
    private final Git git;

    public GitRepository(File projectDirectory, int maxRepoSearchDepth) throws GitInitializationException
    {
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        File ceiling = projectDirectory;
        for (int i = 0; i < maxRepoSearchDepth && ceiling.getParentFile() != null; i++) {
            ceiling = ceiling.getParentFile();
        }

        try
        {
            Repository repository = builder.findGitDir(projectDirectory) // lookup .git dir
                                           .addCeilingDirectory(ceiling)    // limit search depth
                                           .readEnvironment()               // scan environment GIT_* variables
                                           .build();

            if (repository.getDirectory() == null)
            {
                LOGGER.error("No git repository found in project dir '{}' or parent directories.", projectDirectory);
                throw new GitInitializationException("No git repository found in project dir '" + projectDirectory + "' or parent directories.", null);
            }
            this.repositoryDirectory = repository.getDirectory();
            this.git = new Git(repository);
        }
        catch(IOException e)
        {
            LOGGER.error("Failed to initialize git repository at {}.", projectDirectory, e);
            throw new GitInitializationException("Failed to initialize git repository at " + projectDirectory, e);
        }
    }

    /**
     * Commits all changed files in the repository with the given commit message.
     * @param message the commit message to use for the commit
     * @return the SHA-1 hash of the commit or null if the commit failed. See error log for reason.
     */
    public String commit(String message)
    {
        String authorName = git.getRepository().getConfig().getString("user", null, "name");
        String authorEmail = git.getRepository().getConfig().getString("user", null, "email");

        if (authorName == null || authorEmail == null)
        {
            LOGGER.error("Author name or email not configured in git repository at {}. Committing changes not possible.", repositoryDirectory);
            return null;
        }

        try
        {
            git.add().addFilepattern(".").call();
            RevCommit revCommit = git.commit().setAll(true).setMessage(message).call();
            return revCommit.getId().getName();
        }
        catch(GitAPIException e)
        {
            // TODO break on error?
            LOGGER.error("Error while committing changes to git repository at {}.", repositoryDirectory, e);
            return null;
        }
    }

    /**
     * Checks if the Git status is clean. Means there are no uncommitted changes or untracked files in the git repository.
     * @return true if status is clean (no uncommitted changes or untracked files), false otherwise
     */
    public boolean isClean()
    {
        try
        {
            return git.status().call().isClean();
        }
        catch(GitAPIException e)
        {
            LOGGER.error("Error while checking the status of git repository  '{}'.", repositoryDirectory, e);
            return false;
        }
    }

    /**
     * Returns the directory of the git repository.
     * @return the directory of the git repository
     */
    public File getRepositoryDirectory()
    {
        return repositoryDirectory;
    }

    @Override
    public void close()
    {
        this.git.close();
    }
}
