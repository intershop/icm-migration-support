# ICM Migration Support

Tooling for migrating an Intershop ICM customization project from **7.10 to the current release**.

A migration has two halves, and keeping them apart is the point of this repository.

- **The deterministic half.** Moving files into the ICM 11+ layout, converting build scripts, renaming
  dependencies and packages. Mechanical, high volume, and worth doing the same way on every project.
  That is the migration tool here: a set of YAML-described steps, one commit per step, with a
  machine-readable report of every file it touched.
- **The judgement half.** Cartridge dependency reconstruction, component and ApplicationType placement,
  configuration wiring, the API deltas of the newest release, and deciding what a running server needs.
  No step set covers these. That is the **`icm-migration` playbook**, which ships here so that it
  versions with the code it describes.

The tool is built to be driven by an agent and checked by a person. Its output is JSON first and prose
second, its exit codes distinguish every outcome, and it reports what it did **not** handle as well as
what it did, because unrecognised content is where a migration's risk lives.

## Start here

```sh
# 1. the playbook and the starting kit
claude plugin marketplace add intershop/icm-migration-support \
    --sparse .claude-plugin skills templates
claude plugin install icm-migration

# 2. copy the starting kit into the project being migrated, then follow the playbook
```

The marketplace is read from this repository's **default branch**. If the playbook has not merged there
yet, name the branch that carries it, `intershop/icm-migration-support@<branch>`, otherwise the add
fails with `Marketplace file not found`.

| | |
|---|---|
| [the playbook](skills/icm-migration/SKILL.md) | the whole sequence, the preflight, and twelve references |
| [starting kit](templates/README.md) | what to copy into a project on day one |
| [machine-readable interface](docs/agent-interface.md) | exit codes, the operation log, and `inventory` |
| [7.10 to 11 steps](docs/migration-7.10-11.md) | what each automated step does, and its manual follow-up |
| [API cross-check](docs/api-crosscheck.md) | the OpenRewrite recipe set, and why it is a check rather than a step |

## The route

**7.10 straight to the current release.** ICM 11, 12 and 13 are no longer deployment targets, so there
is no reason to stop at them: compiling against the target surfaces the union of every intermediate API
delta at once. The recipe sets that used to be per-version steps are merged into a single
[cross-check](docs/api-crosscheck.md), where no change confirms the hand fixes landed where Intershop
intends and any change is a finding.

## Running the migration tool

### Prerequisites

- **Java 21.** An appropriate JDK installed and configured.
- **Kotlin.** One step shells out to a Kotlin script, so the compiler must be on the path. On Windows it
  is invoked as `kotlin.bat` literally, without `PATHEXT`, so verify with `where kotlin.bat` rather than
  assuming a working `kotlin` command is enough.
- **A git repository.** The project must be checked out, deployed locally, and have a **clean working
  tree**; the tool refuses to run otherwise. The `.git` folder is looked up in the target directory or
  one level above it.

### Preparation

Back up the cartridge list of the deployed 7.10 project. ICM 11+ derives the cartridge list from
declared dependencies, so the old list is the only thing to compare the generated one against.

### Running it

One commit per step, so any single step can be reverted on its own. Disable with `-PnoAutoCommit`.

```sh
# every step, every subproject
gradlew migration:migrateAll -Ptarget=<project> -Psteps=<steps> [-Preport=<dir>] [-PnoAutoCommit]

# one step, one cartridge or the whole project
gradlew migration:migrateOne -Ptask=project  -Ptarget=<project>/<cartridge> -Psteps=<step>
gradlew migration:migrateOne -Ptask=projects -Ptarget=<project>             -Psteps=<step>
```

Run the steps **one at a time** on a first migration, reviewing the report and `git show --stat` after
each. Step sets live in `migration/src/main/resources/migration/`.

### Reading the result

**Do not infer success from the absence of a stack trace.** The exit code carries it: `0` ok, `1` usage,
`2` bad project path, `3` git validation, `4` critical error during preparation, `5` the run happened
and at least one operation failed. The three cases that mean *nothing was changed* are deliberately
distinct from the one that means *something was changed and part of it did not work*.

Every run writes `operations.jsonl`, one JSON object per line, each operation attributed to its step and
each step carrying the commit it produced. Then ask what the run did not cover:

```sh
tools/inventory.py --project <project> --log build/migration-report/operations.jsonl
```

It reports, in order of signal: what is still under `staticfiles/` after the run, what was deleted and
looks worth rescuing (assembly projects routinely carry an acceptance suite), what a step met but did
not handle, and what no step mentioned at all.

`tools/build_rename_corpus.py` flattens every old-name-to-new-name mapping in the step data and the
recipes into one queryable file, tagged with the release each came from. `--check` fails if it has
fallen behind the step data.

## Earlier Copilot instructions

`docs/ai-instructions/` holds an earlier, GitHub Copilot oriented approach:
[migrating cartridges](docs/ai-instructions/migrate-all-cartridges-instructions.md) and
[checking dependency structure](docs/ai-instructions/dependencies-component-instances.md). A training
for it is available in the academy.

The `icm-migration` playbook above supersedes it for the migration itself. The dependency instructions
remain useful on their own after a migration.

## Third party libraries

This project reuses code from [GradleKotlinConverter](https://github.com/bernaferrari/GradleKotlinConverter),
licensed under the Apache License 2.0, adapted to fit this project. Comments in `applyConversions()` of
the Gradle Kotlin DSL converter identify all changes.
