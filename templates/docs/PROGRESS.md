# ICM 7.10 to <target> Migration: Progress Log

Companion to [ADR 0001](../adr/0001-migration-approach.md). The ADR holds the decisions; this file holds
what has actually happened and what is next.

| | |
|---|---|
| Branch | `<branch>` (off `<base>` at `<sha>`) |
| Last updated | `<date>` |
| Current phase | `<phase>` |
| Blocked on | `<nothing, or what>` |

---

## 0. Resume here

**Rewrite this section every time the situation changes. It is the first thing the next session reads,
and a stale one is worse than none.** It answers three questions and nothing else: what state is the
work in, what is the single next action, and what decisions are open.

### Verify the environment before trusting anything

```sh
ls /icm-as          # ICM platform sources, read-only, the authority on API questions
<the project's own smoke or build check>
```

### Traps that have cost this project real time

<Keep this list short and specific. Delete the heading if empty. An entry earns its place by having
cost an hour or produced a wrong conclusion.>

### The work that remains, in value order

1. <...>
2. <...>

### Where things live

| | |
|---|---|
| what happened, in order | section 3 |
| commands for a developer | section 5 |
| maintenance backlog | section 6 |
| migration tool backlog | section 7 |
| reusable how-to | the `icm-migration` skill |

## 1. Phase status

Phases follow the skill's Sequence section. Mark `[x]` only when something confirmed it, `[~]` when the
output is merely plausible, `[ ]` when not started, `[!]` when blocked.

| Phase | Scope | Status |
|---|---|---|
| 0 | prerequisites and safety net | `[ ]` |
| 1 | deterministic 7.10 to 11 layout, the 16 steps | `[ ]` |
| 2 | mechanical residue and assembly rehoming | `[ ]` |
| 3 | dependencies and components | `[ ]` |
| 4 | API cross-check with the OpenRewrite recipes | `[ ]` |
| 5 | remaining API deltas, compiler-driven | `[ ]` |
| 6 | runtime: DBPrepare, component wiring, server start | `[ ]` |

## 2. Baseline metrics

Measured before anything moves. These are the conservation invariants: a step that silently drops
content shows up here first, and until the build runs they are the only evidence available.

| Metric | Baseline |
|---|---:|
| Files in scope | |
| `.java` | |
| `.isml` | |
| `.pipeline` | |
| `.component` | |
| `.resource` | |
| Files under any `staticfiles/` | |
| Top-level `build.gradle` | |

## 3. Work log

Newest last. One entry per meaningful development, numbered `3.N`, each with a date and a heading that
states the **finding**, not the activity. "Q12: every Java error is gone, and a new class F appears"
beats "worked on compile errors".

Record what was wrong as well as what worked. A retracted conclusion, marked as retracted with the
reasoning error named, is worth more than a silently corrected one.

### 3.1 <finding> (<date>)

## 4. Working rules

Mirrors `CLAUDE.md`. Add rules here as they are learned, with the entry number that earned them.

## 5. Commands queued for the developer

<Delete this section if the agent can run the build itself.>

Gradle cannot run in the agent's container. Commands land here; paste the output back and the result
gets recorded in section 3. Give one exact command line, redirecting into `q<N>-<purpose>.log` in the
project root. State which entries can run in any order, because a queue entry that blocks the next one
costs a full human round trip.

### Queued now

### Results

## 6. Open items

**Scope line: the migration ends with a working server; the rest is maintenance.** That sorts this list,
and the test is not "is it worth doing" but "does it stop the server, and did the migration cause it".
Age is not the criterion: a pre-existing defect that the migration surfaced still belongs in scope.

| # | Item | Owner |
|---|---|---|

## 7. Changes to the migration tool

Everything here is **generic**, so it belongs upstream in
[icm-migration-support](https://github.com/intershop/icm-migration-support) rather than in this
repository. The test applied to each finding: *would any 7.10 project hit this?* If yes it goes in the
tool, as code or as step data, or in the skill if it is know-how rather than code. If it is a
project-specific judgement call it stays in section 6.

**Raise these as you find them.** Fixing one by hand here and meaning to contribute it later is how the
same defect gets fixed twice.

| # | Change | Kind | Raised | Found by |
|---|---|---|---|---|
