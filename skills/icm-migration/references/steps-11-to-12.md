# ICM 11 to ICM 12 Migration Steps

This document outlines the migration process from ICM 11 to ICM 12. It includes the automated steps performed by the migration tool, as well as the manual steps required afterward.

## Table of Contents

- [Preparation Steps](#preparation-steps)
  - [Prepare ICM 12 Branch](#prepare-icm-12-branch)
- [Automated Migration Steps](#automated-migration-steps)
  - [Integrate OpenRewrite recipes](#integrate-openrewrite-recipes)
- [Manual Migration Steps](#manual-migration-steps)
  - [Run OpenRewrite on the Migration Project](#run-openrewrite-on-the-migration-project)
    - [Applied recipes](#applied-recipes)
  - [ISML Expression adaption](#isml-expression-adaption)
  
## Preparation Steps

### Prepare ICM 12 Branch

- Ensure that the branch for migration is checked out in your local repository and is up to date with the remote repository.
- Verify a successful Gradle build after applying the previous migration steps (from ICM 7.10 to ICM 11).

## Automated Migration Steps

The automated steps for migrating from ICM 11 to ICM 12 are defined in: `src/main/resources/migration/002_migration_11_to_12`.

Example command:
```
gradlew migration:migrateAll -Ptarget=$ICM -Psteps=src/main/resources/migration/002_migration_11_to_12
```

### Integrate OpenRewrite Recipes

Migrator: `ClasspathResourceFileCopier` 

This adds Intershop's migration recipes to the root of the project for use with OpenRewrite.

## Manual Migration Steps

### Run OpenRewrite on the Migration Project

Run OpenRewrite to apply Jakarta EE migration recipes to the migration project:
```
gradlew --init-script rewrite.gradle rewriteRun
```

> **Note:**  
> The migration process, especially when running OpenRewrite or other code transformation tools, may require a significant amount of memory.  
> Intershop recommends increasing the maximum heap size for Gradle by setting the `GRADLE_OPTS` environment variable, for example:  
> `set GRADLE_OPTS=-Xmx4G` (on Windows) or `export GRADLE_OPTS=-Xmx4G` (on Linux/macOS).

#### Applied Recipes

- Migration to Jakarta EE 10
- Migration to Java 21
- Migration to Gradle 8
- Migration to ICM 12
  - Migration of custom SAXParserPool
  - Migration of EncryptionManager
    - **Note**: Adjust the thrown exceptions in your custom code
  - Migration of ProductListResource
  - Migration of ProcessChain XSD
  - Migration of custom JobMgr implementation
    - Added method implementations for
      - `setEnableJobProcessors(Collection<String>)`
      - `createJobCrontabTimeCondition(Domain, Date, String)`
    - **Note:** Add implementation for method `isJobAllowedOnServer(ServerInfo, JobConfiguration)`

### ISML Expression Adaption

      The ISML expression logic contained an evaluation failure if a conditional value was undefined. The issue was fixed in ICM 12, but requires an adaption of the ISML expressions in the project code.
      See [Guide - 12.x.x API Changes](https://knowledge.intershop.com/kb/index.php/Display/312H13) for more details.

