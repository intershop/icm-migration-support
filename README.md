How to Work with the ICM Migration Support
==========================================

[TOC]:

# Table of Contents
- [Prerequisites](#prerequisites)
- [Migration](#migration)

# Marker

- $ICM is a symbolic marker for the root directory of your ICM 7.10 project
- $ICM_11 is a symbolic marker for the root directory of your ICM 11+ project (template)

# Prerequisites
* This tool is based on Java 21. An appropriate JDK must be installed and configured in your environment.
* The ICM project to be migrated must be managed by Git repository, checked out and deployed locally.

# Preparation
Perform a backup of the cartridge list of your deployed ICM 7.10 project.
Since the cartridge list in ICM 11+ is generated based on the declared dependencies, the current list becomes later 
important to compare the cartridge list of the ICM 7.10 project with the generated one.

## Prepare ICM11+ template
Retrieve customization template and follow the prerequisites steps.

Use customization template to create initial project structure

* checkout customization template
* follow the documentation to configure the template 
  * use versions for ICM 11 in the first step
* execute the initialization script

As result following files are created
- build.gradle.kts - root build script to configure subprojects (cartridges)
-- define and apply gradle repositories (allows to download ICM cartridges)
-- apply version filter to subproject (allows central definition of versions at two subprojects "versions" and "versions_test")
- my_* directories containing example cartridges for different purposes
- ft_production directory defines the cartridge set of production
- ft_test directory defines the test cartridge set for server tests (mostly test data)

## Verify ICM11+ template

- go to your ICM11+ project
- check that result of customization template is working
- set marker

```
gradlew compileTestJava
export ICM_11="$PWD"
```

## Prepare ICM11+ branch

- create and checkout a feature branch on $ICM
- copy result of customization template into $ICM (without overwriting the .git folder)

```
export ICM="$PWD"
git checkout -b feature/migration-to-11
rsync -av --exclude='.git' "$(ICM_11)/" "$(ICM)/"
```

# Migration

As stated in the chapter [Preparation](#preparation), the migration tool tries to commit the made changes after each step.
By this fine granular commit approach, it is possible to revert the changes step by step.
Currently, it is not possible to use that option with a subproject because the `.git` folder is not available in the subproject level.
To disable the auto commit, the `-PnoAutoCommit` parameter must be set.

## Migration all at once

This command will execute all migration steps on all subprojects of the $ICM directory
```
gradlew migration:migrateAll -Ptarget=$ICM -Psteps=src/main/resources/migration/001_migration_7.10-11.0.8
```

## Migration step by step

- run migration script (TODO build a gradle task)

```
gradlew migration:migrateOne -Ptask=project -Ptarget=$ICM/your_cartridge -Psteps=src/main/resources/migration/001_migration_7.10-11.0.8/001_MoveArtifacts.yml -PnoAutoCommit
gradlew migration:migrateOne -Ptask=projects -Ptarget=$ICM -Psteps=src/main/resources/migration/001_migration_7.10-11.0.8/001_MoveArtifacts.yml
```

## Manual Migration steps

### Global defined dependencies

- add central defined libs to subprojects section, as in 7.10 or better at the dependencies to the subprojects as needed.

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
### Sites Folder Copy Tasks

Remove site tasks from build.gradle files, like the following example.
The content of sites folder will be prepared as dbprepare step.

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

Add the migration task to the `dbinit.properties` or desired `migration-to-xxx.properties` 
```
# Prepare sites-folder
pre.Class0=com.intershop.site.dbinit.SiteContentPreparer
```

### Remove assembly projects / Verify and correct dependencies
In ICM11+, dependencies must be declared at the cartridge level. This applies to both implementation and runtime dependencies.
In version 7.10, runtime dependencies were not utilized. The server necessitated a cartridge list, the sequence of which reflected these runtime dependencies indirectly.
The previous approach exhibited inherent limitations, and the dependencies were not always accurate at the cartridge level.

The cartridge list, built in the `build.gradle` file of the assembly project, defined the runtime dependencies only indirectly and on
the wrong level. 
It is not of interest anymore and was already deleted. 
The backup of the generated cartridge list becomes in
this step important since it is a helpful tool to verify and correct the dependencies between the cartridges.

It is imperative that each cartridge be meticulously examined to ascertain its dependencies on other cartridges, and that these dependencies be documented in its respective `build.gradle` file.
Therefore, it is imperative to meticulously examine the code of each code artifact to ascertain its dependencies on other cartridges.
This assertion holds true for the complete array of code artifacts, including component files, ISML templates, Java classes, and the like.
In summary, when an additional code or output from another cartridge is necessary, a dependency on that cartridge must be declared.
