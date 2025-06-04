# ICM 11 to ICM 12 Migration Steps

This document outlines the migration process from ICM 11 to ICM 12, including automated steps performed by the migration tool and manual steps required afterward.

## Table of Contents

- [Preparation Steps](#preparation-steps)
- [Automated Migration Steps](#automated-migration-steps)
- [Manual Migration Steps](#manual-migration-steps)

## Preparation Steps

### Prepare ICM12 Branch

- Ensure that the branch for migration is checked out in your local repository and is up to date with the remote repository.
- Verify a successful Gradle build after applying the previous migration steps (from ICM 7.10 to ICM 11).

## Automated Migration Steps

The automated steps for migrating from 11 to 12 are defined in: `src/main/resources/migration/002_migration_11_to_12`.

Example command:
```
gradlew migration:migrateAll -Ptarget=$ICM -Psteps=src/main/resources/migration/002_migration_11_to_12
```

### Apply Jakarta EE Migration support

Migrator: `ClasspathResourceFileCopier` 

Applies Jakarta EE 10+ migration recipes if required (e.g., using OpenRewrite).
- add gradle init script `rewrite.gradle` to the root of the project

## Manual Migration Steps

### Run OpenRewrite on the migration project

Run OpenRewrite to apply Jakarta EE migration recipes on the migration project:
```
gradlew --init-script rewrite.gradle rewriteRun
```

> **Note:**  
> The migration process, especially when running OpenRewrite or other code transformation tools, can require a significant amount of memory.  
> It is recommended to increase the maximum heap size for Gradle by setting the `GRADLE_OPTS` environment variable, for example:  
> `set GRADLE_OPTS=-Xmx4G` (on Windows) or `export GRADLE_OPTS=-Xmx4G` (on Linux/macOS).
