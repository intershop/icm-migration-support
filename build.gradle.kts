buildscript {
    repositories {
        gradlePluginPortal()
    }
}

plugins {
    // IDE plugin
    idea
    // Gradle base plugin
    base
    // java plugin - required for kotlin scripts
    java
}

description = "Migration support for Intershop project updates"
group = "com.intershop.tooling.migration"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

subprojects {
    group = rootProject.group
    version = rootProject.version

    repositories.addAll(rootProject.repositories)

    plugins.withType<JavaPlugin> {

        dependencies {
            val implementation by configurations
            val testImplementation by configurations

            implementation(platform(project(":versions")))
            testImplementation(platform(project(":versions_test")))
        }

        tasks.withType<JavaCompile> {
            //enable compilation in a separate daemon process
            options.isFork = true
        }
    }
}

java {
    withSourcesJar()
}

sourceSets {
    main {
        resources {
            srcDirs("src/main/resources")
        }
    }
}

