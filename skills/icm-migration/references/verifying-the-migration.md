# Verifying the migration

**Ours.** Written from a completed 7.10 to 14.4 migration, at the point where the server started
cleanly and the honest question became "so what does that prove".

The scope line says migration ends with a working server. That line is worth defending, but it has a
hole in it: **a server that starts is not a server that behaves.** This file is how to close the hole
without inventing a test suite from nothing.

Read it early, not at the end. The plan below rests on one asset that a 7.10 migration essentially
always has, and the only question is how fast you get hold of it.

## 1. Start by admitting what a green build proves

Three facts hold on essentially every 7.10 customization project, and they compound.

- **There is no safety net.** Count the test tasks before assuming otherwise. On the reference project
  **31 of 35 `compileTestJava` tasks were NO-SOURCE**. The build checked compilation and nothing else.
- **A green build and a running server prove very little.** That same migration produced three defects
  that passed both, and each was invisible until somebody read a log closely:
  - a DBPrepare descriptor rejected for a duplicate key, which silently skipped **a whole cartridge's**
    database preparation while the run reported success;
  - three `*.component` overrides inert since the ICM 11 namespace rename, so the platform's own
    implementations stayed in place, custom REST authentication among them;
  - a pipelet that resolved to a placeholder because its cartridge lacked the resourcelist plugin, so
    the pipeline kept running with a hole in it.
  Note the shape they share: **the failure mode of ICM 11+ is silence, not a stack trace.** Wiring that
  does not match is simply not applied.
- **The real question is comparative.** "Did the migration succeed" means "does it still do what 7.10
  did", and nothing in the repository records what 7.10 did. There is nothing to assert against.

That last fact decides the whole concept. Writing assertions from scratch means inventing the expected
behaviour, which is slow and guesses at exactly the details most likely to have drifted.

## 2. The principle

**Cheap and broad before precise and narrow, and prefer an oracle over an assertion.**

Every hour spent writing expected values is an hour not spent comparing against a system that already
knows the answer. The old system is that oracle, and for a migration it is a better one than any test
an engineer would write, because it encodes the behaviour the customer actually has rather than the
behaviour someone believes they have.

## 3. Audit the assets before planning

One pass, an hour at most, and it reorders the plan more often than not:

| asset | what to check |
|---|---|
| an existing acceptance suite | does its test data target **this customer's** channels, or the Intershop demo? |
| HTTP reach from the agent container | can it reach the running server? If yes, probes and diffs need no extra tooling |
| the REST surface | enumerable statically from `*.component`: count the resources and how many are overrides |
| structured logs | every preparation step, every override, every failure, greppable |
| the running 7.10 installation and its database | **assume this exists**, see section 6. It is the oracle the whole plan rests on |

**Expect the inherited acceptance suite not to be the fast path.** On the reference project the Geb
suite was 14 specs of genuine B2B flows, wired and compiling, and unusable as it stood: its
`TestData.properties` targeted `inSPIRED-inTRONICS_Business-Site`, the Intershop demo, and `inSPIRED`
appeared **zero** times in the DBPrepare logs. Retargeting it is not a rename, because the file is full
of demo-specific site, user and product identifiers. Valuable, but not first. Check this before
budgeting for it.

## 4. Tier 0: smoke, same day

One script that requests every registered REST resource root plus a handful of key pages, and asserts
status codes and coarse shape. No framework, no fixtures, runs from the agent container or any laptop.
`scripts/icm_client.py` in this skill is the plumbing, including the backoffice form login; only the
expectations are project-specific.

It catches the entire class of failure a migration keeps producing: a cartridge that did not load, a
resource that is not registered, a component that did not wire, a pipeline that will not build. Exactly
the faults a green build hides.

It does **not** catch wrong data, and it will happily pass a placeholder pipelet returning an empty
result. Smoke is necessary, never sufficient, and should not be quoted as evidence of correctness.

Two rules that make it worth having:

- **Prefer asserting on data over asserting on a log line.** That a preparation step *created the
  service configuration*, checked by logging into the backoffice and reading it back, is a stronger
  claim than that the step logged `Success`. Both of the log-looked-fine defects above are caught this
  way and not otherwise.
- **Run the negative controls.** Point it at a wrong password and at an unreachable host, and confirm
  it exits non-zero. A check that cannot fail is worth nothing, and a smoke script that silently
  degrades to passing is worse than none.

Cost: hours.

## 5. Tier 1: differential REST against the old system, the main event

**Do not write assertions. Capture 7.10's answers and diff.**

1. Point a recorder at a reachable 7.10 environment and replay a corpus of REST requests.
2. Replay the identical corpus against the migrated server.
3. Normalise what is legitimately allowed to differ: timestamps, session and request identifiers,
   absolute URLs containing the host, and collection ordering where the API does not guarantee it.
4. Report per endpoint: identical, differs, or error.

This converts "we have no tests" into "we have a regression oracle" without anyone authoring a single
expected value, and it covers the customised surface precisely because the corpus comes from real usage
rather than from imagination.

Corpus sources, best first:

- **production access logs**, a day of real REST traffic, deduplicated by path template. By far the
  best source and worth asking for early, since it usually needs someone else's approval.
- the static resource inventory from `*.component`, which guarantees coverage of the custom resources
  including any overrides revived during the migration.
- hand-written flows for the money paths: basket, checkout, pricing, availability.

Two honest limitations, worth stating to the customer up front. Authenticated and stateful endpoints
need a seeded user and comparable data on both sides, so start with anonymous and catalogue reads and
add authentication second. And a diff tells you something changed, not whether the change is wrong;
every difference still needs a human verdict once.

Cost: days. Payoff: the only real evidence available that behaviour is preserved.

## 6. The old system is the oracle, and it is always there

**Every 7.10 migration has a working installation behind it, and normally its database too.** Migrating
from nothing is not a real scenario. So do not plan as though the oracle might not exist: assume it
does, ask for a dump on day one, and treat a refusal as the exception that needs escalating rather than
the default to design around.

That single fact is what makes Tier 1 the plan rather than an aspiration. Only one of the two things the
dump buys is testing, and it is the less important one.

### Use 1: the oracle

Restore it into a 7.10 instance and Tier 1 becomes possible as written: the same data on both sides, so
a diff is meaningful rather than noise. **Without matched data a diff is worthless**, because every
product, price and customer differs and the report comes back 100% red.

If standing up a 7.10 application server is impractical, there is a weaker fallback: query the dump
directly with SQL for the facts the REST responses should reflect, such as prices, availability and
organisation attributes, and assert against those. Narrower and more work per case, but it needs no
running 7.10.

### Use 2: the cutover rehearsal, which matters more

**Production will never be a fresh database.** It will be this dump, restored and migrated. Restoring
it into a 14.x environment and running DBPrepare in migration mode is therefore not merely a test, it
is a rehearsal of the cutover, and the only way to find out whether the cutover works before the night
it happens.

It exercises what no fresh install ever touches:

- the `dbmigrate` steps, which a fresh database skips entirely
- **any step moved to `post.` to fix the cartridge-ordering problem** (see SKILL.md). These re-execute
  on a restored database, because `post.ClassNN` has never been recorded as done under that name. The
  preparers involved are usually idempotent and the reference project analysed them as safe, but
  analysis is not evidence, and this is how the evidence is obtained.
- real data volume, real edge cases, and the actual runtime of the migration window
- whatever the accumulated migration descriptors do to data that has been through years of production

Ranking, plainly: a clean-database run proves the fresh-install path, which matters for new developer
environments and for INT. **The dump rehearsal proves the path production will take.** If only one can
be done, do the rehearsal.

## 7. Tier 2: storefront, blocked on an architecture decision

Ask this before spending anything here: **is the target storefront ISML or PWA?** A migrated 7.10
cartridge list frequently contains both worlds, so the repository does not answer it.

- **If ISML:** revive the acceptance suite, which means retargeting it off the demo data or deploying
  demo data alongside the customer's.
- **If PWA:** the inherited Geb suite is a museum piece. The storefront is a JavaScript application with
  its own suite running against the REST API, so Tier 1 already covers the server side and the right
  investment is making Tier 1 thorough.

Answering costs one conversation and decides whether a week of work is useful or wasted.

## 8. Tier 3: characterisation tests for what the migration changed

Narrow unit tests around the code the migration actually touched, as maintenance rather than a gate:
any patch cartridge, whose whole purpose is to shadow platform behaviour and whose mechanism is
position-dependent and unreported; any component override revived during the migration; and any class
reworked to follow an API delta. Small, cheap, and they protect exactly the changes no inherited test
covers.

## 9. The four questions to ask early

Every one of these has a lead time measured in someone else's calendar, and three of them reorder the
plan:

1. **How do we get a dump of the 7.10 database, and who approves it?** Not whether: a working 7.10
   installation is a given. This is a procurement and data-protection question, so start it on day one
   and expect the approval, not the copy, to be the slow part. Production data usually needs scrubbing
   or a restricted environment before anyone may hold it.
2. **ISML or PWA?** Decides Tier 2 entirely.
3. **Can we get production REST access logs?** Turns the Tier 1 corpus from guesswork into coverage.
4. **Is demo data meant to exist in dev?** Decides whether an inherited acceptance suite is revivable as
   written or needs retargeting.

## 10. Fast path

| when | what | needs |
|---|---|---|
| day 1 | Tier 0 smoke running, URL list checked in, negative controls run | a running server |
| day 1 | answers to the four questions | one conversation |
| days 2 to 3 | Tier 1 harness: record, replay, normalise, report | 7.10 restored from the dump |
| days 2 to 3 | **cutover rehearsal: restore the dump into 14.x, DBPrepare in migration mode** | the dump |
| day 3 | first full diff report, triage the differences | the corpus |
| later | Tier 2 per the architecture decision, Tier 3 alongside | |

The honest summary to give a customer: **Tier 0 says the server is wired correctly, only Tier 1 says the
migration preserved behaviour, and only the rehearsal says the cutover will work.**
