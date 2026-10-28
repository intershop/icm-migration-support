# Deprecation catalog

Every fix below is taken from the real ICM migration, not invented. Use it as a lookup: match the
symptom, apply the replacement.

Contents:

- [D1 - `Project` accessed at execution time](#d1---project-accessed-at-execution-time)
- [D2 - Kotlin DSL property delegates](#d2---kotlin-dsl-property-delegates)
- [D3 - a `Project` object as dependency-constraint notation](#d3---a-project-object-as-dependency-constraint-notation)
- [D4 - `ReportingExtension.file(String)`](#d4---reportingextensionfilestring)
- [D5 - custom task classes in `buildSrc`](#d5---custom-task-classes-in-buildsrc)
- [D6 - JUnit 5 tests that never ran now fail the build](#d6---junit-5-tests-that-never-ran-now-fail-the-build)
- [D7 - platform leaks via `versions` / `versions_test`](#d7---platform-leaks-via-versions--versions_test)
- [D8 - `project.javaexec` in an abandoned third-party plugin](#d8---projectjavaexec-in-an-abandoned-third-party-plugin)
- [D9 - generated sources and `cartridge-resourcelist`](#d9---generated-sources-and-cartridge-resourcelist)
- [D10 - a dependency on an unpublished project](#d10---a-dependency-on-an-unpublished-project)
- [Locating a deprecation](#locating-a-deprecation)

## D1 - `Project` accessed at execution time

> `Invocation of Task.project at execution time has been deprecated. This will fail with an error in Gradle 10.`

**All four standard customizations had this**, always in the same place: the `produceReleaseMessage`
task. Any `doLast` / `doFirst` / `onlyIf` body - or any helper it calls - that touches `project`,
`rootProject` or a version service is affected.

The underlying reason is the configuration cache: a task action must not reach back into the build model
at execution time, because that model no longer exists when the task runs from a cached graph. So the fix
is always the same shape - resolve everything into plain values or lazy providers while the build is
still configuring:

```kotlin
tasks {
    // the values are captured at configuration time - reading the project or the version service
    // inside the task action is deprecated in Gradle 9 and fails in Gradle 10
    val releaseInfoFile = layout.buildDirectory.file("release_info/releaseinfo.properties")
    val scmRevision = gitflowVersion.versionService.revID
    val scmBranch = gitflowVersion.branchName
    val projectGroup = project.group.toString()
    val projectId = project.name
    val projectVersion = project.version.toString()

    val produceReleaseMessage = register("produceReleaseMessage") {
        description = "Write properties for release processing"
        outputs.file(releaseInfoFile)

        doLast {
            releaseInfoFile.get().asFile.writeText(
                """
                SCM_REVISION=$scmRevision
                SCM_BRANCH=$scmBranch

                PROJECT_GROUP=$projectGroup
                PROJECT_ID=$projectId
                PROJECT_VERSION=$projectVersion
                """.trimIndent()
            )
        }
    }
}
```

Note that `outputs.files.singleFile` inside the action becomes `releaseInfoFile.get().asFile` - reading
the task's own outputs at execution time is the same anti-pattern in disguise.

Verify by diffing the generated `releaseinfo.properties` before and after. It must be byte identical,
including the trailing whitespace quirks of the original template - downstream release tooling parses
this file.

## D2 - Kotlin DSL property delegates

All of these are removed in Gradle 10:

| before | after |
|---|---|
| `val x: String by settings` | `val x: String = providers.gradleProperty("x").get()` |
| `val x: String by project` | `val x = project.property("x") as String` |
| `val implementation by configurations` + `implementation(…)` | `add("implementation", …)` |
| `val test by getting(JvmTestSuite::class)` | `getByName<JvmTestSuite>("test")` |
| `val myCfg by configurations.creating` | `configurations.create("myCfg")` |

`project.property(name) as String` is the exact 1:1 replacement for `by project` - same lookup set
including extra properties, same `MissingPropertyException` when the property is absent. `by settings`
has no such equivalent, which is why `providers.gradleProperty(..)` is used there instead; it is also
what the deprecation message itself recommends.

The `by configurations` delegates usually exist because the code sits inside an `allprojects { }` or
`subprojects { }` block, where the type-safe accessors (`implementation(..)`) are not generated.
`DependencyHandler.add(String, …)` solves that without any delegate:

```kotlin
allprojects {
    dependencies {
        add("implementation", platform(project(":versions")))
    }
}
```

## D3 - a `Project` object as dependency-constraint notation

```kotlin
val depFactory = project.dependencyFactory

depConstraints.add("api", subproject)                                          // deprecated
depConstraints.add("api", depFactory.createProjectDependency(subproject.path))  // correct
```

`dependencyFactory.create(project)` looks like the obvious fix but does **not** help - it routes the
`Project` through the same deprecated converter. Only the `String`-path overload avoids it.

This pattern appears in platform projects that add an `api` constraint for every cartridge subproject.
That code is load-bearing: customizations consume the platform and then declare cartridges *without* a
version, so removing the block instead of fixing it leaves them unresolvable. Verify a change here by
diffing the generated `build/publications/*/pom-default.xml` before and after - it must be identical.

## D4 - `ReportingExtension.file(String)`

```kotlin
html.outputLocation.set(project.reporting.baseDirectory.dir("restTest"))
```

The resolved path is unchanged (`build/reports/restTest`), but confirm it rather than assume it if
anything downstream collects reports from a fixed location.

## D5 - custom task classes in `buildSrc`

The rule is broader than the warnings suggest: **tasks must not use any `Project` object during
execution** - not a single `project.*` or `getProject()` inside `@TaskAction`, `doLast`, `doFirst`,
`onlyIf`, or any helper method they call.

Replace the project services with injected ones:

| instead of | inject |
|---|---|
| `project.exec` / `project.javaexec` | `ExecOperations` |
| `project.copy` / `project.delete` / `project.sync` | `FileSystemOperations` |
| `project.zipTree` / `project.tarTree` | `ArchiveOperations` |
| `project.layout` / `project.buildDir` | `ProjectLayout` |
| `project.objects` | `ObjectFactory` |
| `project.logger` | the task's own `logger` |
| `project.mkdir(…)` | `java.nio.file.Files.createDirectories(…)`, and declare `outputs.dir(…)` |
| `project.property(…)` at execution time | `providers.gradleProperty(…)`, resolved at configuration time |
| `project.tasks.named(…)` inside `onlyIf` | capture the `TaskProvider` at configuration time |

Two checks are needed, and neither alone is sufficient:

1. `gradlew validatePlugins` - **`assemble` does not run it.** Read the full problem list from
   `build/reports/plugin-development/validation-report.json`; the console truncates at 5 entries.
   Typical findings are a missing `@DisableCachingByDefault(because = …)` ("not cacheable without
   reason") and a missing `@PathSensitive(…)` on `@InputFile` / `@InputDirectory` properties ("missing
   normalization").
2. A grep for **any** `project.` / `getProject()` in task classes. An enumerated member list is not
   enough - in the real migration a `@TaskAction`-only scan found 8 files, extending it to
   `onlyIf`/`doLast`/`doFirst` found three more, and a plain `project.logger` grep found four further
   classes whose calls sat in helper methods. **Indirect** references are worse still: a field or lambda
   holding a type that itself holds a `Project` is invisible to both `--warning-mode fail` and
   `validatePlugins`.

Faithful-replacement detail worth knowing: old code doing `properties.get(NAME).toString()` yields the
string `"null"` when the property is absent (and `parseBoolean("null") == false`). The exact equivalent
is `providers.gradleProperty(NAME).orNull.toString()` - not `.getOrElse("")`.

## D6 - JUnit 5 tests that never ran now fail the build

> There are test sources present and no filters are applied, but the test task did not discover any
> tests to execute.

Gradle's default test framework is JUnit 4. A cartridge with Jupiter test classes but no
`useJUnitPlatform { … }` never executed those tests. That was silent before and is a build error now
(`failOnNoDiscoveredTests`, on by default since Gradle 8.13).

```kotlin
tasks.test {
    useJUnitPlatform {
        includeEngines("junit-jupiter")
        excludeEngines("junit-vintage")
    }
}

dependencies {
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
```

The launcher dependency is required because Gradle 9 no longer injects it.

Expect tests that now run for the first time to fail - fix them rather than suppressing the check; the
whole point is that they were never verifying anything. The exception is a task whose test classes
*structurally cannot* contain tests: ICM's `testIshUnit` points at the entire main source set, so
support and extension cartridges always look like they "have test sources". For that case the suppression
belongs centrally in the task type, not scattered across cartridge build files.

A cartridge can also work without the block if its tests inherit `@RunWith(JUnitPlatform.class)` from a
base class - so verify individually before adding it everywhere.

## D7 - platform leaks via `versions` / `versions_test`

Anything added to a **published** platform becomes a contract for everyone consuming it. A constraint is
only safe if the artifact does not drag a language runtime with it.

This bit twice during the ICM migration. First by adding the Groovy and Spock BOMs to `versions_test`;
then, after that was reverted, by a constraint on an Intershop test library that *itself* depends on
Groovy - which pinned the Groovy major indirectly. Both silently upgraded Groovy in customizations that
deliberately pin an older one for Spock and Geb, producing Spock built for Groovy 4 running on Groovy 5.

Probe for the whole class of problem:

```shell
gradlew -q :<a geb or rest test project>:dependencies --configuration testRuntimeClasspath
```

and look for `->` upgrades on the `groovy` / `spock` lines. Note that Geb source sets compile against
`testRuntimeClasspath` (that is how the ICM Geb plugin wires them), which is exactly where a test
platform lands - `gebTestCompileClasspath` will show nothing and mislead you.

Language and test-framework versions that a customization must control itself (Groovy, Spock, Geb,
Selenium) belong in the customization, not in a shared platform.

## D8 - `project.javaexec` in an abandoned third-party plugin

> `Could not find method javaexec() for arguments [...] on project ':x' of type org.gradle.api.Project.`

plus, under `--warning-mode all`, a D1-style
`Invocation of Task.project at execution time has been deprecated.` whose stack trace ends in the
plugin's own task class rather than in a build script.

D5 is about *your* task classes. This is the same rule hitting a plugin you do not own, and the
difference matters: there is nothing to fix inside it. The concrete case was
`org.hidetake.swagger.generator`, whose `GenerateSwaggerCode.exec()` calls `project.javaexec { … }` from
its `@TaskAction`. Its latest release is 2.19.2 from January 2022, so no upgrade path exists.

Before doing anything else, check whether a fixed release exists - read the plugin's
`maven-metadata.xml` (see SKILL.md) and look at `lastUpdated`. A plugin whose last publication predates
Gradle 9 by years will not be fixed.

**The fix is to inline what the plugin did.** A code generator plugin is almost always a thin wrapper
around `java -cp <generator> <MainClass> <args>`, which is a plain `JavaExec` task - a task type that is
fully lazy, cacheable and configuration-cache safe. Read the plugin's own source for the exact
invocation rather than guessing it from its DSL; for Swagger Codegen v3 the mapping was:

| plugin DSL | `JavaExec` |
|---|---|
| (implicit) | `mainClass = "io.swagger.codegen.v3.cli.SwaggerCodegen"` |
| `language`, `inputFile`, `outputDir` | `args("generate", "-l", …, "-i", …, "-o", …)` |
| `configFile`, `templateDir` | `args("-c", …)`, `args("-t", …)` - only when set |
| `components = mapOf("apis" to true, "apiTests" to false)` | system properties: `true` -> `""`, `false`/`null` -> `"false"` |
| `jvmArgs` | `jvmArgs(…)` |
| `wipeOutputDir = true` (the default) | delete + recreate in `doFirst` |
| the `swaggerCodegen` configuration the plugin created | `configurations.create("swaggerCodegen")` |

```kotlin
val swaggerCodegen: Configuration = configurations.create("swaggerCodegen")

val generateSwaggerCode = tasks.register<JavaExec>("generateSwaggerCode") {
    inputs.file(specFile).withPropertyName("inputFile").withPathSensitivity(PathSensitivity.NAME_ONLY)
    inputs.dir(templateDir).withPropertyName("templateDir").withPathSensitivity(PathSensitivity.NAME_ONLY)
    outputs.dir(outputDir).withPropertyName("outputDir")
    outputs.cacheIf { true }

    classpath = swaggerCodegen
    mainClass.set("io.swagger.codegen.v3.cli.SwaggerCodegen")
    jvmArgs("--add-opens=java.base/java.util=ALL-UNNAMED")
    systemProperties(mapOf("models" to "", "modelTests" to "false", "apis" to "", "supportingFiles" to ""))
    args("generate", "-l", "java", "-i", specFile.asFile.path, "-o", outputDir.get().asFile.path)

    // resolved while configuring, so the action never reaches into the project model (D1)
    val outputDirectory = outputDir.get().asFile
    doFirst {
        outputDirectory.deleteRecursively()
        outputDirectory.mkdirs()
    }
}
```

The inputs/outputs the plugin declared are worth copying too - they are what keeps the task
up-to-date-checkable, and `PathSensitivity.NAME_ONLY` is deliberate: the absolute path of a spec file
must not invalidate the task.

**Verify by diffing the generated output, not by reading the diff of the build script.** The old plugin
cannot run on the new wrapper and the new task cannot run on the old one, so produce the baseline first:

```shell
git stash push -- <the files you changed>     # back to the pre-migration build
gradlew :<project>:<old generate task>        # baseline, on the old wrapper
# copy the generated tree somewhere outside the build directory
git stash pop
gradlew :<project>:<new generate task>
# compare the file *set* and every file hash - both must be identical
```

Expect `wipeOutputDir` to have emptied the directory during a failed run, so there is usually no
baseline lying around to compare against - produce one explicitly.

## D9 - generated sources and `cartridge-resourcelist`

> `A problem was found with the configuration of task ':x:resourceListOrm' (type 'ResourceListFileTask').`
> `Property has implicit dependency` … `uses this output of task ':x:generateSwaggerCode' without
> declaring an explicit or implicit dependency.`

This is a **build error in Gradle 9** where Gradle 8 only warned, so it surfaces in projects that have
been building happily for years. It is a latent defect, not something the migration introduces.

`com.intershop.gradle.cartridge-resourcelist` registers `resourceListOrm` / `resourceListPipelets` like
this:

```kotlin
(mainSourceSet.resources.srcDirs + mainSourceSet.allSource.srcDirs)
    .filterNot { … }
    .forEach { srcDir -> task.sourceFiles.from(project.fileTree(srcDir)) }
```

`srcDirs` resolves to plain `File`s and each one is wrapped in a fresh `fileTree`. Any task dependency
that the source directory carried is discarded in that step. So whenever a cartridge adds a *generated*
directory to its main source set, the resource-list tasks read it without an edge to the generator.

The natural lazy idiom does **not** help:

```kotlin
sourceSets.main.configure {
    java.srcDir(generateSwaggerCode.map { generatedSourceDir.get() })   // not enough on its own
}
```

It is still worth doing - `compileJava`, `sourcesJar` and everything else that consumes the source set
lazily gets its dependency from it - but the resource-list tasks need the edge spelled out:

```kotlin
// the resourcelist plugin resolves the source directories into plain file trees, which drops the
// provider's task dependency - Gradle 9 turns that missing edge into a build error, so declare it
tasks.withType<com.intershop.gradle.resourcelist.task.ResourceListFileTask>().configureEach {
    dependsOn(generateSwaggerCode)
}
```

Generalise the symptom rather than the plugin: *any* consumer that resolves `srcDirs` eagerly needs the
same treatment, and the error message always names both tasks, so it tells you exactly which edge to add.

## D10 - a dependency on an unpublished project

> `Declaring a dependency on an unpublished project has been deprecated. This will fail with an error in
> Gradle 10. A dependency was declared on project ':versions', but that project does not declare any
> publications.`

**This fires only while publishing.** `assemble`, `testClasses` and `tasks --all` are all green with it
present - it needs `publishToMavenLocal` (or `publish`) to appear. It is therefore the sharpest example
of the rule from SKILL.md step 5, and in a customization it tends to fire *twice*, for two different
reasons.

### The platform itself is not published

A customization adds `platform(project(":versions"))` / `platform(project(":versions_test"))` to every
cartridge, and the cartridges are published - so their POMs reference a module that never reaches the
repository. If the platform project applies only `java-platform`, there is no publication at all and
every consumer of the published cartridges gets an unresolvable `dependencyManagement` entry. Check it
with `gradlew :versions:tasks --all | <grep> publish`: no output means no publication.

The fix is what `icm-as` does in its own `versions/build.gradle.kts`:

```kotlin
plugins {
    id("java-platform")
    id("maven-publish")
}

// every cartridge POM references this platform, so it has to be published alongside them
publishing {
    publications.register("mvn", MavenPublication::class.java) {
        from(components.named("javaPlatform").get())
    }
}
```

Use the same publication name the ICM plugins use for cartridges (`mvn`) so the
`publish<Name>PublicationTo…` task names stay consistent.

### The platform constrains an unpublishable cartridge

The constraint loop from D3 walks every subproject carrying `CartridgePlugin` or `DevelopmentPlugin`.
Not all of those are published: in `icm-gradle-plugin`, `maven-publish` is applied by `PublicPlugin`
(and therefore by `ProductPlugin`, which applies it), while `TestPlugin` and `ExternalPlugin` only react
to it via `plugins.withType(MavenPublishPlugin::class.java)`. A test cartridge such as `ft_test` is
consequently a `CartridgePlugin` project with no publication, and constraining it puts an unresolvable
entry into the published platform.

`icm-as` avoids this implicitly by restricting the loop to its source roots
(`platform`, `business`, `content`, `b2b`, `icm_as`). A customization with a flat project layout has no
such boundary, so filter on the thing that actually matters - whether the project publishes:

```kotlin
rootProject.subprojects.forEach { subprj ->
    // only cartridges that are actually published may be constrained here
    subprj.plugins.withType<org.gradle.api.publish.maven.plugins.MavenPublishPlugin> {
        subprj.plugins.withType<com.intershop.gradle.icm.cartridge.CartridgePlugin> {
            depConstraints.add("api", depFactory.createProjectDependency(subprj.path))
        }
        subprj.plugins.withType<com.intershop.gradle.icm.cartridge.DevelopmentPlugin> {
            depConstraints.add("api", depFactory.createProjectDependency(subprj.path))
        }
    }
}
```

Nesting the `withType` callbacks keeps it order-independent: both fire whenever both plugins end up
applied, regardless of which arrives first.

Verify on the generated POM, not on the console: `build/publications/mvn/pom-default.xml` must still
contain every publishable cartridge and must no longer contain the test cartridge. Count the
`dependencyManagement` entries before and after - the delta should be exactly the unpublished projects.



The warning text alone carries no location. Use:

```shell
gradlew help --warning-mode all "-Dorg.gradle.deprecation.trace=true"
```

and read the stack trace. A frame like `Build_gradle.lambda$1$0$0(build.gradle.kts:272)` names the
*script type*, not necessarily the root `build.gradle.kts` - it can be any subproject script of that
name. In the real migration the root script contained no `constraints` block at all; the actual source
was `versions/build.gradle.kts:272`.

Deprecations that only fire when tasks are *realized* will not show up under `help` - use
`gradlew tasks --all` for those.
