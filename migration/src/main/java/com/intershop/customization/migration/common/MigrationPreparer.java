package com.intershop.customization.migration.common;

import java.nio.file.Path;

public interface MigrationPreparer
{
    /**
     * @param resource contains information, what needs to be migrated
     */
    void migrate(Path resource);

    /**
     * Define options for migrator
     * @param step assigns a migration step to the preparer
     */
    default void setStep(MigrationStep step)
    {
    }

    /**
     * Gets the commit message to use when committing the changes of this migration step.
     * @return the name of the migration step
     */
    default String getCommitMessage()
    {
        return "refactor: " + getClass().getSimpleName();
    }

    /**
     * Gets the name of the resource (cartridge).
     * @param resource the resource to get the name from
     * @return the name of the resource (cartridge)
     */
    default String getResourceName(Path resource)
    {
        if (resource == null)
        {
            return null;
        }
        return resource.getName(resource.getNameCount() - 1).toString();
    }
}
