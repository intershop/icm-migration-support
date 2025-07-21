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
   - [Wiring Files Using the Configuration Framework](#wiring-files-using-the-configuration-framework)
   - [Adapt Logback Configuration](#adapt-logback-configuration)
   - [Check Remaining Static Files](#check-remaining-static-files)
   - [Verify and Correct Dependencies](#verify-and-correct-dependencies)

## Preparation Steps

> **Note:** The following markers are used in the commands below:
> - `$ICM`: A symbolic marker for the root directory of your ICM 7.10 project
> - `$ICM_11`: A symbolic marker for the root directory of the ICM 11+ project template

### Prepare ICM 11 Template
Retrieve the ICM 11 customization template and follow the prerequisite steps.

Use the customization template to create the initial project structure.

* Checkout the customization template.
* Follow the documentation to configure the template.
  * Use versions for ICM 11 in the first step.
* Execute the initialization script.

As a result, the following files are created:
- `build.gradle.kts` - root build script to configure subprojects (cartridges)
  - Define and apply gradle repositories (allows to download ICM cartridges)
  - Apply version filter to subproject (allows central definition of versions at two subprojects `versions` and `versions_test`)
- `my_*` directories containing example cartridges for different purposes
- `ft_production` directory defines the cartridge set of production
- `ft_test` directory defines the test cartridge set for server tests (mostly test data)

### Verify ICM 11 Template
To ensure the following migration bases on a working template, verify the following:
1. Switch to your ICM 11+ project.
1. Check that result of customization template is working.
1. Run the following comment to set the marker:

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
- `staticfiles/share/system/config/cluster/domainsplittings.xml` -> `src/main/resources/resources/{cartridgeName}/config/domainsplittings.xml`
- `staticfiles/share/system/config/cluster/urlrewriterules.xml` -> `src/main/resources/resources/{cartridgeName}/config/urlrewriterules.xml`
- `staticfiles/share/system/config/cluster/configuration.xml` -> `src/main/resources/resources/{cartridgeName}/config/configuration.xml`
- `staticfiles/share/system/config/cluster/replication.xml` -> `src/main/resources/resources/{cartridgeName}/replication/replication.xml`

### Move Java Source Code

Migrator: `MoveFilteredFolder`

Moves Java source code and pipelet XML files to their appropriate locations in the ICM 11 structure:

- `javasource` -> `src/main/java` (Java source files)
- `javasource` -> `src/main/resources` (only XML files matching pattern `^.*\\pipelet\\.*\.xml$`)

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
- `com.intershop.common:encryption` -> `com.intershop:encryption`
- `javax.ws.rs:javax.ws.rs-api` -> `jakarta.ws.rs:jakarta.ws.rs-api`

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
- `com.intershop.sellside.rest.common.patch.PATCH` -> `jakarta.ws.rs.PATCH`
- `javax.ws.rs` -> `jakarta.ws.rs`
- `javax.mail` -> `jakarta.mail`
- `javax.xml.bind` -> `jakarta.xml.bind`
- `javax.validation` -> `jakarta.validation`
- `javax.servlet` -> `jakarta.servlet`
- `javax.annotation` -> `jakarta.annotation`

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

### Wiring Files Using the Configuration Framework

Files in `cluster` and `domains` directories need to be wired using the configuration framework. 
Although the automated migration step moved these files to their new locations, manual configuration is still required.

1. Cluster-specific and domain-specific configuration (`/src/main/resources/resources/{cartridgeName}/config/cluster`,
   `/src/main/resources/resources/{cartridgeName}/config/domains/{domainName}`): Requires wiring in `configuration.xml`

2. Cartridge-specific and app-type-specific configuration (`/src/main/resources/resources/{cartridgeName}/config`): No wiring in
   `configuration.xml`

Verify that the configuration is correctly loaded at runtime by checking the server logs during startup.
For more details about the configuration framework, refer to [Concept - Configuration](https://support.intershop.com/kb/index.php/Display/301L43) in the Intershop Knowledge Base.

### Adapt Logback Configuration

In ICM 11, adjusting the Logback configuration is necessary to prevent issues when multiple application servers attempt to write to the same log file:

1. Remove file appenders from your logback configuration files:
   - Locate your logback configuration files in `src/main/resources/resources/{cartridgeName}/logback/`.
   - Remove any `<appender>` configurations that write to files.
   - For example, remove configurations such as:
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

   - Make sure the root logger (or specific loggers) use the console appender:
       ```xml
       <root level="INFO">
           <appender-ref ref="STDOUT" />
       </root>
       ```

In cloud environments, logs are typically collected and aggregated by external systems rather than stored in local files, making these changes essential for proper log management and centralized monitoring.

### Check Remaining Static Files

After the automated migration, verify whether any static files were not migrated:

1. The migration tool should have reported any unmapped directories in the `staticfiles` folder.
1. Certain directories are exceptions and should remain in their original locations:
   - `staticfiles/cartridge/configdef`
   - `staticfiles/cartridge/generationTemplates`
   - `staticfiles/cartridge/lib`
   - `staticfiles/cartridge/rules`
   - `staticfiles/cartridge/static`
   - `staticfiles/cartridge/definition`
   - `staticfiles/cartridge/wsdl`
   - `staticfiles/cartridge/urlrewrite`
1. Check your project for any remaining `staticfiles` directories that were not migrated but should have been moved.
1. For each remaining directory or file:
   - Determine the appropriate new location based on the file type and purpose
   - Manually move the file to its correct location in the ICM 11 structure
   - Update any references to these files in your code if needed

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

Tge migtation step "Ecamine Cartidge Dependencies" verifies the dependencies in the buiod.gradle.hts files, eiter for one or for all cartridges.

From the migration ool directory run
```
$ICM/gradlew migration:migrateAll \
-PnoAutoCommit \
-Ptask=project \
-Ptarget=$ICM \
-Psteps=src/main/resources/migration/001_migration_7x10_to_11/911_ExamineCartridgeDependencies.yml \
&& cat $TEMP/cartridgeAssignmentResults.txt

& cat /c/Users/hmordt/AppData/Local/Temp/

```
whereby
`$ICM` pounts to the prodect code directory ontraining all cartridges,
migrateAll means all, migrateOne a single cartridge to be analyzed.

The followinf steps are done
 1. analyze the cartridgedeoendencies by scanning the `build.gradle.kts` files,
 2. check for curcular references in there, when running migrateAll  across all caridges,
 3. amalyze the top level cartridges of the applications by scanning the `src/main/resources/resources/comonnts/app*,omponent` - this concerns the application definitions and extendions
 4. Check for marker carteidges, supposed to be in a certain application, but also in te dependencies  elsewhere, the result is stored ion `$MP/cartridgeAssignmentResults.txt`.

The application an der top level cartridges and the maarker cartridges or each application are defined in the files
```
migration/src/main/resources/cartridgedependencies/apps_top_level_cartridges.properties
migration/src/main/resources/cartridgedependencies/appmarker-cartridges.properties
```

In t11_ExamineCartridgeDependencies.yml`  the output format of the dependencies and e output file can be configured.
```
ns:
  treeFormat: "TEXT"or "JWON"
  treeOutputFile: ""
```
In case of "TEST" the output looks like
```
    as_smc_soennecken
        bc_organization_customproject
        ...
        bc_platform_rest_customproject
            com.intershop.platform:app
            com.intershop.platform:bc_application
            ...
            jakarta.xml.bind:jakarta.xml.bind-api
            com.google.inject:guice
            org.slf4j:slf4j-api
            jakarta.ws.rs:jakarta.ws.rs-api
            io.swagger.core.v3:swagger-annotations-jakarta
            jakarta.inject:jakarta.inject-api:1.0.3
        bc_user_orm_soennecken
        ...
```
In case of JSON for each cartridge a cartridge is described more detiled, A migrateOne sqnple gives the output

```
M/gradlew migration:migrateOne \
-PnoAutoCommit \
-Ptask=project \
-Ptarget=$ICM/bc_platform_rest_customproject
-Psteps=src/main/resources/migration/001_migration_7x10_to_11/911_ExamineCartridgeDependencies.yml
...
{
  "root": {
    "value": {
      "name": "customproject"
      "dependencyType": "ROOT"
    },
    "children": [
      {
        "value": {
          "name": "bc_platform_rest_customproject"
          "dependencyType": "CARTRIDGE"
        },
        "children": [
          {
            "value": {
              "name": "com.intershop.platform:app",
              "artifactName": "build.gradle.kts",
              "dependencyType": "UNKNOWN"
            },
            "children": []
          },
          ...
```

A `ependencyType` may be
 - ROOT - Represents the root entry in the dependency tree,
  - CARTRIDGE - Represents a cartridge dependency,
 - ARTIFACT - Represents an artifact dependency, almost jar files,
 - OMPONENT - Represents a component dependency, used to resolve the dependencies declared by the comonant,
 - framewor,
 - LIBRARY - Represents a library dependency,
 - PACKAGE - Represents a package dependency,
 - UNKNOWN - Represents an unknown dependency type.

