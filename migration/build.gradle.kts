plugins {
    java
}

description = "Migration Project"

tasks.test {
    useJUnitPlatform {
        includeEngines("junit-jupiter")
    }
}

dependencies {
    implementation("io.github.classgraph:classgraph")
    implementation("org.slf4j:slf4j-api")
    implementation("org.yaml:snakeyaml")
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.7.0.202309050840-r")
    runtimeOnly("ch.qos.logback:logback-classic")

    testImplementation("org.junit.jupiter:junit-jupiter") {
        exclude(group = "junit", module = "junit")
    }
    testImplementation("org.junit.platform:junit-platform-runner") {
        exclude(group = "junit", module = "junit")
    }
}

tasks.register<JavaExec>("migrateAll") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.intershop.customization.migration.Migrator")

    val autoCommit = project.hasProperty("autoCommit")

    args = mutableListOf<String>().apply {
        add("projects")
        project.findProperty("target")?.let { add(it.toString()) }
        project.findProperty("steps")?.let { add(it.toString()) }
        if (autoCommit) add("--autoCommit")
    }
}

tasks.register<JavaExec>("migrateOne") {
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.intershop.customization.migration.Migrator")

    args = listOfNotNull(
        project.findProperty("task") as? String,
        project.findProperty("target") as? String,
        project.findProperty("steps") as? String
    )
}