# ADR 0001: ICM 7.10 to <target> migration approach

Status: <proposed / accepted>, <date>

## Context

<The starting state in one paragraph: ICM version, cartridge count, what the customization does, what
forces the migration now.>

## Decision

Use [icm-migration-support](https://github.com/intershop/icm-migration-support) for every deterministic
transformation and an agent for the judgement work, per the `icm-migration` skill. Do not hand-migrate
what the tool already encodes: that loses reproducibility and a vendor-aligned baseline for no gain.

### Route

**7.10 straight to <target>.** ICM 11, 12 and 13 are not deployment targets, so there is no reason to
stop at them. Compiling against the target surfaces the union of every intermediate API delta at once.
The OpenRewrite recipe set is run afterwards as a **cross-check**, where no change confirms the
compiler-driven fixes landed where Intershop intends and any change is a finding.

### Scope line

**The migration ends with a working server. Everything after that is maintenance.** A 7.10 tree offers
endless things worth improving and each one looks small. Fix what stops the server, record the rest with
enough detail that someone can pick it up, and hand it over.

## Project decisions

These are the ones a migration cannot make for itself. Record the answer and the date.

| # | Question | Decision |
|---|---|---|
| 1 | Target release, and does it move during the project? | |
| 2 | Storefront: ISML or PWA? Decides whether an inherited acceptance suite is worth reviving | |
| 3 | Is a 7.10 database dump obtainable, and who approves it? Decides whether differential testing is possible | |
| 4 | Naming for any new cartridges | |
| 5 | Who owns the production cartridge list and its ordering | |

## Consequences

<What this commits the project to, and what it defers.>
