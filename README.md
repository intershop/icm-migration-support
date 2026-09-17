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
  versions with the code it describes. It is markdown, not a product feature, so any agent can use it.

The tool is built to be driven by an agent and checked by a person. Its output is JSON first and prose
second, its exit codes distinguish every outcome, and it reports what it did **not** handle as well as
what it did, because unrecognised content is where a migration's risk lives.

## Start here

**1. Get a container with everything the migration needs.** `templates/container/` builds one: JDK 21,
Kotlin, Python, git, jq, ripgrep, and your agent's CLI. One image, one service per agent.

```sh
docker compose run --rm claude      # or: copilot
```

`skills/icm-migration/references/agent-environment.md` grades every mount, tool and credential by what
its absence actually costs. Read it before the first command: one item on it, the artifact feed
credential, decides whether the agent can build at all, and it is the difference between a migration
measured in weeks and one measured in months.

**2. Get the playbook.** How depends on the agent, because the content is the same either way.

- **Claude Code** installs it as a plugin, which keeps it updated:

  ```sh
  claude plugin marketplace add intershop/icm-migration-support \
      --sparse .claude-plugin skills templates
  claude plugin install icm-migration
  ```

  The marketplace is read from this repository's **default branch**. If the playbook has not merged
  there yet, name the branch that carries it, `intershop/icm-migration-support@<branch>`, otherwise the
  add fails with `Marketplace file not found`.

- **GitHub Copilot**, and any other agent, reads it as files. In the container this repository is
  mounted, so start at **`/icm-migration-support/skills/icm-migration/SKILL.md`**, which links the rest.
  Outside a container, clone this repository and read it from there.

**3. Copy the starting kit** from `templates/` into the project being migrated, then follow the
playbook. The kit includes an entry point for each agent: `AGENTS.md` holds the working agreements, and
`CLAUDE.md` and `.github/copilot-instructions.md` are short redirects to it, so the rules cannot differ
depending on who is working.

| | |
|---|---|
| [the playbook](skills/icm-migration/SKILL.md) | the whole sequence, the preflight, and twelve references |
| [starting kit](templates/README.md) | what to copy into a project on day one |
| [machine-readable interface](docs/agent-interface.md) | exit codes, the operation log, and `inventory` |
| [7.10 to 11 steps](docs/migration-7.10-11.md) | what each automated step does, and its manual follow-up |
| [API cross-check](docs/api-crosscheck.md) | the OpenRewrite recipe set, and why it is a check rather than a step |
| [ADR 0001](docs/adr/0001-enhance-the-tool-rather-than-replace-it.md) | why there is still a step engine at all, now that an agent could write the transformations itself |

## The route

**7.10 straight to the current release.** ICM 11, 12 and 13 are no longer deployment targets, so there
is no reason to stop at them: compiling against the target surfaces the union of every intermediate API
delta at once. The recipe sets that used to be per-version steps are merged into a single
[cross-check](docs/api-crosscheck.md), where no change confirms the hand fixes landed where Intershop
intends and any change is a finding.

## Driving the tool

Reference for what the tool accepts and returns. **Which step to run, in what order and at what scope,
is the playbook's job, not this page's**: it is a migration decision that depends on the project, and
duplicating it here would only let the two drift apart. Normally the agent drives this directly. Where
it cannot, usually because Gradle has no artifact feed credentials, a person runs the same commands and
pastes the output back.

### Prerequisites

- **Java 21.** An appropriate JDK installed and configured.
- **Kotlin.** One step shells out to a Kotlin script, so the compiler must be on the path. On Windows it
  is invoked as `kotlin.bat` literally, without `PATHEXT`, so verify with `where kotlin.bat` rather than
  assuming a working `kotlin` command is enough.
- **A git repository.** The project must be checked out, deployed locally, and have a **clean working
  tree**; the tool refuses to run otherwise. The `.git` folder is looked up in the target directory or
  one level above it.

### Commands

One commit per step, so any single step can be reverted on its own. Disable with `-PnoAutoCommit`.

```sh
# every step, every subproject
gradlew migration:migrateAll -Ptarget=<project> -Psteps=<steps> [-Preport=<dir>] [-PnoAutoCommit]

# one step, one cartridge or the whole project
gradlew migration:migrateOne -Ptask=project  -Ptarget=<project>/<cartridge> -Psteps=<step>
gradlew migration:migrateOne -Ptask=projects -Ptarget=<project>             -Psteps=<step>
```

Step sets live in `migration/src/main/resources/migration/`. `-Psteps` takes either a folder or a single
step file, which is how a step is run on its own.

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

## Third party libraries

This project reuses code from [GradleKotlinConverter](https://github.com/bernaferrari/GradleKotlinConverter),
licensed under the Apache License 2.0, adapted to fit this project. Comments in `applyConversions()` of
the Gradle Kotlin DSL converter identify all changes.
