# ICM 7.10 to ICM 11 Migration Steps

This document outlines the migration process from ICM 7.10 to ICM 11. It includes the automated steps performed by the migration tool, as well as the manual steps required afterward.

## Table of Contents

- [Preparation Steps](#preparation-steps)
  - [Prepare ICM 11 Template](#prepare-icm-11-template)
  - [Verify ICM 11 Template](#verify-icm-11-template)
  - [Prepare ICM 11 Branch](#prepare-icm-11-branch)
- [Automated Migration Steps](#automated-migration-steps)
  - [Remove Assembly Projects](#remove-assembly-projects)
  - [Move Folder Structure](#move-folder-structure)
  - [Move Additional Files](#move-additional-files)
  - [Move Java Source Code](#move-java-source-code)
  - [Convert build.gradle Files](#convert-buildgradle-files)
  - [Convert to Cartridge Dependency](#convert-to-cartridge-dependency)
  - [Rename Dependencies](#rename-dependencies)
  - [Remove Obsolete Dependencies](#remove-obsolete-dependencies)
  - [Move DB Prepare Files](#move-db-prepare-files)
  - [Migrate Configuration Resources](#migrate-configuration-resources)
  - [Migrate Version Information](#migrate-version-information)
  - [Add Site Content Preparer](#add-site-content-preparer)
  - [Rename Packages](#rename-packages)
  - [Delete Obsolete Files](#delete-obsolete-files)
  - [Create Environment Example Files](#create-environment-example-files)
- [Manual Migration Steps](#manual-migration-steps)
  - [Globally Defined Dependencies](#globally-defined-dependencies)
  - [Remove Sites Folder Copy Tasks](#remove-sites-folder-copy-tasks)
  - [Verify and Correct Dependencies](#verify-and-correct-dependencies)

## Preparation Steps

> **Note:** The following markers are used in the commands below:
> - `$ICM`: A symbolic marker for the root directory of your ICM 7.10 project
> - `$ICM_11`: A symbolic marker for the root directory of the ICM 11+ project template

### Prepare ICM 11 Template
Retrieve the ICM 11 customization template and follow the prerequisite steps.

Use the customization template to create the initial project structure.

* Checkout customization template.
* Follow the documentation to configure the template.
  * Use versions for ICM 11 in the first step.
* Execute the initialization script.

As a result, the following files are created:
- `build.gradle.kts` - root build script to configure subprojects (cartridges)
  -- define and apply gradle repositories (allows to download ICM cartridges)
  -- apply version filter to subproject (allows central definition of versions at two subprojects `versions` and `versions_test`)
- `my_*` directories containing example cartridges for different purposes
- `ft_production` directory defines the cartridge set of production
- `ft_test` directory defines the test cartridge set for server tests (mostly test data)

### Verify ICM 11 Template
To ensure the following migration bases on a working template, verify the following:
1. Switch to your ICM 11+ project.
1. Check that result of customization template is working.
1. Set marker:

```
gradlew compileTestJava
export ICM_11="$PWD"
```

### Prepare ICM 11 Branch

To prepare the ICM branch:
- Create and checkout a feature branch on $ICM
- Copy result of customization template into $ICM (without overwriting the `.git` folder)

```
export ICM="$PWD"
git checkout -b feature/migration-to-11
rsync -av --exclude='.git' "$ICM_11/" "$ICM/"
```

> **Note:** When command `rsync` is not available on your system, you can copy the content using any available
> file copy command. Just make sure to **exclude** the `.git` folder.

## Automated Migration Steps

The automated steps for migrating from 7.10 to 11 are defined in the path: `src/main/resources/migration/001_migration_7x10_to_11`. 
This results in the following parameters for the Gradle task:

- **task**: Specifies the type of migration (`project` for a single project or `projects` for all projects). This parameter is only required for the `migrateOne` task.
- **target**: The root directory of your ICM 7.10 project (`$ICM`, if defined as described above).
- **steps**: The path to the definitions for the automated migration steps (in this case: `src/main/resources/migration/001_migration_7x10_to_11`).

Example command:
```
gradlew migration:migrateAll -Ptarget=$ICM -Psteps=src/main/resources/migration/001_migration_7x10_to_11
```

> **Note:** `{cartridgeName}` is a placeholder and will be replaced by the name of the cartridge to be migrated.

### Remove Assembly Projects

Migrator: `RemoveAssembly`

- Assembly projects handled cartridge lists for deployment in ICM 7.10.
- This removes assembly projects which are no longer needed in ICM 11.

### Move Folder Structure

Migrator: `MoveFolder`

Moves all static files to their new locations within the ICM 11 structure:

- `edl` -> `src/main/resources/resources/{cartridgeName}/edl`
- `staticfiles/cartridge/components` -> `src/main/resources/resources/{cartridgeName}/components`
- `staticfiles/cartridge/config` -> `src/main/resources/resources/{cartridgeName}/config`
- `staticfiles/cartridge/extensions` -> `src/main/resources/resources/{cartridgeName}/extensions`
- `staticfiles/cartridge/impex` -> `src/main/resources/resources/{cartridgeName}/impex`
- `staticfiles/cartridge/lib/resources/com` -> `src/main/resources/com`
- `staticfiles/cartridge/lib/resources/tests` -> `src/test/resources/tests`
- `staticfiles/cartridge/lib/resources/{cartridgeName}/dbinit` -> `src/main/resources/resources/{cartridgeName}/dbinit`
- `staticfiles/cartridge/lib/resources/{cartridgeName}/dbmigrate`-> `src/main/resources/resources/{cartridgeName}/dbmigrate`
- `staticfiles/cartridge/localizations` -> `src/main/resources/resources/{cartridgeName}/localizations`
- `staticfiles/cartridge/logback` -> `src/main/resources/resources/{cartridgeName}/logback`
- `staticfiles/cartridge/naming` -> `src/main/resources/resources/{cartridgeName}/naming`
- `staticfiles/cartridge/objectgraph` -> `src/main/resources/resources/{cartridgeName}/objectgraph`
- `staticfiles/cartridge/pagelets` -> `src/main/resources/resources/{cartridgeName}/pagelets`
- `staticfiles/cartridge/pipelines` -> `src/main/resources/resources/{cartridgeName}/pipelines`
- `staticfiles/cartridge/queries` -> `src/main/resources/resources/{cartridgeName}/queries`
- `staticfiles/cartridge/templates` -> `src/main/isml/{cartridgeName}`
- `staticfiles/cartridge/webforms` -> `src/main/resources/resources/{cartridgeName}/webforms`
- `staticfiles/share/sites` -> `src/main/resources/resources/{cartridgeName}/sites`
- `staticfiles/share/system/config/cartridges` -> `src/main/resources/cartridges`
- `staticfiles/share/system/config/cluster` -> `src/main/resources/resources/{cartridgeName}/config/cluster`
- `staticfiles/share/system/config/domains` -> `src/main/resources/resources/{cartridgeName}/config/domains`
- `staticfiles/share/system/config/apps` -> `src/main/resources/resources/{cartridgeName}/config/apps`

The following directories remain intentionally unchanged:
- `staticfiles/cartridge/static`
- `staticfiles/cartridge/urlrewrite`

### Move Additional Files

Migrator: `MoveFiles`

Moves specific files to their new locations within the ICM 11 structure:

- `staticfiles/cartridge/directCustomAttributes.xml` -> `src/main/resources/resources/{cartridgeName}/directCustomAttributes.xml`

### Move Java Source Code

Migrator: `MoveFolder`

Moves Java source code and pipelet XML files to their appropriate locations in the ICM 11 structure:

- `javasource` -> `src/main/java` (Java source files)
- `javasource` -> `src/main/resources` (only XML files matching pattern `^.*/pipelet/.*\.xml$`)

This step ensures that:

- All Java source files are properly located in the standard Gradle Java source directory
- Pipelet XML files are moved to the `resources` directory while maintaining their relative path structure

### Convert build.gradle Files

Migrator: `ConvertBuildGradle`

- Adapts plugins in `build.gradle` files to match ICM 11 requirements
- Updates build system configurations to the new structure

### Convert to Cartridge Dependency

Migrator: `ConvertToCartridgeDependency`

- Refactors Intershop dependencies in `build.gradle` files
- Updates dependency groups:
    - `com.intershop.platform`
    - `com.intershop.content`
    - `com.intershop.business`
    - `com.intershop.b2b`

### Rename Dependencies

Migrator: `RenamedDependency`

Renames dependencies in `build.gradle` files:

- `commons-lang:commons-lang` -> `org.apache.commons:commons-lang3`
- `commons-collections:commons-collections` -> `org.apache.commons:commons-collections4`

### Remove Obsolete Dependencies

Migrator: `RemovedDependency`

Removes dependencies that are no longer needed in ICM 11:

- `com.intershop.business:ac_inventory_service`

### Move DB Prepare Files

Migrator: `MoveFiles`

Moves database initialization and migration properties files (starting with `migration` or `dbinit`) to the new location:

- `staticfiles/cartridge` -> `src/main/resources/resources/{cartridgeName}`

### Migrate Configuration Resources

Migrator: `MigrateConfigResources`

Updates configuration resources to the ICM 11 format

### Migrate Version Information

Migrator: `MigrateVersionFiles`

- Transfers data from `*.version` files to `versions/build.gradle`
- Centralizes version management in ICM 11

### Add Site Content Preparer

Migrator: `AddSiteContentPreparer`

Injects `SiteContentPreparer` into site `dbinit.properties`

### Rename Packages

Migrator: `RenamedPackages`

Renames packages in Java and ISML source files:

- `commons-lang:commons-lang` -> `org.apache.commons:commons-lang3`
- `commons-collections:commons-collections` -> `org.apache.commons:commons-collections4`

### Delete Obsolete Files

Migrator: `RemoveFiles`

Removes files that are no longer needed in ICM 11:

- `*.version` files (version information is now stored in a central location)
- Root `build.gradle` and `settings.gradle` files (replaced by a customization template)

### Create Environment Example Files

Migrator: `CreateEnvironmentExampleFiles`

Creates or replaces the following example files in the project root with environment-specific content:

- `environment.bat.example`:
    - Created from `migration/src/main/resources/environment/environment.bat.example.template`
    - Placeholder `<rootProject.name in settings.gradle.kts>` will be replaced by the value of `rootProject.name` in `settings.gradle.kts`, for example: "prjzz-icm"
    - Placeholder `<ishprjxxacr>` will be replaced by the value of `dockerRegistry` in `gradle.properties`, for example: "ishprjzzacr.azurecr.io"
- `icm.properties.example`:
    - Created from `migration/src/main/resources/environment/icm.properties.example.template`
    - Placeholder `<rootProject.name in settings.gradle.kts>` will be replaced by the value of `rootProject.name` in `settings.gradle.kts`, for example: "prjzz-icm"
 - `clean.bat`:
    - Created from `migration/src/main/resources/environment/clean.bat.template`
    - `{cartridgeName}` will be replaced by one line per cartridge
    - `{cartridgeName.last}` will be replaced by the last cartridge in the list
    - `{cartridgeName}` will be replaced by one line per cartridge, except for the last cartridge in the list

## Manual Migration Steps

### Globally Defined Dependencies

Add centrally defined libraries to the subprojects section, as in 7.10, or better specify them in the dependencies of the subprojects as needed.

```
subprojects {
...
    plugins.withType<JavaPlugin> {

        dependencies {
            val cartridge by configurations
            val implementation by configurations
            val testImplementation by configurations
...
            // centrally defined libs
            implementation ("com.intershop.platform:bc_spreadsheet")
            implementation ("com.intershop.platform:pipeline")
            implementation ("javax.inject:javax.inject")
            implementation ("org.slf4j:slf4j-api")
            implementation ("ch.qos.logback:logback-core")
            implementation ("com.google.inject:guice")
            implementation ("org.apache.tomcat:tomcat-el-api")
            implementation ("org.apache.tomcat:tomcat-servlet-api")
...
```

### Remove Sites Folder Copy Tasks

Remove site tasks from `build.gradle` files, as in the following example.
The content of the `sites` folder will be prepared as a DBPrepare step. The required `SiteContentPerparer` will be added for these subprojects where such a folder exists.

```
/*
 * Create a copy of the smb whitestore content for the simple smb storefront
 */
task copySimpleSMBWhiteStore(type: Copy) {
    from "$projectDir/staticfiles/share/sites/inSPIRED-inTRONICS_Business-Site/units/inSPIRED-inTRONICS_Business-smb-responsive/impex/src/whitestore"
    into "$projectDir/staticfiles/share/sites/inSPIRED-inTRONICS-Site/units/inSPIRED-inTRONICS-smb-responsive/impex/src/whitestore"
}
zipShare.dependsOn copySimpleSMBWhiteStore
```

### Verify and Correct Dependencies
Starting with ICM 11, dependencies must be declared at the cartridge level. This applies to implementation and runtime dependencies alike.
In version 7.10, runtime dependencies were not utilized. Instead, the server required a cartridge list whose sequence indirectly reflected these runtime dependencies.
This approach had inherent limitations, and the dependencies were not always accurate at the cartridge level.

The cartridge list in the `build.gradle` file of the 7.10 assembly project defined the runtime dependencies indirectly and at the wrong level.
It is no longer relevant and has already been deleted.
The backup of the generated cartridge list is important in this step since it is a helpful tool for verifying and correcting dependencies between cartridges.

Each cartridge must be examined meticulously to determine its dependencies on other cartridges, and these dependencies must be documented in the respective `build.gradle.kts` file.
This applies to all source code artifacts, including component files, ISML templates, Java classes, property files that declare DBPrepare steps, and more.
In summary, a dependency on another cartridge must be declared when additional code or output from that cartridge is necessary.