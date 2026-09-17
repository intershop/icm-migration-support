# The machine-readable interface

This tool's main consumer is an agent or a script that has to decide what a run did without reading
prose. Two things make that possible: an exit code that never lies, and a structured log of every
operation. The human-readable summary is generated from the same records, so the two cannot disagree.

## Exit codes

| code | meaning |
|---:|---|
| 0 | every step ran, no operation failed |
| 1 | usage error, unknown task, or an unexpected exception |
| 2 | the project path is not a directory |
| 3 | git validation failed, typically a dirty working tree, so nothing ran |
| 4 | a step reported a critical error during preparation, so nothing ran |
| 5 | the migration ran and at least one operation FAILED |

Distinct codes on purpose. "It failed" is not an actionable answer, and the three cases that mean
*nothing was changed* (2, 3, 4) are worth telling apart from the one that means *something was changed
and part of it did not work* (5).

**Never infer success from the absence of a stack trace.** An earlier version logged a git validation
failure and then exited 0, so a migration that never ran reported success.

## The operation log

Every run writes `operations.jsonl`, one JSON object per line, flushed as it happens so an aborted run
still leaves everything that happened before it. Default location is `build/migration-report/` inside
the tool, deliberately **not** inside the migrated project: auto-commit stages with `git add .` and
would otherwise sweep the report into the project's history. Override with `-Preport=<dir>`:

```
gradlew migration:migrateAll -Ptarget=$ICM -Psteps=<steps> -Preport=/tmp/report
```

Three record shapes share the file, distinguished by `event`:

```json
{"ts":"...","event":"step-start","stepIndex":0,"step":"020_MoveFolder","message":"refactor: move staticfiles to resources"}
{"ts":"...","event":"operation","stepIndex":0,"step":"020_MoveFolder","project":"mycartridge","type":"MOVE","status":"SUCCESS","source":"...","target":"...","message":null}
{"ts":"...","event":"step-end","stepIndex":0,"step":"020_MoveFolder","commit":"4b62878...","counts":{"SUCCESS":4,"SKIPPED":21,"UNKNOWN":0,"WARNING":0,"FAILED":0}}
```

A fourth, `critical-error`, carries a message only.

`type` is one of MOVE, DELETE, CREATE, MODIFY. `status` is one of SUCCESS, SKIPPED, UNKNOWN, WARNING,
FAILED. Records appear in the order the operations happened, so a run can be reconstructed from the
file alone.

### Why there are no per-file content hashes

The tool commits per step and `step-end` carries the commit. Byte identity of a move is therefore
already provable from git itself, with `git log --follow` or `git show --stat`, and duplicating it here
would add a second answer that can disagree with the first.

### Useful queries

```sh
REPORT=build/migration-report/operations.jsonl

# everything that failed, with the step that produced it
grep '"status":"FAILED"' $REPORT

# what a given step did
grep '"step":"020_MoveFolder"' $REPORT

# per-step totals
grep '"event":"step-end"' $REPORT

# anything the tool was unsure about: the review queue
grep '"status":"UNKNOWN"' $REPORT
```

`UNKNOWN` and `WARNING` are the rows worth reading every time. They are where the tool is telling you
it met something it does not handle, which is where a migration's risk lives.

## Asking what the tool did NOT do

The log records what the tool **did**. The more valuable report is the complement, and it is
`tools/inventory.py`:

```
tools/inventory.py --project <project> --log build/migration-report/operations.jsonl --json out.json
```

It derives everything from the log rather than re-implementing each step's matching rules, so it cannot
drift from what the tool actually does. It reports, in order of signal:

- **still under `staticfiles/` after the run**, taken from the migrated tree rather than from arithmetic
  on the log. `staticfiles` is a backward-compatibility path in ICM 11+, not a target location, so
  everything here is content no step knew how to move. Read this first.
- **deleted content worth a look**, because source and tests are routinely bundled into assembly
  projects that step 005 removes wholesale. On the reference project this listed 184 files, an entire
  Geb acceptance suite, which is exactly the content a migration wants rescued before the step set runs.
- **seen but not handled**: a step met the path and reported UNKNOWN, WARNING or FAILED. On the
  reference project one UNKNOWN directory covered roughly 900 files.
- **unclaimed**: no step mentioned it at all.
- **failed** and **unknown or warning** operations, grouped by step.

Two things it gets right that are easy to get wrong, both found by checking the output against the
filesystem rather than trusting it:

- steps that move a whole folder record the **directory**, not each file, so a file counts as covered
  when it or any ancestor appears in the log. Matching file paths alone reported 817 files still under
  `staticfiles/` when the true number was 16;
- a directory recorded as UNKNOWN has been *seen* but not *handled*, so its contents belong in the
  review queue rather than in the handled count.

The baseline is the commit before the first step commit, which the log records, so the run configures
this itself. Pass `--ref` when auto-commit was disabled.
