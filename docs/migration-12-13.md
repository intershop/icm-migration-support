# ICM 12 to ICM 13 Migration Steps

This document outlines the migration process from ICM 12 to ICM 13. It covers the automated processes handled by the migration tool, as well as the manual interventions necessary to complete the upgrade.

## Table of Contents

- [Preparation Steps](#preparation-steps)
  - [Prepare ICM 13 Branch](#prepare-icm-13-branch)
- [Automated Migration Steps](#automated-migration-steps)
  - [Integrate OpenRewrite Recipes](#integrate-openrewrite-recipes)
- [Manual Migration Steps](#manual-migration-steps)
  - [Run OpenRewrite on the Migration Project](#run-openrewrite-on-the-migration-project)
    - [Applied Recipes](#applied-recipes)

## Preparation Steps

### Prepare ICM 13 Branch

- Verify that your migration branch is properly checked out locally and synchronized with the remote repository.
- After completing the previous migration phase (ICM 11 to 12), ensure that your project builds successfully with Gradle.

## Automated Migration Steps

The automated steps for migrating from ICM 12 to ICM 13 are defined in: `src/main/resources/migration/003_migration_12_to_13`.

Example command:
```
gradlew migration:migrateAll -Ptarget=$ICM -Psteps=src/main/resources/migration/003_migration_12_to_13
```

### Integrate OpenRewrite Recipes

Migrator: `ClasspathResourceFileCopier`

This adds Intershop's migration recipes to the root of the project for use with OpenRewrite.

## Manual Migration Steps

### Run OpenRewrite on the Migration Project

Run OpenRewrite to apply migration recipes to the migration project:
```
gradlew --init-script rewrite.gradle rewriteRun
```

> **Note:**  
> The migration process, especially when running OpenRewrite or other code transformation tools, may require a significant amount of memory.  
> Intershop recommends increasing the maximum heap size for Gradle by setting the `GRADLE_OPTS` environment variable, for example:  
> `set GRADLE_OPTS=-Xmx4G` (on Windows) or `export GRADLE_OPTS=-Xmx4G` (on Linux/macOS).

#### Applied Recipes

- Migration to ICM 13
  - Migration of the EmailSendingHandler#send() to the new signature
  - Migrate BitSetDeserializer/BitSetSerializer to the new package
  - Migrate OrderListResource#getOrders_v1 to new signature
  - Migrate BasketFeedbackHandler to FeedbackHandler
  - Migrate EXTENSION_ID constant of CatalogBORepositoryExtension
  - Migrate deprecated ProductVariationMgr methods to their replacements
  - Migrate deprecated MVCatalogMgr methods to their replacements
  - Migrate deprecated ProductBOAttachmentsExtension methods to their replacements
  - Migrate ProductBORepository usages
  - Migrate ProductBO method names
  - Migrate ProductConfigurationValidatorValueListBO method names
  - Migrate AbstractProductConfigurationBO usages
  - Migrate AbstractProductConfigurationOptionSelectionBO usages
  - Migrate ProductPreConfigurationBORepository usages
  - Replace NumberSeriesProvider by NumberSequenceProvider
  - Migrate ChannelBO#getOwnedRepository to getOwnedRepositoryBO
  - Migrate CatalogMgr#getCatalogCategoryByUUID to resolveCatalogCategoryFromID