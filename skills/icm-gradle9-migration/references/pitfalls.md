# Pitfalls: false greens, environmental failures, and upgrade ceilings

Read this before declaring a migration done. Most of the time lost in the ICM migration went here, not
into the actual code changes.

## The build lies to you

**`assemble` is not a gate.** `--warning-mode fail` only reports deprecations that actually fire. Any
task outside the normal build chain - `produceReleaseMessage` is the standard example, but also anything
custom the project added - has to be *run*, not just configured. A build that is green on `assemble` and
`tasks --all` can still fail on Gradle 10.

**`ignoreFailures = true`.** The ICM `restTest` and `gebTest` tasks are configured this way, so the
Gradle exit code is always 0 even with failing tests. Parse the JUnit XML under
`build/test-results/<task>` instead of trusting the exit code.

**Stale JUnit XML.** When a build fails *before* the tests run - a compile error, an unresolvable
dependency - the XML from the previous run is still on disk and still green. Always check the exit code
and the "What went wrong" section as well. This fooled the migration once: a non-existent dependency
version broke `compileGebTestGroovy`, and the leftover results looked like a clean pass.

**Unstable baselines.** Before using a test as a before/after baseline, run it **twice**. Several ICM
UI and Geb specs are data-dependent and vary not just in outcome but in test *count* between runs. A
spec that reports 2 tests / 1 failure and then 1 test / 0 failures cannot tell you anything about your
change.

**Forgetting `-PprojectVersion` when publishing.** The build silently falls back to `version = "LOCAL"`,
publishes into a `LOCAL` directory and leaves the previous versioned artifacts in the local Maven
repository untouched - so a later consistency check can "match" against two stale things. Verify the
`LastWriteTime` of the jar in the local repository is from the current run.

**A shared `GRADLE_USER_HOME` can verify the wrong thing entirely.** `$GRADLE_USER_HOME/gradle.properties`
*overrides* the project's own `gradle.properties`. On a developer machine that holds several ICM
workspaces, that file routinely carries `localVersion=true` plus `icmVersion = LOCAL`,
`headlessVersion = LOCAL` and so on, and credentials for whichever feed was used last. A customization
pinned to `icmVersion = 14.4.2` then silently builds against a locally published icm-as snapshot. The
symptom is compile errors in product API usage that have nothing to do with Gradle - in the real case a
`LogScope cannot be converted to Level` in a staging processor. **Read that file before trusting any
result**, and check `$GRADLE_USER_HOME` itself: the environment variable may point somewhere other than
`~/.gradle`.

**`produceReleaseMessage` does not exist everywhere.** It is the standard example, not a universal task.
When `gradlew produceReleaseMessage` answers `Task 'produceReleaseMessage' not found`, do not treat that
as "nothing to do" - enumerate the project's own out-of-chain tasks with `gradlew tasks` and run the ones
that need neither Docker nor a database. In a customization that is typically `publishToMavenLocal`,
`createServerInfo`, `provideLibFilter`, `prepareServer`, `prepareContainer`, `prepareTestContainer`,
`createMainPkg`, `createTestPkg`, `provideDockerfile`, `projectReport`, `buildDashboard` and
`dependencyUpdates`. `publishToMavenLocal` earns its place: it is the only one that exposes D10.

## Failures that are not migration defects

`gradlew build` needs live infrastructure in several ICM customizations:

- Geb tests -> `waitForServer` needs a running ICM server.
- ISH unit tests -> `ishUnitTestReport` consumes results the server produced.

If one of these fails, prove whether it is pre-existing before investigating: re-run `build` with the
*old* committed plugin versions. An identical failure means it was already broken and is not yours.
(This was done for `ishUnitTestReport` and confirmed pre-existing.)

The meaningful local gate is therefore `assemble` + `testClasses` + `produceReleaseMessage` +
`publishToMavenLocal`, not `build`.

Solr-based tests additionally need their index: run `gradlew rebuildSearchIndex` first, otherwise most
REST specs fail with HTTP 500 / `SolrException: Collection not found` and look like a code problem.

**Stale cache locks in a shared `GRADLE_USER_HOME`.**

> `Could not resolve all dependencies for configuration 'detachedConfiguration1'.`
> `Cannot invoke "org.gradle.cache.FileLock.writeFile(java.lang.Runnable)" because the return value of
> "org.gradle.cache.internal.DefaultCacheCoordinator.access$300(...)" is null`

This is not a migration defect and not a broken plugin version, even though it likes to name the plugin
you just bumped. It is a stale lock in the dependency cache, which a `GRADLE_USER_HOME` shared between
several Gradle versions runs into after a daemon is killed mid-write. It is also *not* transient: once
it appears it reproduces on every invocation, down to `gradlew help`, and it moves between
configurations from run to run, which makes it look like a different problem each time. Fix:

```shell
gradlew --stop
# then delete, with no daemon running:
#   $GRADLE_USER_HOME/caches/modules-2/modules-2.lock
#   $GRADLE_USER_HOME/caches/journal-1/journal-1.lock
```

Gradle recreates both. Nothing in the cache content is lost. Suspect this whenever a resolution failure
mentions `FileLock` or `DefaultCacheCoordinator` rather than a repository, a 401 or a missing version.

## Upgrade ceilings - do not attack these

**Geb caps both Selenium and Groovy.** `org.gebish:geb-core` / `geb-spock` are dormant - latest release
7.0, January 2023, built against Selenium 4.2.2. Measured by bisection: Selenium 4.34.0 works, and
4.35.0 / 4.36.0 / 4.38.0 / 4.42.0 / 4.49.0 all fail with
`groovy.lang.MissingMethodException: No signature of method: geb.Browser.reportGroup()`. `geb-spock:7.0`
depends on `spock-core:2.3-groovy-4.0`, a Groovy 4 artifact, so Groovy 5 and `spock-2.4-groovy-5.0` are
impossible in Geb projects. Anything pulling Groovy 5 transitively breaks them too (see D7 in
`deprecations.md`).

What *is* possible in a Geb project: moving Spock from a milestone to its final release
(`2.4-M6-groovy-4.0` -> `2.4-groovy-4.0`) and patch bumps of Groovy 4, slf4j and testcontainers. Note
`org.testcontainers:spock` and `:selenium` are not published for testcontainers 2.x, so a shared version
reference caps the whole testcontainers family at 1.x.

The real fix is replacing Geb - ICM is moving to Playwright.

**The asciidoctor plugin.** `org.asciidoctor.jvm.convert` 4.0.5 is the latest release and its internal
`grolifant` library still calls `StartParameter.isConfigurationCacheRequested`, removed in Gradle 10.
Researched alternatives: no 5.x of that plugin id exists, the xbib fork hard-fails on Gradle 9 with
`NoClassDefFoundError: org/gradle/util/CollectionUtils`, and every other asciidoc plugin on the portal is
a *generator*, not a renderer. Keep it, document why in a comment above the declaration, and upgrade when
a final `org.asciidoctor` 5.x ships.

**Plugins two majors behind.** License-report, sonarqube and similar can stay where they are if they
survive `tasks --all` and `assemble` under `--warning-mode fail`. They are actively configured, so a
major jump can require configuration rewrites - that is a separate task with its own risk, not part of a
Gradle migration.

## Working habits that paid off

**Baseline before changing anything.** Where a result is numeric - test counts, generated file contents,
resolved dependency versions - capture it before the change and compare afterwards, rather than judging
"looks fine". The POM diffs and the byte-identical `releaseinfo.properties` check are what made the risky
changes safe.

**Never hand back a refactoring without executing it.** A single edit that joined two lines made all 8
tests of a spec fail with `MissingPropertyException`; it was caught only because the suite was actually
run.

**Use absolute paths for file writes on Windows.** `Set-Content` / `[System.IO.File]::WriteAllText` with
a relative path writes to the process working directory, not the repository you think you are in. Stray
files sat untracked in the wrong repo for four days before anyone noticed. After finishing work in one
repository, run `git status --short` in the neighbouring ones too.

**PowerShell**: quote property arguments - `& .\gradlew.bat publishToMavenLocal "-PprojectVersion=8.0.0"`
- and use `;` rather than `&&` to chain.
