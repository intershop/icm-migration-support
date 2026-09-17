# ADR 0001: Enhance this tool rather than replace it with an agent

Status: accepted, 2026-09-17

## Context

This tool was built with human actors in mind. A companion project,
`icm-migration-copilot-support`, was built for an earlier and less capable generation of AI agent. By
2026 neither assumption holds: an agent can read a 7.10 project, write the transformations itself, and
carry them out without a step engine.

So the question was put directly, during a completed 7.10 to 14.4 migration run by an agent:

> Do we really need the tool? You could do everything better without it, couldn't you? Write some Python
> scripts to move files, do text replacements in whatever way. Honest answer please.

It is a fair question, and the easy answer is yes. The tool had just been shown to be mediocre. One
migration produced seven defects worth fixing, three of which corrupted output: a converter that
recorded failed conversions as successes, a configuration parser that crashed a whole run on an `=`
inside a value, and a build-script converter that emitted invalid Groovy. Its step data was incomplete
in four separate places, including six of the nine renames listed in Intershop's own migration document.
Nine further findings were all the same shape: the tool is silent about content it does not recognise.

## Decision

**Keep the tool and invest in it, and reshape it so that an agent is its primary consumer.**

## Rationale

Three reasons, in order of weight. The first is the one that decided it.

### 1. The agent is the unreliable component, not the tool

In the course of that single migration the agent wrote up a security hole that did not exist and had to
retract it, misdiagnosed an environment trap and left the wrong explanation in the project's
instructions for two days, proposed a fix to a build script that was working correctly, and had two
points corrected by the platform sources after being written up as ready to apply. Later, while
measuring step idempotency, it reported two steps as idempotent when they had in fact crashed on startup
and done nothing.

Every one of those was caught by something outside the agent: a control run, the shipped sources, a
developer pasting back a log, an exit code that did not match the story. **Bespoke scripts written fresh
per project remove exactly those external checks from the phase that has the fewest of them.**

### 2. Phase 1 has no oracle, and the tool is the only one available

Later phases are arbitrated by a compiler: a mistake surfaces as an error. The 7.10 to 11 file moves have
nothing equivalent. Around 850 file operations happen, and the only evidence they were correct is
conservation counts. **A count produced by a script and checked by the same script is not a check.**

The tool's per-step auto-commit and per-file SUCCESS / SKIPPED / UNKNOWN / WARNING / FAILED report are
the only external verification that phase has. That is a property of the step engine, not of the
transformations, and it is what an ad-hoc script cannot replicate by being written more carefully.

### 3. Fixes compound here and die in a branch anywhere else

Over twenty projects are expected to make this migration. Bespoke scripts make project seven's migration
incomparable to project two's, and six months later "what did the migration do to this file" has no
answer beyond one large commit. One migration produced nineteen improvements to this tool; applied here,
every later project gets them for free.

That asymmetry only pays if fixes are actually contributed, which is the condition attached below. It is
not hypothetical: the same `split("=")` defect was found and fixed twice, months apart, because the
first fix was made in a clone nobody else could see.

A fourth, weaker reason: Intershop owns the definition of what a migrated project should look like. A
repository migrated by Intershop's own tool with a clean step-by-step history is defensible to support
and to the customer in a way that "the AI wrote some Python" is not, particularly on the day something
goes wrong.

## The counter-argument, and why it does not flip the decision

The 13 to 14 step has no tool, no step set and no recipes, and it went fine: nine classes of API error,
all found and fixed by the agent. That is real evidence for replacing the tool.

It does not generalise, because **the compiler was the oracle**. Every mistake surfaced as an error. The
file-move phase has no such feedback, and that is precisely where the step engine earns its place.

## Consequences

**The tool's value to an agent is verification, not transformation.** The agent is faster at writing a
move script than this tool is at being fixed. It cannot check its own work. So investment goes into
observability rather than new transformations: a structured operation log, exit codes that distinguish
every outcome, a report of what no step claimed, and executable invariants. That is the plan the
AI-first work follows.

**Observable, not autonomous.** The tool must never decide whether a phase succeeded. The earlier
AI-facing companion did exactly that, deciding phase outcomes from exit codes and logging
`Phase completed successfully` for a phase whose own agent output said migration could not proceed. The
tool reports; the agent decides; a person can see both.

**Fixes go into the tool, not around it.** This is a condition of the decision rather than an
aspiration. If a defect is patched by hand in a customer project and contributed later, it will not be
contributed. Three known step-data gaps were handled that way during the reference migration and had to
be applied again afterwards.

**The playbook ships with the tool.** The judgement half of a migration is documented in
`skills/icm-migration`, in this repository rather than beside it, because it makes claims about specific
code here and would rot the day that code changed.

## What would reverse this

Stated so the decision can be tested rather than defended:

- if the step engine could not be made to produce evidence an agent can verify against. It now does, so
  this has been answered rather than assumed;
- if the deterministic phase acquired an independent oracle, so that the step engine stopped being the
  only external check on roughly 850 file operations;
- if maintaining the tool cost more than the round trips it saves. Measured so far it does not: the
  contributed fixes removed twenty spurious failures from a single real run and made the `javax.inject`
  conversion automatic, which had been hand work across 54 files.

## Evidence since the decision

Both directions have held. The agent found four defects in the tool that no test covered, including one
where a failed git validation exited zero and reported success. The tool found drift in the agent's own
output, when a generated corpus check failed the moment step data changed underneath it.
