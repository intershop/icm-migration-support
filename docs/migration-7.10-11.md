# ICM 7.10 to ICM 11 Migration Steps

This document outlines the migration process from ICM 7.10 to ICM 11, including automated steps performed by the
migration tool and manual steps required afterward.

> **Note:** `{cartridgeName}` is a placeholder and will be replaced by the name of the cartridge being migrated.

## Table of Contents

- [Preparation Steps](#preparation-steps)
    - [Prepare ICM11 template](#prepare-icm11-template)
    - [Verify ICM11 template](#verify-icm11-template)
    - [Prepare ICM11 branch](#prepare-icm11-branch)
- [Automated Migration Steps](#automated-migration-steps)
    - [Remove Assembly Projects](#remove-assembly-projects)
    - [Move Folder Structure](#move-folder-structure)
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
- [Manual Migration Steps](#manual-migration-steps)
    - [Global defined dependencies](#global-defined-dependencies)
    - [Remove Sites Folder Copy Tasks](#remove-sites-folder-copy-tasks)
    - [Verify and correct dependencies](#verify-and-correct-dependencies)

## Preparation Steps

> **Note:** The following marker are used in the commands below:
> - `$ICM` is a symbolic marker for the root directory of your ICM 7.10 project
> - `$ICM_11` is a symbolic marker for the root directory of your ICM 11+ project (template)

### Prepare ICM11 template
Retrieve customization template for ICM 11 and follow the prerequisites steps.

Use customization template to create initial project structure

* checkout customization template
* follow the documentation to configure the template
  * use versions for ICM 11 in the first step
* execute the initialization script

As result following files are created
- `build.gradle.kts` - root build script to configure subprojects (cartridges)
  -- define and apply gradle repositories (allows to download ICM cartridges)
  -- apply version filter to subproject (allows central definition of versions at two subprojects `versions` and `versions_test`)
- `my_*` directories containing example cartridges for different purposes
- `ft_production` directory defines the cartridge set of production
- `ft_test` directory defines the test cartridge set for server tests (mostly test data)

### Verify ICM11 template
To ensure the following migration bases on a working template, verify the following:
- go to your ICM11+ project
- check that result of customization template is working
- set marker

```
gradlew compileTestJava
export ICM_11="$PWD"
```

### Prepare ICM11 branch

- create and checkout a feature branch on $ICM
- copy result of customization template into $ICM (without overwriting the .git folder)

```
export ICM="$PWD"
git checkout -b feature/migration-to-11
rsync -av --exclude='.git' "$ICM_11/" "$ICM/"
```

> **Note:** When command `rsync` is not available on your system you can copy the content using any available
> file copy command. Just make sure to **exclude** the `.git` folder.

## Automated Migration Steps

### Remove Assembly Projects

Migrator: `RemoveAssembly`

- Assembly projects handled cartridge lists for deployment in ICM 7.10
- Removes assembly projects which are no longer needed in ICM 11

### Move Folder Structure

Migrator: `MoveFolder`

Moves all staticfiles to their new locations in the ICM 11 structure:

- `staticfiles/cartridge/pipelines` -> `src/main/resources/resources/{cartridgeName}/pipelines`
- `staticfiles/cartridge/queries` -> `src/main/resources/resources/{cartridgeName}/queries`
- `staticfiles/cartridge/templates` -> `src/main/isml/{cartridgeName}`
- Various other resource directories moved to their new locations

### Convert build.gradle Files

Migrator: `UpdateGradleBuild7to10`

- Adapts plugins in build.gradle files to match ICM 11 requirements
- Updates build system configurations to new structure

### Convert to Cartridge Dependency

Migrator: `ConvertToCartridgeDependency`

- Refactors Intershop dependencies in build.gradle files
- Updates dependency groups:
    - `com.intershop.platform`
    - `com.intershop.content`
    - `com.intershop.business`
    - `com.intershop.b2b`

### Rename Dependencies

Migrator: `RenamedDependency`

Renames dependencies in build.gradle files:

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

- `*.version` files (version information now in central location)
- Root `build.gradle` and `settings.gradle` files (replaced by customization template)

## Manual Migration Steps

### Global defined dependencies

Add central defined libs to subprojects section, as in 7.10 or better at the dependencies to the subprojects as needed.

```
subprojects {
...
    plugins.withType<JavaPlugin> {

        dependencies {
            val cartridge by configurations
            val implementation by configurations
            val testImplementation by configurations
...
            // central defined libs
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

Remove site tasks from `build.gradle` files, like the following example.
The content of `sites` folder will be prepared as dbprepare step. The required `SiteContentPerparer` will be added for
these subprojects where such a folder exists.

```
/*
 * create a copy of the smb whitestore content for the simple smb storefront
 */
task copySimpleSMBWhiteStore(type: Copy) {
    from "$projectDir/staticfiles/share/sites/inSPIRED-inTRONICS_Business-Site/units/inSPIRED-inTRONICS_Business-smb-responsive/impex/src/whitestore"
    into "$projectDir/staticfiles/share/sites/inSPIRED-inTRONICS-Site/units/inSPIRED-inTRONICS-smb-responsive/impex/src/whitestore"
}
zipShare.dependsOn copySimpleSMBWhiteStore
```

### Verify and correct dependencies
Starting with ICM11, dependencies must be declared at the cartridge level. This applies to both implementation and runtime dependencies.
In version 7.10, runtime dependencies were not utilized. The server necessitated a cartridge list, the sequence of which reflected these runtime 
dependencies indirectly.
The previous approach exhibited inherent limitations, and the dependencies were not always accurate at the cartridge level.

The cartridge list, built in the `build.gradle` file of the 7.10 assembly project, defined the runtime dependencies only indirectly and on
the wrong level.
It is not of interest anymore and was already deleted.
The backup of the generated cartridge list becomes in this step important since it is a helpful tool to verify and correct the dependencies between 
the cartridges.

It is imperative that each cartridge be meticulously examined to ascertain its dependencies on other cartridges, and that these dependencies be 
documented in its respective `build.gradle.kts` file.
This holds true for all source code artifacts, including component files, ISML templates, Java classes, and so on., as well as property files that 
declare dbprepare steps.
In summary, when an additional code or output from another cartridge is necessary, a dependency on that cartridge must be declared.