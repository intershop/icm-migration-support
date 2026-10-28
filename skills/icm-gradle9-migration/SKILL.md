---
name: icm-gradle9-migration
description: Migrate an Intershop ICM customization build from Gradle 8 to Gradle 9 and make it fit for Gradle 10 - aligning Intershop plugin versions, bumping the wrapper, and removing deprecated Gradle API usage so the build survives `--warning-mode fail`. Use this skill whenever someone works on an ICM or customization Gradle build and mentions Gradle 9, Gradle 10, a wrapper upgrade, deprecation warnings, `--warning-mode`, `validatePlugins`, "Invocation of Task.project at execution time", "Could not find method javaexec()", "Property has implicit dependency", "Declaring a dependency on an unpublished project", configuration cache problems, "did not discover any tests to execute", or aligning `icm.docker` / `icm.project` / `isml` / `jaxb` / `cartridge-resourcelist` / `escrow` / `gitflow` plugin versions - even if they do not use the word "migration". This skill is about the build toolchain only; for the ICM 7.10 project layout, cartridge content and API deltas, use the icm-migration skill instead.
---

# Migrating an ICM Customization to Gradle 9

ICM 12 and the whole Intershop Gradle plugin chain have been migrated to Gradle 9 and cleaned of
deprecated Gradle APIs so they keep working on Gradle 10. Customization builds that consume ICM have to
follow.

This skill is the distilled result of actually doing it: 15 Intershop plugin repositories, `icm-as`
itself, the four standard customizations (`icm-as-headless`, `icm-solrcloud`, `icm-as-demo-data`,
`a_responsive`) and a real customer customization (`edezz-icm`). A standard customization needed only
**2 to 4 changed files**; a customer project with its own third-party plugins and generated sources came
to six. Either way the work is small. What costs time is the handful of traps where the build *looks*
migrated but is not. Those are the real value here.

## When this applies

A build is affected if it applies any Intershop plugin (`com.intershop.gradle.icm.project`, `icm.base`,
`icm.cartridge*`, `icm.docker*`, `isml`, `jaxb`, `cartridge-resourcelist`, `escrow`,
`version.gitflow`) or consumes the published platforms `com.intershop.icm:versions` /
`versions_test`.

Prerequisites: JDK 21 toolchain, and an ICM release that is already on Gradle 9.

**A project still on the 7.10 layout is not ready for this.** This skill assumes a customization that
already builds against ICM 11+ with `build.gradle.kts` and the ICM 11+ resource layout. If the tree
still has `staticfiles/`, `build.gradle` or 7.10 package names, do that migration first — the sibling
playbook is `../icm-migration/SKILL.md`, and the Gradle 9 work is one of its post-migration
maintenance items, not a phase of it.

Note that "on Gradle 9" refers to the plugin chain, not necessarily to a tagged ICM release. The
Intershop plugins are published independently, so a customization can migrate while the matching
`icm-as` Gradle 9 work still sits on a branch - the published `icm-as` *artifacts* a customization
consumes are unaffected by which Gradle built them. When the Gradle 9 state of `icm-as` is only on a
branch, read the versions from that branch (step 1 below) and say so, rather than falling back to the
Gradle 8 versions of the last tag.

## How to choose the versions

Never hardcode or recall version numbers. They move with every ICM release, so a number written into a
guide, a comment or a chat answer is wrong within weeks (`dockerPluginVersion` went 7.5.0 -> 8.0.0 ->
9.0.1 within one release cycle).

**The rule: for each plugin, take the highest released version inside the same major as the matching
`icm-as` release uses.**

The major is the compatibility boundary - the Intershop plugins follow semantic versioning, so a major
jump is where breaking changes live and where icm-as itself had to adapt. Staying inside the major keeps
the customization compatible with the ICM release it builds against, while the highest version within it
picks up the fixes published since that ICM release was cut. Those two goals are why neither "copy
icm-as exactly" nor "always take latest" is right.

**Step 1 - read the major from `icm-as`:**

| what | where in `icm-as` |
|---|---|
| Intershop plugin versions (`icm.docker*`, `escrow`, `jaxb`, `javacc`, `isml`, `cartridge-resourcelist`, `architectural.report`) | `settings.gradle.kts`, `pluginManagement { plugins { … } }` |
| `icm-gradle-plugin`, `gitflow-version-gradle-plugin` | `buildSrc/build.gradle.kts` |
| third-party build plugins (`com.gradle.develocity`, `com.dorongold.task-tree`, versions plugin) | `settings.gradle.kts` |
| Gradle version | `gradle/wrapper/gradle-wrapper.properties` |

**Step 2 - find the highest release in that major:**

```
Gradle plugins: https://plugins.gradle.org/m2/<id-as-path>/<id>.gradle.plugin/maven-metadata.xml
Maven Central:  https://repo1.maven.org/maven2/<group-path>/<artifact>/maven-metadata.xml
```

Exclude prereleases - anything with `-RC`, `-Beta`, `-alpha`, `-M<n>` in the version. They show up in the
metadata and will otherwise be picked as "highest". `io.github.ben-manes.versions` can do the lookup for
you where the build already applies it, but verify the major before accepting what it proposes: that
plugin reports the latest *overall* version, which may be the next major.

The same rule applies to the Gradle wrapper itself - stay on the Gradle major that icm-as uses and take
the highest release within it.

Check **both** `settings.gradle.kts` and `build.gradle.kts` in the customization - plugin versions are
declared in both, and a shared variable such as `dockerPluginVersion` usually feeds several ids at once
(`icm.docker`, `icm.docker.customization`, `icm.docker.solrcloud`, `icm.docker.gebtest`).

If a plugin turns out to have no release above the one icm-as uses, that is the answer - use that one,
do not jump a major to find something newer.

## Workflow

Work in this order and verify after each step. The steps are independent enough that a failure is
immediately attributable, which is the whole point of not doing them in one go.

### 1. Align the Intershop plugin versions

Update `settings.gradle.kts` (and `buildSrc/build.gradle.kts` if present) using the rule above: same
major as `icm-as`, highest release within it.

Do this *before* the wrapper bump: the new plugin majors still support Gradle 8.5 upwards - their
TestKit matrix covers 8.5, 8.10.2, 9.x - so this step can be verified on the old wrapper. The migration
deliberately did **not** raise the minimum supported Gradle version. (If asked "which Gradle does plugin
X require?", read `intershop.gradle.versions` in that plugin's `build.gradle.kts`; never derive it from
the wrapper version.)

### 2. Bump the third-party build plugins

Typically `com.gradle.develocity`, `com.dorongold.task-tree` and the versions plugin - same rule, same
major as `icm-as`, highest release within it.

The versions plugin id was **renamed** to `io.github.ben-manes.versions` (previously
`com.github.ben-manes.versions`). A build still using the old id must rename it. The same applies to
`com.intershop.gradle.escrow-plugin`, which is now `com.intershop.gradle.escrow` - and note that the old
id still exists on the portal as an unrelated plugin with much higher version numbers, so "take the
highest version" on the old id gives a wrong and confusing answer.

Third-party plugins also have to be checked for being *abandoned* rather than merely outdated. Read
`lastUpdated` in the plugin's `maven-metadata.xml`: a plugin whose last release predates Gradle 9 will
not be fixed, and if it calls a removed API it has to be replaced rather than upgraded. See D8 in
`references/deprecations.md`.

### 3. Bump the wrapper

```shell
gradlew wrapper --gradle-version <highest release in the Gradle major icm-as uses> --distribution-type bin
```

Run it **twice**: the first pass only rewrites `gradle-wrapper.properties`, the second regenerates
`gradlew`, `gradlew.bat` and `gradle-wrapper.jar`. All four files must show up in `git status`.

Expect the first build afterwards to take several minutes - changing the Gradle version invalidates every
compiled Kotlin DSL script, so all `*.gradle.kts` files recompile against the new API on a cold daemon.

**The order of steps 3 and 4 is not a suggestion.** Some replacements from step 4 use API that only
exists in Gradle 9 - `dependencyFactory.createProjectDependency` (D3) is the obvious one - so applying
them first makes the build script fail to *compile*, and the `wrapper` task can then no longer run.
If step 4 has already been started, revert those files, bump the wrapper, and re-apply afterwards.

### 4. Remove the deprecated API usage

Build with `--warning-mode fail` and fix what fires. The catalog of concrete fixes, with before/after
snippets taken from the real migrated code, is in **`references/deprecations.md`** - read it when you hit
a warning, or up front if the build is large. Trap D1 (`Task.project` at execution time) hit *every
single* customization, so look for it even if nothing has fired yet.

### 5. Verify

```shell
gradlew help                                        --warning-mode fail
gradlew tasks --all                                 --warning-mode fail
gradlew assemble                                    --warning-mode fail
gradlew testClasses                                 --warning-mode fail
gradlew produceReleaseMessage --rerun               --warning-mode fail
gradlew publishToMavenLocal --rerun-tasks           --warning-mode fail
```

Before running any of them, confirm the build is actually resolving the ICM version it claims to - a
shared `GRADLE_USER_HOME` overrides the project's `gradle.properties` and can point the whole
verification at the wrong ICM. See `references/pitfalls.md`.

Three of these are not decoration:

- `tasks --all` *realizes* every task. Some deprecations fire only at task creation and stay invisible
  during `help`.
- `produceReleaseMessage --rerun` is an example of the most important rule in this whole migration:
  **`--warning-mode fail` only reports deprecations that actually fire.** `assemble` never executes
  `produceReleaseMessage`, so a build can look perfectly clean and still break on Gradle 10. Any task
  outside the normal build chain has to be *run* at least once. The first customization was reported as
  done on an `assemble`-only basis - wrongly, and the very first `produceReleaseMessage` run proved it.
  Not every project has this task; when it is missing, run the out-of-chain tasks that project *does*
  have (`references/pitfalls.md` lists the usual candidates).
- `publishToMavenLocal --rerun-tasks` is the only step that reaches the publishing code, and D10
  (a dependency on an unpublished project) fires nowhere else. `--rerun-tasks` matters because the
  publish tasks are otherwise up-to-date and a deprecation in a skipped task is a deprecation you
  do not see.

Before trusting a green result, read **`references/pitfalls.md`**. It covers the ways this build lies to
you (tasks with `ignoreFailures = true` always exit 0; a build that fails before the tests leaves the
previous, green JUnit XML in place; a shared `GRADLE_USER_HOME` that silently substitutes the ICM
version), which failures are environmental rather than migration defects (stale dependency-cache locks
look exactly like a broken plugin version), and the upgrade ceilings that are not worth attacking
(Geb caps Selenium and Groovy; the asciidoctor plugin has no deprecation-free alternative yet).

Two of the fixes change what the build *publishes* rather than how it runs - D8 regenerates sources with
your own task and D10 adds a publication and drops constraints. Both are verifiable by diffing an
artifact (generated sources, `pom-default.xml`); do that rather than declaring them fine because the
build is green.

## Commit

Follow the ICM pattern, with the ticket number from the branch name appended:

```
chore!: migrate to gradle 9 (#XXXXXX)

* updated plugins:
  * pluginName:oldVersion -> newVersion
* updated libraries:
  * libName:oldVersion -> newVersion
* replaced calls to Gradle API that would not work for Gradle 10
```

Omit empty sections.
