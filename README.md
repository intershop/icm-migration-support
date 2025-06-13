# How to Work with the ICM Migration Support

ICM Migration Support is a tool that assists with migrating from one major ICM version to another.
It aims to streamline the migration process by reducing repetitive tasks and providing a set of executable migration steps.
However, it is not a fully automated migration solution; some manual steps remain necessary.

ICM Migration Support is an open source project provided by Intershop.  
It is available as source code only.

## Table of Contents

- [Prerequisites](#prerequisites)
    - [Clone or Download as Source Code](#clone-or-download-as-source-code)
    - [Compile and Test](#compile-and-test)
    - [Kotlin Environment](#kotlin-environment)
- [Preparation](#preparation)
    - [Backup IS 7.10 Cartridge List](#backup-is-710-cartridge-list)
    - [IS 7.10 Cartridge Migration Start](#is-710-cartridge-migration-start)
- [Migration](#migration)
    - [Define Environment Variable ICM](#define-environment-variable-icm)
    - [Adjust Log Level](#adjust-log-level)
    - [Auto Commit](#auto-commit)
    - [Migration All at Once](#migration-all-at-once)
    - [Migration Step by Step](#migration-step-by-step)
    - [Available Migration Steps](#available-migration-steps)
    - [Third Party Libraries](#third-party-libraries)


## Prerequisites

### Clone or Download as Source Code

This tool is available as source code only.  
Clone or download the [ICM Migration Support](https://github.com/intershop/icm-migration-support) project to your computer, for example by using the following command:

```bash
git clone git@github.com:intershop/icm-migration-support.git
```

### Compile and Test

This tool is based on Java 21 (same version as used by ICM 12 & ICM 13). An appropriate JDK must be installed and configured in your environment.

Then, e.g. set JAVA_HOME in your command line (Windows):
```
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.6.7-hotspot
```

To compile and test the tool open a command line where Java is configured/available (see above) and go to the root directory of this tool (icm-migration-support) and execute:
```bash
gradlew clean compileTestJava
```

### Kotlin Environment

One step (001_migration_7x10_to_11/
070_ConvertToKotlin.yml) requires a Kotlin environment. The Kotlin compiler must be installed and configured in your environment.
- You can use e.g. Android Studio to get a Kotlin environment
    - Because Kotlin is not in the PATH after installing Android Studio on Windows, set e.g.:
        ```
        set PATH=%PATH%;C:\Program Files\Android\Android Studio\plugins\Kotlin\kotlinc\bin
        ```
        - Test it by calling `kotlin.bat` or `kotlinc.bat`

## Preparation

### Backup IS 7.10 Cartridge List

Perform a backup of the cartridge list of your deployed ICM 7.10 project.
Since the cartridge list in ICM 11+ is generated based on the declared dependencies, the current list becomes important later to compare the cartridge list of the ICM 7.10 project with the generated one.

### IS 7.10 Cartridge Migration Start

Start the IS 7.10 --> ICM 11+ migration with the IS 7.10 source code cartridges in the new ICM 11+ GIT repository.  
(This will not build/compile, but the ICM 11+ GIT repository is the right place to execute the migration of the IS 7.10 cartridges.)

Copy all IS 7.10 cartridges into the new ICM 11+ GIT repository, except:
- gradle
    - The gradle environment is not a cartridge and it is specific to the ICM version and there exists already a gradle environment in the ICM 11+ repository that shouldn't be changed
- Any cartridges where there is already a replacement in ICM 11+
    - E.g. pf_configuration_fs, some payment cartridges, ...
- Assembly
    - assembly_* "cartridges" (directories)
    - Assemblies are removed anyway in the first migration step

Commit the IS 7.10 cartridges in the new ICM 11+ GIT repository.

## Migration

### Define Environment Variable ICM

(This step is optional, but recommended.)

Set an environment variable `ICM` where the cartridges to be migrated are located, on Windows e.g.:
```
set ICM=D:\ICM\PRJXX\projectxx-icm
```

You can test the environment variable using (Windows):
```
set ICM
dir %ICM%
```

### Adjust Log Level

(This step is optional.)

By default, the logging prints only information of level `INFO` or higher.  
To enable DEBUG-level logging, set the system property `COM_INTERSHOP_LOG_LEVEL` to the desired log level before executing any Gradle tasks.  
In `migration/src/main/resources/logback.xml`, switch between these lines:
```
<logger name="com.intershop" level="${COM_INTERSHOP_LOG_LEVEL:-INFO}" />
<logger name="com.intershop" level="${COM_INTERSHOP_LOG_LEVEL:-DEBUG}" />
<logger name="com.intershop" level="${COM_INTERSHOP_LOG_LEVEL:-WARN}" />
<logger name="com.intershop" level="${COM_INTERSHOP_LOG_LEVEL:-ERROR}" />
<logger name="com.intershop" level="${COM_INTERSHOP_LOG_LEVEL:-TRACE}" />
```

### Auto Commit

The ICM project to be migrated must be managed by a Git repository, checked out locally. Otherwise you always have to use `-PnoAutoCommit`.

The migration tool tries to commit changes automatically after each step.  
This fine-grained commit approach allows users to revert changes step by step if necessary.

Please note, that the `.git` folder is looked up in the given target directory or one level above the given target directory.

To disable the auto commit, set the `-PnoAutoCommit` parameter.

### Migration All at Once

Use the following command to execute all migration steps on all subprojects within a directory:
```
gradlew :migration:migrateAll -Ptarget=<path_to_7_10_project> -Psteps=<path_to_migration_steps> [-PnoAutoCommit]
```

### Migration Step by Step

Use the following commands to execute specific migration steps.  
Execute migration step for one cartridge only:
```
gradlew :migration:migrateOne -Ptask=project -Ptarget=<path_to_7_10_project>/your_cartridge -Psteps=<path_to_single_migration_step> [-PnoAutoCommit]
### Example (Windows):
gradlew :migration:migrateOne -Ptask=project -Ptarget=%ICM%/my_cartridge -Psteps=src/main/resources/migration/001_migration_7x10_to_11/010_MoveFolder.yml
```
Execute migration step for all cartridges in the target directory:
```
gradlew :migration:migrateOne -Ptask=projects -Ptarget=<path_to_7_10_project> -Psteps=<path_to_single_migration_step> [-PnoAutoCommit]
### Example (Windows):
gradlew :migration:migrateOne -Ptask=projects -Ptarget=%ICM% -Psteps=src/main/resources/migration/001_migration_7x10_to_11/010_MoveFolder.yml
```

### Available Migration Steps

The migration steps are located in the `migration/src/main/resources/migration/` folder of the project and are organized by major ICM versions. This allows to migrate from one major version to another while considering different starting points and supporting migration in more maintainable steps.  
**Please read the following documentation, containing also _Manual Migration Steps_**:

* [Migration 7.10 to 11](docs/migration-7.10-11.md)
* [Migration 11 to 12](docs/migration-11-12.md)

### Third Party Libraries

This project reuses code from the project [GradleKotlinConverter](https://github.com/bernaferrari/GradleKotlinConverter), licensed under the Apache License 2.0. 
The code from the project was adapted to fit the needs of this project.
Comments in the function `applyConversions()` of the _Gradle Kotlin DSL converter_ code identify all changes.