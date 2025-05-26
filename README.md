How to Work with the ICM Migration Support
==========================================

A tool to support the migration from one major ICM version to another. The goal is supporting
the migration by reducing repetitive tasks by providing a set of migration steps that can be executed.
It is not a complete migration tool, that migrates everything automatically. Some manual steps are still required.

## Table of Contents
- [Prerequisites](#prerequisites)
- [Preparation](#preparation)
- [Migration](#migration)
  - [Migration all at once](#migration-all-at-once)
  - [Migration step by step](#migration-step-by-step)
  - [Available Migration Steps](#available-migration-steps)
- [Third Party Libraries](#third-party-libraries)


## Prerequisites
* This tool is based on Java 21. An appropriate JDK must be installed and configured in your environment.
* One step requires a Kotlin environment. The Kotlin compiler must be installed and configured in your environment.
* The ICM project to be migrated must be managed by Git repository, checked out and deployed locally.

## Preparation
Perform a backup of the cartridge list of your deployed ICM 7.10 project.
Since the cartridge list in ICM 11+ is generated based on the declared dependencies, the current list becomes later 
important to compare the cartridge list of the ICM 7.10 project with the generated one.

## Migration
As stated in the beginning, the migration tool tries to commit the made changes after each step.
By this fine granular commit approach, it is possible to revert the changes step by step.
Currently, it is not possible to use that option with a subproject because the `.git` folder is not available in the subproject level.
To disable the auto commit, the `-PnoAutoCommit` parameter must be set.

### Migration all at once

This command will execute all migration steps on all subprojects of the $ICM directory
```
gradlew migration:migrateAll -Ptarget=$ICM -Psteps=<path_to_migration_steps> [-PnoAutoCommit]
```

### Migration step by step

- run migration script

```
gradlew migration:migrateOne -Ptask=project -Ptarget=$ICM/your_cartridge -Psteps=<path_to_single_migration_step> [-PnoAutoCommit]
gradlew migration:migrateOne -Ptask=projects -Ptarget=$ICM -Psteps=<path_to_single_migration_step> [-PnoAutoCommit]
```

### Available Migration Steps
The migration steps are located in the `migration/src/main/resources/migration` folder of the project and grouped by major ICM versions. This allows to
migrate from one major version to another, while keeping different start points in mind and supporting a migration in more maintainable steps.

* [Migration 7.10 to 11](docs/migration-7.10-11.md)

### Third Party Libraries
This project re-uses code from the project [GradleKotlinConverter](https://github.com/bernaferrari/GradleKotlinConverter), licensed under the Apache License 2.0. The code from the 
project was adapted to fit the needs of this project. All changes are marked in the "Gradle Kotlin DSL converter" code with a comment
in the function `applyConversions()`.
