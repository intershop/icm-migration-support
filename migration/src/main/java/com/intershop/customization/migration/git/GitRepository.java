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

    public GitRepository(File repositoryDirectory) throws GitInitializationException
    {
        this.repositoryDirectory = repositoryDirectory;

        File gitDir = new File(repositoryDirectory, ".git");
        if (!gitDir.exists() || !gitDir.isDirectory())
        {
            LOGGER.error("Given project dir '{}' is no git repository .", repositoryDirectory);
            throw new GitInitializationException("Given project dir '" + repositoryDirectory + "' is no git repository.", null);
        }

        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        try
        {
            Repository repository = builder.setGitDir(gitDir)
                                           .readEnvironment() // scan environment GIT_* variables
                                           .build();
            this.git = new Git(repository);
        }
        catch(IOException e)
        {
            LOGGER.error("Failed to initialize git repository at {}.", repositoryDirectory, e);
            throw new GitInitializationException("Failed to initialize git repository at " + repositoryDirectory, e);
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
