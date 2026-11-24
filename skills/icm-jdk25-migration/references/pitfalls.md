# Pitfalls: signatures, false greens, environmental failures

## Error signatures

Match the message, then read the cause - several look alike but have opposite fixes.

| message | where | cause | fix |
|---|---|---|---|
| `Unsupported class file major version 69` | a bytecode tool (JaCoCo agent, ASM) | tool too old for Java 25 | update the tool (table below) |
| `class file has wrong version 69.0, should be 65.0` | `compileJava` | project still on the 21 toolchain, upstream already on 25 | switch the toolchain (SKILL step 1) |
| `UnsupportedClassVersionError: ... class file version 69.0 ... up to 65.0` | server start, ISH unit, Geb, REST | Java 25 classes run on a JDK 21 image | order: upstream image not yet on 25 - not a defect of this project |
| `MalformedURLException: Unsupported non-local file URL` | `openConnection()` | `file://host/...` URL | R1 |
| `IOException: Stream closed`, `zip file closed`, changing per start | parallel resource loading | shared `JarFile` closed | R2 (often caused by R1) |
| `Liveness probe failed`, `curl: not found`, restart after ~30 s | Kubernetes | base image without `curl` | R5 |
| `WARNING: sun.misc.Unsafe::staticFieldBase ... terminally deprecated` | startup log | Guice bytecode generation | R7 |
| `[Guice/AopDisabled]` | injector creation | `bindInterceptor` with bytecode generation disabled | R7 |
| `Cannot find a Java installation ... languageVersion=25` | Gradle, CI | `installations.fromEnv` still lists `JAVA_HOME_21_X64` | SKILL step 3 |
| assertion `java.version ... startsWith("25")` fails in `buildSrc` | Gradle, local | daemon runs on JDK 21 | see "Local environment" |

## Bytecode tools

Anything that instruments or analyzes class files has to support version 69. Check every one the project
pins itself - a version managed by the ICM platform is already fine.

| tool | observed | minimum for Java 25 |
|---|---|---|
| JaCoCo (`jacoco { toolVersion = ... }`) | 0.8.12 failed in all four standard customizations | 0.8.14 (first with official Java 25 support) |
| ASM, Byte Buddy (Mockito), cglib, AspectJ, ArchUnit, SpotBugs, PMD, Error Prone, Lombok | not hit in ICM | check the tool's release notes |

```shell
git grep -n -E "toolVersion|jacoco|byte-?buddy|org\.ow2\.asm|aspectj|archunit|spotbugs|lombok"
```

JaCoCo is the easy one to miss: the failure is in `jacocoTestReport` / the test JVM's agent, after all
tests passed, and a project without unit tests (`icm-as-headless`) never instruments anything and stays
green.

## False greens

**The server started, so it works.** Read the startup log, not the probe status. Two icm-as defects (R2's
`Stream closed`, a missing pipelet) were ERROR lines in an otherwise healthy start; the probe defect (R5)
made a working server look crashed. Grep the log for the signatures above and compare the ERROR count
with a start of the previous version.

**One clean start.** R2 is a race. Start the server at least twice before calling it fixed.

**`ignoreFailures = true`, stale JUnit XML, data-dependent Geb specs** - see
`../../icm-gradle9-migration/references/pitfalls.md`; they apply unchanged.

## Environmental failures

**Shared `GRADLE_USER_HOME` with `org.gradle.java.home` on 21.** The setting overrides `JAVA_HOME`, so
the daemon stays on 21 and `buildSrc` (or the toolchain) fails. When the same `GRADLE_USER_HOME` serves
branches on 21 and 25, pass the JDK per invocation instead of changing the file:

```shell
gradlew -Dorg.gradle.java.home=/path/to/jdk-25 <tasks>
```

**`dbPrepare` against a reused database.** Running a JDK 25 `dbPrepare --clean-db=yes` on a database
another branch had prepared failed with `OCAException: Object is not valid` in the first preparer and
hundreds of follow-up failures. On a fresh database it passed completely. Recreate the database
container before judging a `dbPrepare` failure.

**Duplicate cartridge jars.** Builds without `localVersion=true` can leave two jars of each cartridge in
`build/libs` (`<name>-<version>.jar` and `<name>-local-<version>.jar`); `dbPrepare` then warns
`Found multiple jar files` and runs with a mixed classpath. Use the same `localVersion` setting for all
builds of a workspace, or clean the cartridges.

**`--offline`.** A fresh branch may need plugin or library versions that are not in the cache yet; the
offline build then fails on dependency resolution, which looks like a JDK problem. Build online after
switching branches.
