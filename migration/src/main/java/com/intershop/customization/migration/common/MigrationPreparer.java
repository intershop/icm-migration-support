package com.intershop.customization.migration.common;

import java.nio.file.Path;

/**
 * Interface for implementing migration preparers that handle specific migration tasks.
 * </p>
 * A migration preparer is responsible for transforming resources according to migration rules. Implementations should
 * provide logic for modifying directories, files, and other resources to ensure compatibility with target platform
 * versions.
 */
public interface MigrationPreparer
{
    /**
     * Migrates a resource.
     *
     * @param resource Path to the resource that needs to be migrated
     *
     * @deprecated Use {@link #migrate(Path, MigrationContext)} instead.
     */
    @Deprecated(forRemoval = true)
    default void migrate(Path resource)
    {
    }

    /**
     * Migrates resources at the root level.
     * This is typically used for project-wide migrations that need to be applied once.
     *
     * @param resource Path to the root directory that needs to be processed
     *
     * @deprecated Use {@link #migrateRoot(Path, MigrationContext)} instead.
     */
    @Deprecated(forRemoval = true)
    default void migrateRoot(Path resource)
    {
    }

    /**
     * Prepares a resource for migration. This method is called before the actual migration process starts.
     * It can be used to perform any necessary setup or validation on the resource.
     *
     * @param resource Path to the resource that needs to be prepared
     * @param context The migration context for tracking operations and their results
     */
    default void prepareMigrate(Path resource, MigrationContext context)
    {
    }

    /**
     * Prepares a root resource for migration. This method is called before the actual migration process starts.
     * It can be used to perform any necessary setup or validation on a resource at root level.
     *
     * @param resource Path to the root directory that needs to be prepared
     * @param context The migration context for tracking operations and their results
     */
    default void prepareMigrateRoot(Path resource, MigrationContext context)
    {
    }

    /**
     * Migrates a resource with context tracking.
     * It allows recording success, failures, and other metrics.
     *
     * @param resource Path to the resource that needs to be migrated
     * @param context The migration context for tracking operations and their results
     */
    default void migrate(Path resource, MigrationContext context)
    {
        migrate(resource);
    }

    /**
     * Migrates resources at the root level with context tracking.
     * It allows recording success, failures, and other metrics.
     *
     * @param resource Path to the root directory that needs to be processed
     * @param context The migration context for tracking operations and their results
     */
    default void migrateRoot(Path resource, MigrationContext context)
    {
        migrateRoot(resource);
    }

    /**
     * Configures this preparer with a migration step, which may contain options and other configuration details needed
     * for the migration process.
     *
     * @param step The migration step containing configuration options for this preparer
     */
    default void setStep(MigrationStep step)
    {
    }

    /**
     * Extracts the name of the resource from its path. For cartridge migrations, this is typically the cartridge name.
     *
     * @param resource The resource path to extract the name from
     * @return The name of the resource, or null if the resource path is null
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
