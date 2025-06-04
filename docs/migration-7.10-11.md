# ICM 7.10 to ICM 11 Migration Steps

This document outlines the migration process from ICM 7.10 to ICM 11, including automated steps performed by the
migration tool and manual steps required afterward.

## Table of Contents

- [Preparation Steps](#preparation-steps)
    - [Prepare ICM11 template](#prepare-icm11-template)
    - [Verify ICM11 template](#verify-icm11-template)
    - [Prepare ICM11 branch](#prepare-icm11-branch)
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
    - [Global defined dependencies](#global-defined-dependencies)
    - [Remove Sites Folder Copy Tasks](#remove-sites-folder-copy-tasks)
    - [Wiring Files using the Configuration Framework](#wiring-files-using-the-configuration-framework)
    - [Adapt Logback Configuration](#adapt-logback-configuration)
    - [Check remaining staticfiles](#check-remaining-staticfiles)
    - [Verify and correct dependencies](#verify-and-correct-dependencies)

## Preparation Steps

> **Note:** The following marker are used in the commands below:
> - `$ICM` is a symbolic marker for the root directory of your ICM 7.10 project
> - `$ICM_11` is a symbolic marker for the root directory of the ICM 11+ project template

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

The automated steps for migrating from 7.10 to 11 are defined in the path: `src/main/resources/migration/001_migration_7x10_to_11`. 
Given this, the following parameters result for the Gradle task:

- **task**: Specifies the type of migration (`project` for a single project or `projects` for all projects). This parameter is only required for the `migrateOne` task.
- **target**: The root directory of your ICM 7.10 project (`$ICM`, if defined as above).
- **steps**: The path to the definitions for the automated migration steps (here: `src/main/resources/migration/001_migration_7x10_to_11`).

Example command:
```
gradlew migration:migrateAll -Ptarget=$ICM -Psteps=src/main/resources/migration/001_migration_7x10_to_11
```

> **Note:** `{cartridgeName}` is a placeholder and will be replaced by the name of the cartridge being migrated.

### Remove Assembly Projects

Migrator: `RemoveAssembly`

- Assembly projects handled cartridge lists for deployment in ICM 7.10
- Removes assembly projects which are no longer needed in ICM 11

### Move Folder Structure

Migrator: `MoveFolder`

Moves all staticfiles to their new locations in the ICM 11 structure:

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

Moves specific files to their new locations in the ICM 11 structure:

- `staticfiles/cartridge/directCustomAttributes.xml` -> `src/main/resources/resources/{cartridgeName}/directCustomAttributes.xml`

### Move Java Source Code

Migrator: `MoveFolder`

Moves Java source code and pipelet XML files to their appropriate locations in the ICM 11 structure:

- `javasource` -> `src/main/java` (Java source files)
- `javasource` -> `src/main/resources` (only XML files matching pattern `^.*/pipelet/.*\.xml$`)

This step ensures that:

- All Java source files are properly located in the standard Gradle Java source directory
- Pipelet XML files are moved to the resources directory while maintaining their relative path structure

### Convert build.gradle Files

Migrator: `ConvertBuildGradle`

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

### Create Environment Example Files

Migrator: `CreateEnvironmentExampleFiles`

Creates or replaces the following example files in the project root, with content specific for this environment:

- `environment.bat.example`:
    - Created from `migration/src/main/resources/environment/environment.bat.example.template`
    - Placeholder `<rootProject.name in settings.gradle.kts>` will be replaced by value of `rootProject.name` in `settings.gradle.kts`, e.g. "prjzz-icm"
    - Placeholder `<ishprjxxacr>` will be replaced by value of `dockerRegistry` in `gradle.properties`, e.g. "ishprjzzacr.azurecr.io"
- `icm.properties.example`:
    - Created from `migration/src/main/resources/environment/icm.properties.example.template`
    - Placeholder `<rootProject.name in settings.gradle.kts>` will be replaced by value of `rootProject.name` in `settings.gradle.kts`, e.g. "prjzz-icm"
 - `clean.bat`:
    - Created from `migration/src/main/resources/environment/clean.bat.template`
    - `{cartridgeName}` will be replaced by one line per cartridge
    - `{cartridgeName.last}` will be replaced by the last cartridge in the list
    - `{cartridgeName}` will be replaced by one line per cartridge, except for the last cartridge in the list

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

### Wiring Files using the Configuration Framework

Files in `cluster` and `domains` directories need to be wired using the Configuration Framework. The automated
migration step moved these files to their new locations, but manual configuration is required:

1. Cluster- and Domain-specific config (`/src/main/resources/resources/{cartridgeName}/config/cluster`,
   `/src/main/resources/resources/{cartridgeName}/config/domains/{domainName}`): Requires wiring in `configuration.xml`

2. Cartridge- (app-type-)specific config (`/src/main/resources/resources/{cartridgeName}/config`): No wiring in
   `configuration.xml`

Verify that the configuration is correctly loaded at runtime by checking the server logs during startup.
For more details about the configuration framework, refer to the [Concept - Configuration](https://support.intershop.com/kb/index.php/Display/301L43)
Guide in the ICM documentation.

### Adapt Logback Configuration

In ICM 11, especially in cloud environments, the logback configuration needs to be adjusted to prevent issues with
multiple application servers trying to write to the same log file.

1. Remove file appenders from your logback configuration files:
   - Locate your logback configuration files in `src/main/resources/resources/{cartridgeName}/logback/`
   - Remove any `<appender>` configurations that write to files
   - For example, remove configurations like:
       ```xml
       <appender name="DEBUG_LOG" class="ch.qos.logback.core.rolling.RollingFileAppender">
           <file><@loggingDir@>/debug.log</file>
           <encoder>
               <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
           </encoder>
           <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
               <FileNamePattern><@loggingDir@>/debug-%d{yyyy-MM-dd}.log.zip</FileNamePattern>
           </rollingPolicy>
       </appender>
       ```

2. Use console logging instead:
   - Ensure you have a console appender configured:
       ```xml
       <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
           <encoder>
               <pattern>%d{"yyyy-MM-dd'T'HH:mm:ss.SSSXXX", UTC} [%thread] %-5level %logger{36} %msg%n</pattern>
           </encoder>
       </appender>
       ```

   - Make sure the root logger or specific loggers use the console appender:
       ```xml
       <root level="INFO">
           <appender-ref ref="STDOUT" />
       </root>
       ```

In cloud environments, logs are typically collected and aggregated by external systems rather than stored in local
files, making these changes essential for proper log management and centralized monitoring.

### Check remaining staticfiles

After the automated migration, you should verify if any staticfiles remain unmigrated:

1. The migration tool should have reported any unmapped directories in the staticfiles folder

2. There are exceptions for certain directories that are not moved and should remain in their original location:
   - `staticfiles/cartridge/configdef`
   - `staticfiles/cartridge/generationTemplates`
   - `staticfiles/cartridge/lib`
   - `staticfiles/cartridge/rules`
   - `staticfiles/cartridge/static`
   - `staticfiles/cartridge/definition`
   - `staticfiles/cartridge/wsdl`
   - `staticfiles/cartridge/urlrewrite`

3. Check your project for any remaining staticfiles directories that were not properly migrated and should be moved

4. For each remaining directory or file:
   - Determine the appropriate new location based on the file type and purpose
   - Manually move the file to its correct location in the ICM 11 structure
   - Update any references to these files in your code if needed

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