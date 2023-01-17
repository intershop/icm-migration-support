package com.intershop.customization.migration.common;

import java.nio.file.Path;

public interface MigrationPreparer
{
    /**
     * @param resource
     * @return
     */
    void migrate(Path resource);

    /**
     * Define options for migrator
     * @param options
     */
    default void setStep(MigrationStep step)
    {
    }
}
