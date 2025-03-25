How to Work with the ICM Migration Helper
=========================================

[TOC]: #

# Table of Contents
- [Prerequisites](#prerequisites)
- [Migration](#migration)

# Marker #

- $ICM is a sympolic marker for the root directory of your ICM 7.10 project
- $ICM_11 is a sympolic marker for the root directory of your ICM 11+ project (template)

# Prerequisites

Retrieve customization template and follow the prerequisites steps.

Use customization template to create initial project structure

* checkout customization template
* follow the documentation to configure the template
* execute the initialization script

As result following files are created
- build.gradle.kts - root build script to configure sub projects (cartridges)
-- define and apply gradle repositories (allows to download ICM cartridges)
-- apply version filter to sub project (allows central definition of versions at two sub projects "versions" and "versions_test"
- my_* directories containing example cartridges for different purposes
- ft_production directory defines the cartridge set of production
- ft_test directory defines the test cartridge set for server tests (mostly test data)

## Prepare ICM11+ template

- go to your ICM11+ project
- check that result of customization template is working
- set marker

```
gw compileTestJava
export ICM_11="$PWD"
```

## Prepare ICM11+ branch

- create and checkout a feature branch on $ICM
- copy result of customization template into $ICM

```
export ICM="$PWD"
git checkout -b feature/migration-to-11
cp $(ICM_11)/* $(ICM)/
```

# Migration #

## Migration all at once ##

This command will execute all migration steps on all subprojects of the $ICM directory
```
gradlew migration:migrateAll -Ptarget=$ICM -Psteps=src/main/resources/migration/001_migration_7.10-11.0.8
```

## Migration step by step ##

- run migration script (TODO build a gradle task)

```
gradlew migration:migrateOne -Ptask=project -Ptarget=$ICM/your_cartridge -Psteps=src/main/resources/migration/001_migration_7.10-11.0.8/001_MoveArtifacts.yml
gradlew migration:migrateOne -Ptask=projects -Ptarget=$ICM -Psteps=src/main/resources/migration/001_migration_7.10-11.0.8/001_MoveArtifacts.yml
```

## Manual Migration steps ##

### Global defined dependencies ###

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
### Sites Folder Copy Tasks ###

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

Add the migration task to the dbinit.properties or desired migration-to-xxx.properties 
```
# Prepare sites-folder
pre.Class0=com.intershop.site.dbinit.SiteContentPreparer
```

### Remove assembly projects ###

Assembly projects are no longer needed. Just remove it.

### Remove .version files ###

Version numbers are declared inside the two subprojects "versions", "versions_test" for third party libraries.
The ICM version and required customization/extension versions are managed in gradle.properties
If a *.version file contains version declaration of specific dependencies, these must be transferred to versions projects.
