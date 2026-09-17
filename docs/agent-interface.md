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

## What is not covered yet

The log records what the tool **did**. It does not yet record what it **did not touch**, which is the
more valuable report: content no step claims is exactly what gets silently carried into a migrated
project and discovered months later. That is the `inventory` command, planned next.
