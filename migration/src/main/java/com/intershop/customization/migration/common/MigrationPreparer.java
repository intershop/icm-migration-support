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
     * @param step
     */
    default void setStep(MigrationStep step)
    {
    }
}
