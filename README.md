How to Work with the ICM Migration Support
==========================================

ICM Migration Support is a tool that assists with migrating from one major ICM version to another.
It aims to streamline the migration process by reducing repetitive tasks and providing a set of executable migration steps.
However, it is not a fully automated migration solution; some manual steps remain necessary.

## Table of Contents
- [Prerequisites](#prerequisites)
- [Preparation](#preparation)
- [Migration](#migration)
  - [Migration All at Once](#migration-all-at-once)
  - [Migration Step by Step](#migration-step-by-step)
  - [Available Migration Steps](#available-migration-steps)
  - [Third Party Libraries](#third-party-libraries)


## Prerequisites
* This tool is based on Java 21. An appropriate JDK must be installed and configured in your environment.
* One step requires a Kotlin environment. The Kotlin compiler must be installed and configured in your environment.
* The ICM project to be migrated must be managed by a Git repository, checked out and deployed locally.

## Preparation
Perform a backup of the cartridge list of your deployed ICM 7.10 project.
Since the cartridge list in ICM 11+ is generated based on the declared dependencies, the current list becomes important later to compare the cartridge list of the ICM 7.10 project with the generated one.

## Migration
The migration tool tries to commit changes automatically after each step.  
This fine-grained commit approach allows users to revert changes step by step if necessary.  

Currently, this option cannot be used with a subproject because the `.git` folder is unavailable at the subproject level.

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
The migration steps are located in the `migration/src/main/resources/migration` folder of the project and are organized by major ICM versions. This allows to migrate from one major version to another while considering different starting points and supporting migration in more maintainable steps.

* [Migration 7.10 to 11](docs/migration-7.10-11.md)

### Third Party Libraries
This project reuses code from the project [GradleKotlinConverter](https://github.com/bernaferrari/GradleKotlinConverter), licensed under the Apache License 2.0. 
The code from the project was adapted to fit the needs of this project. 
All changes are marked in the "Gradle Kotlin DSL converter" code with a comment in the function `applyConversions()`.
