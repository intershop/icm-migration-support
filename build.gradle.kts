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
}

description = "Migration support for Intershop project updates"
group = "com.intershop.tooling.migration"
version = "1.0.0-SNAPSHOT"

// used for repo access
val adoOrganizationName: String by project
val adoProjectName: String by project

// used for publishing
val repoUser: String by project
val repoPassword: String by project

repositories {
    mavenCentral()

    // can be removed for a customization configuration
    maven {
        name = "ICM-AS"
        url = uri("https://pkgs.dev.azure.com/${adoOrganizationName}/${adoProjectName}/_packaging/icm-as-releases/maven/v1")
        credentials {
            username = repoUser
            password = repoPassword
        }
        mavenContent {
            releasesOnly()
        }
    }
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

