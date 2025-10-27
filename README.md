# Introduction

This projects contains three major tools to support migration projects from ICM7.10 to ICM11+.

* The "migration tool" uses standard Java Application to migrate up to the latest version. Major tasks are exposed via gradle.
* The "AI migration tools" to [instruct](docs/ai-instructions/migrate-all-cartridges-instructions.md) Github Copilot (in agent mode) to help migrate from one version of the platform to the next. It follows the same essential steps as the Java-based migration tool. A training for this is available in the academy.
* The "AI dependency [instructions](docs/ai-instructions/dependencies-component-instances.md)" can be used after the migration to check if there are issues in the dependency structure and propose fixes. In agent mode, the Github Copilot can use this file to actually perform the fix to solve the issues.

## Table of Contents
- [Prerequisites](#prerequisites)
- [Preparation](#preparation)
- [Migration](#migration)
  - [Migration All at Once](#migration-all-at-once)
  - [Migration Step by Step](#migration-step-by-step)
  - [Available Migration Steps](#available-migration-steps)
  - [Third Party Libraries](#third-party-libraries)

# How to Work with the "Migration Tool"

ICM Migration Support is a tool that assists with migrating from one major ICM version to another.
It aims to streamline the migration process by reducing repetitive tasks and providing a set of executable migration steps.
However, it is not a fully automated migration solution; some manual steps remain necessary.

## Prerequisites
* This tool is based on Java 21. An appropriate JDK must be installed and configured in your environment.
* One step requires a Kotlin environment. The Kotlin compiler must be installed and configured in your environment.
* The ICM project to be migrated must be managed by a Git repository, checked out and deployed locally.

## Preparation
Perform a backup of the cartridge list of your deployed ICM 7.10 project.
Since the cartridge list in ICM 11+ is generated based on the declared dependencies, the current list becomes important later to compare the cartridge list of the ICM 7.10 project with the generated one.

## Migration
The migration tool tries to commit changes automatically after each step.  
This fine-grained commit approach allows users to revert automatically applied changes step by step if necessary.  

Please note, that the `.git` folder is looked up in the current directory or one level above the given target path.

To disable the auto commit, set the `-PnoAutoCommit` parameter.

### Migration All at Once

Use the following command to execute all migration steps on all subprojects within a directory:

```
gradlew migration:migrateAll -Ptarget=<path_to_7_10_project> -Psteps=<path_to_migration_steps> [-PnoAutoCommit]
```

### Migration Step by Step

Use the following commands to execute specific migration steps:

```
gradlew migration:migrateOne -Ptask=project -Ptarget=<path_to_7_10_project>/your_cartridge -Psteps=<path_to_single_migration_step> [-PnoAutoCommit]
gradlew migration:migrateOne -Ptask=projects -Ptarget=<path_to_7_10_project> -Psteps=<path_to_single_migration_step> [-PnoAutoCommit]
```

### Available Migration Steps
The migration steps are located in the _migration/src/main/resources/migration_ folder of the project and are organized by major ICM versions. This allows to migrate from one major version to another while considering different starting points and supporting migration in more maintainable steps.

* [Migration 7.10 to 11](docs/migration-7.10-11.md)
* [Migration 11 to 12](docs/migration-11-12.md)
* [Migration 12 to 13](docs/migration-12-13.md)

### Third Party Libraries
This project reuses code from the project [GradleKotlinConverter](https://github.com/bernaferrari/GradleKotlinConverter), licensed under the Apache License 2.0. 
The code from the project was adapted to fit the needs of this project.
Comments in the function `applyConversions()` of the _Gradle Kotlin DSL converter_ code identify all changes.