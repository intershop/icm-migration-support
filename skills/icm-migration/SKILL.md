---
name: icm-migration
description: Migrate an Intershop ICM customization project from 7.10 to ICM 11/12/13/14. Use when working on an ICM cartridge migration - converting build.gradle to build.gradle.kts, moving staticfiles into the ICM 11+ resource layout, reconstructing cartridge dependencies, splitting app-extension components into as_ cartridges, or running the icm-migration-support tool. Portable across projects.
---

# ICM 7.10 to 14.x migration

Reusable playbook. Everything here is project-agnostic; per-project decisions belong in that
project's `docs/adr/` and `docs/migration/PROGRESS.md`.

**Start by copying the starting kit**, `templates/` in this plugin's repository: the `CLAUDE.md`
working agreements, the `PROGRESS.md` skeleton with its command queue and tool backlog, the ADR with
the five questions a migration cannot answer for itself, a `smoke.py` skeleton and the compose mounts.
It is one copy and it saves every project reinventing the same bookkeeping badly.

**This skill ships with the tool it describes**, `intershop/icm-migration-support`, and that is
deliberate: statements here like "step 040's rename map covers only X" are claims about specific code in
that repository, and a skill living apart from the code it documents rots the day someone fixes the
code. When a migration turns up something any 7.10 project would hit, raise it there rather than fixing
it locally and meaning to contribute it later.

## The model

Two layers, and keeping them separate is the whole point.

**Layer 1, deterministic.** [icm-migration-support](https://github.com/intershop/icm-migration-support)
applies 16 YAML-described steps for 7.10 to 11, plus OpenRewrite recipe drops for 11 to 12 and 12 to
13. Auto-commits per step, so every step is revertable alone, and reports SUCCESS / SKIPPED / UNKNOWN
/ WARNING / FAILED per file operation. Use it for every mechanical transformation. Do not hand-migrate
what it already encodes; you lose reproducibility and a vendor-aligned baseline for no gain.

**Layer 2, judgement.** No tool covers these: cartridge-level dependency reconstruction, component and
ApplicationType placement, ISML expression adaptation, logback cleanup, `configuration.xml` wiring,
and the 13-to-14 delta, which has no step set or recipes upstream at all. This is the agent's work,
verified by a compiler.

**Migration ends with a working server. Everything after that is maintenance.** This is the scope
line, and it is worth defending, because a 7.10 tree offers endless things worth improving and each
one looks small. Dead configuration, duplicated descriptors, preparers that never ran, a pipelet that
would be better as a pipeline node: none of these block a running server, so none of them is a
migration step. Fix what stops the server, record the rest as maintenance with enough detail that
someone can pick it up, and hand it over. A migration that ships is worth more than a tidy one that
does not.

**Prefer a Guice module over a `*.component` file wherever the platform offers one.** This is a
standing preference, not a style note. Component XML fulfillment requires the instance being fulfilled
to be *declared* in a context visible from the fulfilling cartridge, so it is sensitive to cartridge
placement and ordering, and it fails at `global` context configuration during startup with a message
that names the instance rather than the cause. The equivalent Guice binding has no declaration
requirement and no ordering sensitivity. The commonest case by far, registering cartridges at
ApplicationType cartridge lists, is in
`references/cartridge-content-delivery.md` under "Registering cartridges at ApplicationType lists",
and it replaced an entire planned `as_` cartridge split on the project this skill came from.

**A cartridge that ships pipelets or ISML templates must apply the plugin that indexes them.** ICM 11+
finds both through a generated index file rather than by scanning, so a missing plugin makes the
content invisible at runtime while the build stays green. Pipelets and ORM classes need
`id("com.intershop.gradle.cartridge-resourcelist")`, templates need `id("com.intershop.gradle.isml")`.
The pipelet failure is the nastiest, because the pipeline inserts a placeholder and keeps running, so
the symptom is a feature quietly doing nothing. The migration tool applies these unevenly, so audit
by content location rather than trusting the build scripts. Details and the audit commands are in
`references/cartridge-target-layout.md` section 9b.

**Prefer a pipeline node over a pipelet for new and reworked code.** A `@PipelineNode` class needs no
base class and no hand-maintained XML descriptor, and it declares its inputs and outputs as typed
interfaces, so the pipeline dictionary binding is compiler-checked instead of resolved from strings at
runtime. It needs `annotationProcessor("com.intershop.platform:pipeline")`, which generates both the
descriptors and the index. Converting existing pipelets is a nice to have, not a migration step: it is
maintenance, and it belongs after the server runs. See `references/cartridge-target-layout.md` section 9c, with
`com.intershop.beehive.core.capi.request.Redirect` as the reference example.

**Ask the running server, do not only read its logs.** Once Phase 6 produces a server, an agent
container can usually reach it over HTTPS, and that is the fastest verification loop in the whole
migration. It also produces better evidence: that a dbinit step *created the service configuration*
is a stronger claim than that the step logged `Success`, and this migration had two defects where the
log looked fine. `scripts/icm_client.py` is a small project-agnostic client with ICM's backoffice
login built in, because the login form posts to a generated `ViewApplication-ProcessLogin` URL and
the organization goes in a hidden field. Pair it with a project-side smoke script holding the
expectations. Certificates are self-signed on dev instances, so verification is off and it must never
point at production.

**Mount the ICM platform sources read-only and treat them as the authority on every API question.**
Not the compiler output, which says a call is wrong without saying what replaced it or why, and
occasionally misdiagnoses it outright. This one habit changes how Phases 3 to 5 go; the practicalities,
including the two directories that will waste your time, are in `references/compiler-driven-migration.md`.
**Pair every search that comes back empty with a positive control of the same shape that must return
hits.** An empty `grep` over that mount has two well-documented ways of meaning "not searched" rather
than "not present", and both have produced findings that had to be retracted. Same file.

**Never report a step done on the strength of a zero exit code.** Either a static check confirms it or
a Gradle run does. A separate AI runner for this migration
([icm-migration-copilot-support](https://github.com/intershop/icm-migration-copilot-support)) decided
phase outcomes purely from exit codes and logged `Phase completed successfully` for a phase whose
agent output said migration could not proceed. Its prompt tables are worth keeping (see
`references/`); its orchestration is not.

## Preflight, in order

**Two prerequisites to secure before the checks below.** Both are external, both are ordinary asks
rather than work, and both have held up a real migration.

- **Get the ICM platform sources as files and mount them read-only.** Intershop always has them and
  partners usually do, and they are handed over as a plain file tree, so this is an ask, not a build.
  Make it the first ask of the engagement and match the source version to the target release. It is the
  highest-leverage practice in this playbook (`references/compiler-driven-migration.md`), and Phases 3
  to 5 go measurably worse without it: every API question becomes an inference from compiler text,
  which is the mode that produces findings you later retract.
- **Resolve artifact feed credentials before Phase 1, not during it.** Nothing compiles without them
  and there is no workaround, so an unresolved feed login is a hard stop rather than a background task.
  It blocked one migration's first compile for a full day. They have to be resolved anyway, so resolving
  them first costs nothing.

Each of the following has bitten a real project. Check them before running any step.

1. **The working tree must be clean.** `Migrator.validateGitRepository` aborts otherwise, and
   `GitRepository.commit` stages with `git add .` plus `setAll(true)`, so anything loose gets swept
   into a step's commit. Add tool clones and local container files to `.gitignore` first.
2. **Check whether "modified" files are really CRLF churn.** A repo with `* text=auto` in
   `.gitattributes` but CRLF-committed blobs reports files dirty forever. Confirm with
   `git show HEAD:<path> | tr -d '\r' | diff - <(tr -d '\r' < <path>)`; if empty, fix with
   `git add --renormalize .` rather than stashing or discarding.
3. **Set a git identity in the repo.** The tool commits through JGit and fails without one.
4. **Rescue the assembly project before step 005 deletes it.** `assembly_acme`-style projects
   routinely hold far more than a cartridge list: the ordered cartridge list, test cartridge
   exclusions, global dependency exclusions, the `checkClassCollisions` ignore list, and frequently
   an entire Geb acceptance suite under `src/remoteTest/`. Step 005 runs **first** and deletes all of
   it, under a commit message that says "remove assembly projects".
   The clean rescue: `Migrator.migrateProjects` only descends into directories containing a
   `build.gradle` or `build.gradle.kts`, so `git mv` the suite to its final home **now** and leave
   that directory without a build script until the tool run is finished. History follows, no
   duplicate commit, nothing to remember to restore. Keep a copy of the assembly `build.gradle`; the
   dependency phase needs its cartridge list.
5. **Kotlin must be on PATH as `kotlin.bat` on Windows.** Step 070 calls
   `new ProcessBuilder("kotlin.bat", ...)`, and `ProcessBuilder` resolves that literal filename
   without applying `PATHEXT`. Chocolatey shims are `.exe`, so a working `kotlin` command proves
   nothing. Verify with `where kotlin.bat`. The Chocolatey `kotlinc` package puts the real one in
   `C:\ProgramData\chocolatey\lib\kotlinc\tools\kotlinc\bin`.
6. **Decide about step 901 before it runs.** `CreateEnvironmentExampleFiles` regenerates
   `environment.bat.example`, `icm.properties.example`, `clean.bat` and `clean.sh` from generic
   templates, logging `File already exists, content will be replaced`. If the project has
   hand-tailored versions, skip 901 or restore them afterwards.
7. **Fix the template's project discovery before anything else needs it.** The ICM 11+
   `settings.gradle.kts` includes **every** top-level directory that is not on a hard-coded
   `excludeList` and does not start with a dot, without checking whether it holds a build script. So
   `docs/`, `docker-compose/`, placeholder folders, build output and any tool clone all become Gradle
   projects. This stays invisible through `projects`, `tasks`, `compileJava` and `build`, because
   nothing asks an empty project for anything, and then fails the **product** build with a missing
   cartridge descriptor, naming whichever directory it reached first. The shipped `excludeList` already
   shows the strain: it excludes `docker` but not `docker-compose`. Add the build-script requirement
   rather than extending the list:

   ```kotlin
   val filter: (File, String) -> Boolean = { dir, filename ->
       val candidate = File(dir, filename)
       candidate.isDirectory && ! excludeList.contains(filename) && ! filename.startsWith(".") &&
               (File(candidate, "build.gradle.kts").isFile || File(candidate, "build.gradle").isFile)
   }
   ```

   It self-maintains, and it mirrors `Migrator.migrateProjects`, which descends only into directories
   containing a build file. **Do this before cloning the migration tool anywhere near the project**, or
   the clone becomes a phantom cartridge.
8. **`versions/build.gradle.kts` must exist.** Step 055 targets the `.kts` file
   (`MigrateVersionFiles.BUILD_GRADLE_KTS`) and records a critical error if absent. Intershop's own
   `migration-7.10-11.md` says `versions/build.gradle`; the docs are stale, the code is right.
9. **Scan for duplicate property keys.** Run `scripts/scan_duplicate_property_keys.py <project>`.
   7.10 loaded everything through `java.util.Properties`, which keeps the **last** duplicate and never
   complains, so a preparer can sit dead in a descriptor for years. ICM 14 parses the DBPrepare
   descriptors with a validating reader that rejects the whole file:

   ```
   ConfigurationValidationException: Duplicate property key detected in
   '.../<cartridge>-LOCAL.jar!/resources/<cartridge>/dbinit.properties' : Class50
   ```

   **The blast radius is the whole cartridge, not the duplicated entry.** A rejected descriptor
   leaves that cartridge with only `starts` and `Register cartridge` in the DBPrepare log; every one
   of its preparers is skipped. DBPrepare logs the error and keeps going, so the run still ends
   "successfully" with the cartridge silently uninitialised. Grep the log for `Invalid properties in`.

   Fix by renumbering the later key into a free slot, not by deleting a line: both entries were meant
   to run, and the one that has been losing since the duplicate appeared is usually the *first*.
   `git blame` the two lines to see which was bolted on and keep the intended order.

   Do not write this scan as a regex. Two shapes produce false positives, and both occur in real
   descriptors: DBPrepare preparer arguments are backslash-continuation lines that are indented and
   contain `=`, which a naive scan reads as a duplicate key; and files are routinely CRLF, so the
   terminator must be stripped before the continuation test. The script implements
   `java.util.Properties` line joining for that reason. A related trap: `:` is a key/value separator
   in Java properties, so a `.properties` file whose keys contain colons is not the file it claims to
   be. Rename such reference data to `.txt`.

## Sequence

**Preparation.** Apply the ICM 11+ customization template over the 7.10 tree (root `build.gradle.kts`,
`settings.gradle.kts`, `versions`, `versions_test`, `ft_production`, `ft_test`), then branch. Back up
the deployed 7.10 cartridge list; it is the cross-check for the dependency phase.

**Phase 1, 7.10 to 11.** Run the 16 steps **one at a time**, not `migrateAll`, reviewing the operation
report and `git show --stat` after each:

```
cd <tool>
gradlew migration:migrateOne -Ptask=projects -Ptarget=$ICM \
  -Psteps=src/main/resources/migration/001_migration_7x10_to_11/005_RemoveAssembly.yml
```

Pilot on one cartridge first with `-Ptask=project -Ptarget=$ICM/<small_cartridge>` and the whole step
folder. That path also runs `prepareMigrate`, which is where the Kotlin guard actually fires; the
`projects` path calls `prepareMigrateRoot`, which `ConvertToKotlin` does not override, so a missing
Kotlin does **not** abort a full run. It silently fails step 070 once per cartridge instead.

**Phase 2, residue.** Redeclare the shared libraries the 7.10 root `subprojects` block provided.
Rehome whatever the assembly carried. Deal with everything still under `staticfiles/`, remembering that
`staticfiles` is a **backward-compatibility path** in ICM 11+ and not a resting place. Strip file
appenders from logback: in a container deployment everything goes to stdout through the root `Console`
appender, and a cartridge fragment declares levels only. Wire `cluster` and `domains` content into
`configuration.xml`, classifying each file by content rather than by path.

Most of this phase is delivery mechanics, and each kind of content has its own: see
`references/cartridge-content-delivery.md`, which also covers preparer scoping, the dbinit/dbmigrate
split, and how to ship class patches if a project has them.

**Phase 3, dependencies and components.** The expensive phase. Start with
`scripts/derive_import_surface.py` (below), work in the dependency order recovered from the old
cartridge list, and let the compiler arbitrate. See `references/component-application-types.md` for
the `as_` cartridge split and the `intershop.B2CWebShop.*` to `intershop.WebShop.*` renaming.

**Phase 4, 11 to 12 to 13.** Drop in the recipe sets, then
`GRADLE_OPTS=-Xmx4G gradlew --init-script rewrite.gradle rewriteRun`, once per version step, compiling
between them. Manual residue is listed in `references/steps-11-to-12.md` and
`references/steps-12-to-13.md`.

**Phase 4 is normally not a phase at all.** ICM 11, 12 and 13 are no longer deployment targets, so
there is one route: 7.10 straight to the current release. Compiling against that target surfaces the
union of every intermediate delta at once, and fixing them is Phase 5, so Phases 4 and 5 collapse into
one. Migrate version by version only if the project genuinely has to deploy at an intermediate release,
which is now rare.

Each step set contains a single `ClasspathResourceFileCopier` that copies `rewrite.gradle` and
`rewrite.yml` into the root; all the substance is in the recipes, and **every one of them fixes a
compile-breaking delta** (`ChangePackage`, `ChangeMethodName`, `DeleteMethodArgument`,
`ChangeMethodParameter`). On the direct route they have nothing left to change, so run them afterwards
as a **cross-check**: no change confirms the compiler-driven route landed where the vendor's recipes
would have; any change means something was hand-fixed differently from the way Intershop intends, and
the diff says where.

The recipe *content* keeps its value even when the version framing is dropped, because a rename that
happened in ICM 12 is still a rename between 7.10 and 14. Read them as a lookup table of old name to new
name, alongside the step 040 and 065 maps.

Either way, read the diff before committing it. These recipes match on signature and cannot tell that a
call site is already correct, so on already-migrated code they can rewrite something that was right.

**Phase 5, the last version step, 13 to 14 at the time of writing.** No step set, no recipes, no
tooling at all. Compiler-driven against the official API change guide for that release, plus
`references/icm-14-api-deltas.md` for the deltas already found by doing this and
`references/compiler-driven-migration.md` for how to work when the compiler is the only oracle. Read the
method file before starting Phase 3; it applies from there on, not just here.

**The target moves with every ICM release, so do not treat any version as the destination.** Check what
the current release is when the project starts. Published APIs stay largely stable between releases, so
each additional step is small, and the delta file is the template rather than the answer: name the next
one `icm-<N>-api-deltas.md`, keep the same shape, and record the compiler text that announced each
delta so the following project matches on symptoms.

The expensive deltas are not renamed methods, which the compiler points straight at. They are **removed
concepts**, where the compiler reports a missing method and cannot tell you that the idea behind it no
longer exists. `IS_SHARE` ceasing to be a server directory was one: nothing was renamed, a concept was
deleted, and the fix was a data change across sixteen files rather than a code change. Expect one or two
per release step, budget for them, and treat the rest as mechanical.

**Phase 6, runtime.** Compiling is not migrating. Component wiring, DBPrepare steps and site content
only fail at runtime.

**And a green build says almost nothing, so plan the verification rather than declaring victory on it.**
Count the test tasks before assuming there is a safety net: on the reference project 31 of 35
`compileTestJava` tasks were NO-SOURCE. Three defects from that migration passed both a green build and
a running server, and every one was found by reading a log closely: a descriptor rejected for a
duplicate key that skipped a whole cartridge's DB preparation, three `*.component` overrides inert since
the ICM 11 namespace rename, and a pipelet resolving to a placeholder. **The failure mode of ICM 11+ is
silence, not a stack trace.** `references/verifying-the-migration.md` is the plan: what to audit, the
tiers in cost order, and the four questions to ask on day one. Its central assumption is that the old
7.10 system is still running and is therefore an oracle you can diff against, which beats any assertion
anyone would write by hand.

### Cartridge order is derived now, so "the init cartridge ran first" is false

Expect a wave of DBPrepare failures that all say some domain, organization or channel does not
exist, from cartridges whose steps are correct and unchanged.

7.10 assemblies listed cartridges in an explicit `order` expression, conventionally init cartridges
first, and the platform honoured it. **ICM 11+ derives cartridge order from the dependency graph.**
An init cartridge depends on nearly everything, so it sorts almost *last*. Every dbinit step written
against the old assumption now runs before the data it needs exists. In one project this was 28 of
2,949 steps, seven cartridges times four `Service*Preparer` steps, failing 22 seconds before
`OrganizationPreparer` created the organizations they configure.

It is quiet: DBPrepare logs each failure, keeps going, and finishes with a summary. Nothing aborts.

**Fix with the phase, never by reordering.** DBPrepare runs every `pre.` step across all cartridges,
then every main step, then every `post.` step, so a `post.` step sees everything any main step
created regardless of cartridge order:

```properties
post.Class10 = com.intershop.component.service.dbinit.preparer.ServicePermissionPreparer \
    com.acme.component.foo.prepare.FooServicePermission
```

Anything depending on domains, organizations, channels or sites belongs in `post.`. A
`SiteContentPreparer` belongs in `pre.`. Confirm the phase boundary in the log before trusting it:
the first `:post.` line must come after the last main-phase line of the init cartridge.

```sh
grep -n ':post\.' start.log | head -1        # must be greater than:
grep -n 'Success: \[init\]init_<project>:Class' start.log | tail -1
```

### Prefer migration-only descriptors

Shipping both `dbinit.properties` and `migration-to-*.properties` means maintaining two paths, and
the init path stops being exercised as soon as the database exists, so it rots silently. A cartridge
that ships **only** `migration-to-*.properties` is fully supported: on a fresh database its steps run
in init mode, logged as `[init]<cartridge>:ClassNN ... Version:1.1.0`.

Consolidate where the evidence is unambiguous. `git hash-object` both files first: identical hashes
mean deleting `dbinit.properties` loses nothing. Where they differ, this is a project decision and
needs the real question asked, namely whether the migration chain builds a complete database from
empty. Do not rename a `dbinit.properties` to `migration-to-*.properties` and call it consolidated.

Note the two properties are independent: being migration-only neither causes nor cures the ordering
failure above.

## Tooling

`scripts/derive_import_surface.py <project-root>` is read-only and never touches source. It scans
every `.java` file for `import com.intershop...`, and classifies each package three ways:

- declared in the importing cartridge itself: no dependency needed
- declared in a sibling cartridge: `cartridge(project(":<cartridge>"))`
- neither: resolved to `group:artifact` through `references/cartridge-packages.txt` and
  `references/import-to-cartridge.md`, or flagged `UNMAPPED`

The UNMAPPED rows are the review queue, and they are usually a couple of dozen rather than hundreds.
The `cartridges` column shows which cartridges import each package, which is exactly the evidence
Phase 3 needs. A package resolving to a sibling cartridge that is *not* the importer, while a shorter
prefix of it resolves elsewhere, indicates a split package worth investigating.

Note that static imports leave a class name in the `package` column, since the last segment is
stripped blind. Prefix resolution still lands on the right coordinate; only the label is imprecise.

## Verification: static evidence first, then the Gradle ladder

**Gradle genuinely cannot run at the start, whatever the setup.** The 7.10 root `build.gradle` applies
`ish-*` plugins that no longer resolve, the assembly uses the removed `compile` configuration, and two
competing `settings.gradle` files exist. The first meaningful Gradle command is `gradlew projects`
after step 100. So Phase 1 is gated on static evidence for everyone, which is what makes small
reviewable steps essential.

**Give the agent Gradle from step 100 onward if you can.** It is normally no problem, and it is the
difference between a verification loop measured in minutes and one measured in developer round trips.
Everything below assumes it.

Record conservation counts before starting and check them after: total files, `.java`, `.isml`,
`.pipeline`, `.component`, `.resource`, files under `staticfiles/`. Renames are count-neutral
(`build.gradle` to `.kts`, `.resource` to `.properties`), so any drift means a move step dropped
something. Also assert: no `build.gradle` left, every cartridge has a `.kts`, no `compile` or
`testCompile` configurations remain, no `*.version` at root including the dot-prefixed ones, no
`javax.*` imports except the JDK's own `javax.crypto` and `javax.net.ssl`, and no cartridge declaring
both `cartridge()` and `cartridgeRuntime()` for one coordinate.

Then climb the Gradle ladder in order, never skipping a rung: `projects`, `tasks --all`,
`compileJava --continue`, `compileTestJava --continue`,
`:ft_production:dependencies --configuration cartridgeRuntime`, `test`, `build`, DBPrepare and server
start.

**Do not use `--configuration cartridge` for that rung.** It is not resolvable standalone: project
dependencies resolve, external coordinates get no version, and a perfectly healthy cartridge reports
**72 `FAILED` lines** under a `BUILD SUCCESSFUL`. Read without a baseline it looks exactly like a broken
build, and it has cost one project three round trips and two written-up "findings" that had to be
retracted. The version constraints reach those declarations only once `cartridge` is folded into
`compileClasspath`, so use `compileClasspath` to ask what a cartridge compiles against and
`cartridgeRuntime` to ask what the product ships.

### When the agent cannot run Gradle

A workable fallback, and worth setting up deliberately rather than improvising, because the round trip
is the scarce resource. Keep a numbered queue of commands in the project's progress log, and for each:

- give **one exact command line**, not a description of what to run;
- have it redirect into a `q<N>-<purpose>.log` in the project root, with `*.log` already in
  `.gitignore`, so the output can be pasted or read back verbatim;
- record the result against the queue entry before queueing the next one.

Two rules that matter more here than anywhere else. **Never report a step done on a zero exit code**,
because the whole point is that you did not watch it run. And **batch independent commands**: a queue
entry that blocks the next one costs a full human round trip, so state explicitly which entries can run
in any order.

**The cartridge-list half of that rung needs no Gradle at all.** Diff the `cartridgeRuntime(project(...))`
entries in `ft_production/build.gradle.kts` against the group arrays in the assembly backup with a
twenty-line script. It compares intent against intent, it is exact, and it runs in the agent's
container. Save Gradle for the resolved graph.

## Editing rules

Moves use `git mv`, never copy-and-delete; verify with `git hash-object` before and after that every
file arrived byte-identical and that Git recorded renames. Bulk regex edits on `.java`, `.kts` and
`.groovy` are guarded: count the occurrences the pattern will hit before applying, confirm the
changed-line count matches afterwards, and read the diff. A regex that touches more lines than
predicted gets reverted, not patched.

## References

| File | What it is |
|---|---|
| `references/steps-7.10-to-11.md` | the 16 steps and their manual follow-up |
| `scripts/icm_client.py` | **ours.** HTTP client for a running ICM, with backoffice login. Phase 6 verification |
| `scripts/scan_duplicate_property_keys.py` | **ours.** Preflight 9: duplicate keys that ICM 14 rejects |
| `references/steps-11-to-12.md` | OpenRewrite recipes and manual residue for ICM 12 |
| `references/steps-12-to-13.md` | the same for ICM 13 |
| `references/cartridge-target-layout.md` | post-migration cartridge layout, plugin and dependency mapping |
| `references/component-application-types.md` | ApplicationType instances, `as_` cartridge rules |
| `references/cartridge-packages.txt` | package to `group:cartridge` lookup, 202 entries |
| `references/import-to-cartridge.md` | import prefix to cartridge coordinate, ~200 rows |
| `references/fix-catalogue.md` | known post-migration compile failures and fixes |
| `references/icm-14-api-deltas.md` | **ours.** Real 13-to-14 API and dependency deltas, with the compiler text that announces each |
| `references/compiler-driven-migration.md` | **ours.** How to work in Phases 3 to 5: sources as authority, sweeping, what is and is not a customer decision |
| `references/cartridge-content-delivery.md` | **ours.** How configuration, sites content, preparers and class patches actually reach a running ICM 11+ server. Phase 2 and Phase 6 work |
| `references/verifying-the-migration.md` | **ours.** What a green build does not prove, and the verification tiers that close the gap. Read before Phase 6, not after |

The first five of the vendor files are from `intershop/icm-migration-support` (Apache License 2.0);
`fix-catalogue.md`, `cartridge-packages.txt` and `import-to-cartridge.md` from
`intershop/icm-migration-copilot-support`. Both are copied here so they survive a clone of the project
repo. **They are undated and may have drifted from current ICM.** Treat every row as a lead to confirm
against the compiler, never as authority.

The last two are ours and work differently: they are written *from* migrations rather than before them,
and every project that uses this skill should add to them. `icm-14-api-deltas.md` is the only reference
anywhere covering the 13-to-14 step. When a migration turns up a new delta, append it with the compiler
text that announced it and the platform source that confirmed it, so the next project matches on
symptoms rather than on having read the file.
