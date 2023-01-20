How to Work with the ICM Migration Helper
=========================================

[TOC]: #

# Table of Contents
- [Prerequisites](#prerequisites)
- [Migration](#migration)

## Prerequisites

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

# Migration #

- $ICM is a sympolic marker for the root directory of your ICM 7.10 project
- check that result of customization template is working

```
gw compileTestJava
export ICM_11_TEMPLATE="$PWD"
```

- create and checkout a feature branch on $ICM
- copy result of customization template into $ICM

```
export ICM="$PWD"
git checkout -b feature/migration-to-11
cp $(ICM_11_TEMPLATE)/* $(ICM)/
```

## Migration via search and replace ##

- run migration script (TODO build a gradle task)

```
java com.intershop.customization.migration.Migrator projects $ICM_710 migration/src/main/resources/migration/001_migration_7.10-11.0.8
java com.intershop.customization.migration.Migrator project $ICM_710/my_project my_migration_steps/buildGradle/001_cartridge
gradlew migrate -Ptarget=$ICM_710 -Psteps=migration/src/main/resources/migration/001_migration_7.10-11.0.8
gradlew migrate -Ptarget=$ICM_710 -Pstep=my_migration_steps/buildGradle/001_cartridge
```

- add central defined libs to sub projects section, as in 7.10

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
- TODO remove site tasks

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

- TODO clear assembly projects

```
plugins {
    id 'java'
}

dependencies {
}
```

- try to compile

```
gradlew compileTestJava
```
