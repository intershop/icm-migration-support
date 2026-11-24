---
name: icm-jdk25-migration
description: Migrate an Intershop ICM customization (and the ICM standard customizations) from JDK 21 to JDK 25 - toolchain, pipelines, bytecode tools, Docker images, start scripts and the JDK behavior changes that only show up at runtime. Use this skill whenever someone works on an ICM build or server and mentions JDK 25, Java 25, Temurin 25, ICM 15, `JavaLanguageVersion.of(25)`, `JAVA_HOME_25_X64`, "Unsupported class file major version 69", "class file has wrong version 69.0", `UnsupportedClassVersionError`, "Unsupported non-local file URL", "Stream closed" or "zip file closed" while loading resources, liveness probes failing with `curl: not found`, `sun.misc.Unsafe` warnings from Guice, `[Guice/AopDisabled]`, ECJ, Drools language level or JaCoCo failures after a JDK upgrade - even if they do not use the word "migration". It assumes a customization already on Gradle 9 and the ICM 11+ layout; for those use icm-gradle9-migration and icm-migration first.
---

# Migrating an ICM Customization to JDK 25

ICM 15 builds and runs on Java 25 (Eclipse Temurin 25). Customizations consume the ICM artifacts and are
built on the ICM image, so they have to follow - and they cannot stay on 21: a Java 21 compiler cannot
read the Java 25 class files of ICM.

This skill is the distilled result of doing it for `icm-as` and the four standard customizations
(`icm-solrcloud`, `icm-as-headless`, `icm-as-demo-data`, `icm-responsive`). A standard customization
needed **2 to 7 changed files**, almost all of them one-line version changes. What costs time is the
part that is *not* in the build files: JDK behavior changes that compile fine and break at runtime,
often behind a swallowed exception. Those are the real value here.

## When this applies

A project is affected if it builds against an ICM release whose `icm-as` uses a Java 25 toolchain, or if
its image is built `FROM` such an ICM image.

Prerequisites:

- Gradle 9.1 or newer - the first Gradle that supports Java 25 toolchains. A project still on Gradle 8
  needs `../icm-gradle9-migration/SKILL.md` first.
- A JDK 25 locally and on the build agents (Intershop Azure agents provide `JAVA_HOME_25_X64`).
  The agent container from `templates/container/` ships JDK 21 only; add a JDK 25 for this migration.
- **Every upstream project already published with JDK 25.** See "Order" below; this is the one
  prerequisite that cannot be worked around.

## How to choose the versions

Never hardcode or recall versions - read them from the ICM release the project builds against:

| what | where in `icm-as` |
|---|---|
| Java version | `build.gradle.kts`: `JavaLanguageVersion.of(N)`; `buildSrc/build.gradle.kts`: the `java.version` assertion |
| runtime JDK of the image | `docker/main/Dockerfile`: the `eclipse-temurin` base image |
| Gradle | `gradle/wrapper/gradle-wrapper.properties` |
| libraries (ECJ, Drools, Tomcat, Guice, Mockito, ...) | the published platforms `com.intershop.icm:versions` / `versions_test` |

For bytecode tools that a customization pins itself (JaCoCo, ...), the rule is: **highest release of the
tool that officially supports the target Java version** - see `references/pitfalls.md` for the minimum
versions observed. Do not pin libraries the ICM platform already manages; a pin overrides the platform
and is the usual cause of "works in icm-as, fails in the customization".

## Order

Java 21 cannot load Java 25 class files, in both directions that matter:

- a customization still on the 21 toolchain fails to compile against upstream artifacts built with 25
  (`class file has wrong version 69.0, should be 65.0`);
- a customization built with 25 but started on an image that still runs JDK 21 fails at runtime
  (`UnsupportedClassVersionError ... 69.0`).

So each project switches **right after** everything it consumes is published with JDK 25 - not earlier,
not much later. For the ICM chain:

```
icm-as  ->  icm-solrcloud, icm-as-headless  ->  icm-as-demo-data  ->  icm-responsive  ->  customer project
```

Read the real chain from the project's `gradle.properties` (`icmVersion`, `solrVersion`,
`headlessVersion`, `demoDataVersion`, ...) and the `image.set(...)` lines in `build.gradle.kts`.
Prepare the branches of all projects in advance (draft PRs), then merge them along the chain. While a
project's upstream is already on 25 and the project itself is not, its main branch is red - keep that
window short.

The compile and unit-test part of a branch *can* be verified before upstream switches: Java 25 reads
Java 21 class files. Server-based tests (ISH unit, Geb, REST) cannot, because their image still runs 21.
Run the build early anyway and separate the two kinds of failure - it found a real defect (JaCoCo) in
all four standard customizations a day before icm-as was merged.

## Workflow

Verify after each step.

### 1. Switch the toolchain

Replace `JavaLanguageVersion.of(21)` everywhere - the root `build.gradle.kts` usually has **two**
(the `java` toolchain and the `javadoc` launcher), and test subprojects (`*_rest_test`, `*_geb_test`)
often have their own. Search, do not assume:

```shell
git grep -n -E "JavaLanguageVersion\.of\(|languageVersion|jvmToolchain|release\.set|sourceCompatibility"
```

### 2. Update bytecode tools

Anything that reads class files must know version 69. **JaCoCo was pinned to 0.8.12 in all four standard
customizations** and failed in `jacocoTestReport`; see `references/pitfalls.md` for the full list of
suspects and the symptom.

### 3. Switch the pipelines

Two settings per pipeline, and **both** are needed - changing only `JAVA_VERSION` leaves Gradle looking
for the old toolchain:

- `JAVA_VERSION: '25'` (used by `JavaToolInstaller` with `jdkSourceOption: PreInstalled`)
- `-Dorg.gradle.java.installations.fromEnv=JAVA_HOME_25_X64` in the Gradle options (drop old
  entries such as `JAVA_HOME_17_X64`)

Check every pipeline file (`azure-pipeline*.yml`, `.github/workflows/*`), not just the main one -
replication-test and vulnerability pipelines carry their own `JAVA_VERSION`. Also: Qodana
`projectJDK`, IDE settings written by pipelines (`gradleJvm` in `.idea/gradle.xml`), README
prerequisites.

```shell
git grep -n -I -E "JAVA_HOME_(1[0-9]|2[0-4])_X64|JAVA_VERSION|java-version|projectJDK|temurin-2[0-4]|JDK ?2[0-4]"
```

### 4. Docker images and start scripts

An image built `FROM` the ICM image inherits JDK 25 and needs nothing. An image built `FROM
eclipse-temurin:25-*` directly - or any custom start script - needs the checks in
`references/runtime-changes.md` (R5 to R7): missing tools such as `curl`, removed JVM flags, Guice.

### 5. Review the code for JDK behavior changes

Compile errors are the easy part. Go through **`references/runtime-changes.md`** and grep for each
pattern - every item there compiles fine and fails at runtime, several of them silently.

### 6. Verify

Locally, with the daemon on JDK 25 (see pitfalls for the shared `GRADLE_USER_HOME`):

```shell
gradlew compileJava compileTestJava javadoc
gradlew test jacocoTestReport
gradlew dbPrepare        # against a fresh database, see pitfalls
gradlew testIshUnit
```

Then start the server the way production does (container / Helm) and read the startup log - not just
the probe status. Search it for the signatures in `references/pitfalls.md`; two of the icm-as defects
were visible *only* there.

## Commit

```
chore: Build and test with JDK 25 (#XXXXXX)

- Java 25 toolchain
- JAVA_VERSION 25 and JAVA_HOME_25_X64 in Azure pipelines
- updated tools:
  - toolName:oldVersion -> newVersion
```

Omit empty sections; one commit per concern (toolchain, tool updates, runtime fixes) keeps review easy.
