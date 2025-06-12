package com.intershop.customization.migration.git;

/**
 * Exception thrown when a validation error occurs in the Git migration process.
 * This exception is used to indicate that the migration cannot proceed due to
 * issues with the Git repository or its configuration.
 */
public class GitValidationException extends Exception
{
    /**
     * Constructs a new GitValidationException with the specified detail message.
     *
     * @param message the detail message explaining the reason for the exception
     */
    public GitValidationException(String message)
    {
        super(message);
    }
}
