# Working method for the compiler-driven phases

**Provenance: this file is ours, not Intershop's.** It is the working method distilled from running
Phases 3 to 5 on a real customization, where the tool has stopped helping and the compiler is the only
oracle. Every rule below is here because breaking it cost a round trip; several were learned twice.

The companion data file is `icm-14-api-deltas.md`. This file is the *how*; that one is the *what*.

---

## Mount the platform sources and treat them as the authority

The single highest-leverage change to how this work goes. Mount the ICM sources read-only
(`- /path/to/icm-as:/icm-as:ro` in a gitignored compose file) and answer every API question by reading
them, never by inferring from compiler output.

The compiler tells you a call is wrong. It does not tell you what replaced it, whether the replacement
is semantically identical, or why the change was made, and the "reason:" line is sometimes actively
misleading: `cannot infer type arguments` turned out to mean a constructor had become `protected` and
grown two parameters. Reading the source answers all of it in one pass.

Constraints that matter in practice:

- **Never walk the whole tree.** `du -sh` and unbounded `grep -r` over the mount exceed a typical tool
  timeout. Scope to a cartridge directory (`/icm-as/platform/ac_oidc`) or use `find` with `-path
  '*/build/*' -prune`.
- **Know the four groups, because scoping depends on them.** The merged tree is split into
  `platform/`, `business/`, `content/` and `b2b/`, which are what ICM 7.10's component sets
  (`p_platform`, `f_business`, `f_content`) became when 11+ merged them into one repository to stop
  version bubbling. A cartridge lives under exactly one, and `platform/` holds most of them, so it is
  the first place to look and `/icm-as/<group>/<cartridge>` is the right scope for almost any question.
  If the **7.10** sources are also mounted, they are still separate checkouts, one per set, so a search
  there must cover all of them or it silently answers for only part of the platform.
- **Exclude `*/build/*` and `*/bin/*`**, which hold stale duplicates of `src/` and will happily answer
  a question with last release's code.
- **The Gradle cache is the second authority.** Mount it too. It answers "what does this artifact
  actually contain" questions that sources cannot, by listing a jar. It is how the `@PATCH` question
  was settled. Its limit: it holds what *this* container resolved, which may lag what the build
  resolves, so check the version before trusting it.

## Read ICM's own caller before writing yours

When an interface gains a parameter, the platform's own implementation and its own call sites show
what to pass, what the parameter means, and whether null is legal. Copy that, do not invent it.

This caught a real bug that reasoning alone had produced. The obvious fix for a new
`VariationInformationBO` parameter was to pass `product.getProductBOMaster().getProductVariationInformationBO()`.
ICM's equivalent caller passes the same expression **guarded by a ternary**, because a product master
has no master and the unguarded version NPEs. Nothing in the compiler output could have revealed that;
thirty seconds of reading did.

## A "customization" is often platform code with a local edit

Before deciding what to do with a customized class, check whether the method you are about to redesign
is in fact a copy of the platform's. Customization cartridges routinely copy a whole platform class to
change three lines.

Twice, a helper that looked like project-specific cleverness turned out to be ICM's own code, still
present in 14.4, changed there in exactly the way the interface had changed. The right move both times
was to mirror the platform's new version, not to design something. Diff the customized class against
its platform original early; it reframes the whole task.

Corollary: if the platform kept a workaround you were about to delete as redundant, it kept it for a
reason, and the reason is usually in a javadoc a few lines up.

## Adopt the shape, do not null it out

When the platform adds a parameter, passing `null` or a placeholder usually compiles and usually is
wrong. It silently disables whatever feature the parameter was added for, and the failure appears much
later, to someone else, as behaviour rather than as an error.

The test: derive the value the way the platform derives it, and check what happens when the underlying
configuration is absent. In the one case where the derivation was replicated rather than nulled, the
platform helper returns null when its property is unset, so today's behaviour was identical to passing
null *and* the feature works if the property is ever set. Twelve lines, no behaviour change, no trap.

Reserve `null` for when the platform's own caller passes null.

## Sweep before fixing, and fix every instance

**Learned three times, which is the reason it is stated this bluntly.** A build failure names the
cartridges whose tasks happened to run, not the extent of the defect. `--continue` does not save you:
a cartridge that fails to resolve never compiles, so its copy of the same defect is invisible.

So: once a cause is identified, grep the whole repository for it, count the instances, and fix all of
them in one commit. A fix scoped to the failing cartridge buys exactly one more build round trip. The
misses ran 3 cartridges when the real count was 4 and 5, and then one cartridge when it was two.

## Check whether a decision is really a decision

Several things that looked like judgement calls needing the customer's input dissolved on being
followed one level down. A page-size argument was parked as "differs in paging behaviour, the project
should pick"; the method it is passed to overwrites the page size before reading an element, so the
choice is behaviour-neutral. An open question about deleting a helper was answered by a javadoc.

Before parking a question, spend five minutes following the value to where it is consumed. Park it only
if the consumer genuinely branches on it. Real decisions do exist and belong to the customer, security
sensitive rewrites especially, but they are rarer than a first pass suggests, and every false one costs
a round trip through someone else's calendar.

## An empty grep over the mount is not evidence of absence

`grep -r` over a large read-only mount can exit with **status 2**, a grep error, when it meets a path it
cannot read. The walk aborts. With `2>/dev/null` and a pipe to `head`, which is how these searches are
usually written, that failure is completely invisible and looks exactly like a clean "no match".

This cost a wrong conclusion that was two sentences from being written down: four searches for the
consumer of a configuration file format all came back empty, and the emerging answer was "nothing in
14.4 reads this format any more". Re-running the same search as

```bash
find . -name '*.java' -not -path '*/build/*' -print0 | xargs -0 grep -l "<pattern>"
```

found two consumers immediately, in a cartridge set the earlier search had supposedly covered.

So: **check the exit status** (0 found, 1 no match, 2 error) before treating an empty result as a
finding, and prefer `find -print0 | xargs -0 grep` on the mount. The same applies to the counting checks
elsewhere in this file: a conservation count built on a silently truncated walk is worse than no count,
because it looks like evidence.

### The second mechanism, and the one that recurred: `timeout` runs BusyBox `grep`

The same migration later produced a second empty-result-that-was-not-empty, with a different cause, and
this one is worth checking for by name because an agent container makes it likely.

Wrapping `grep` in `timeout` can silently change which `grep` runs. In a BusyBox-based container
`timeout` is a BusyBox applet, and it invokes BusyBox's **internal** `grep` rather than whatever `grep`
resolves to on `PATH`. BusyBox grep has no `--include`, so

```sh
timeout 100 grep -r --include='*.java' PATTERN /icm-as
```

prints nothing and exits 2, which is indistinguishable at a glance from a clean search that found
nothing. Confirm which binaries are in play before trusting any timed search:

```sh
timeout 10 grep --version    # BusyBox v1.37.0 ... unrecognized option
grep --version               # ugrep 7.8.4
```

This produced a written-up "security hole" that had to be retracted: the pattern was present in the
platform sources the whole time. It then bit a second time, on a scan for a component namespace, and
was caught only because a positive control was run alongside it.

So, the rule that survives both mechanisms:

**A negative grep is evidence only once its control passes.** Pair every search expected to return
nothing with a search of the same shape, over the same tree, that *must* return hits. If the control
comes back empty, the search apparatus is broken and the negative result means nothing. This costs one
extra command and is the only thing that distinguishes "absent" from "not searched".

Never wrap `grep` in `timeout` to guard against a slow mount. Narrow the tree instead: exclude
`*/build/*` and `*/bin/*`, which hold stale duplicates of `src/`, and drive the search from `find`.

## Run the control before believing a single-project result

A report from one project has no baseline, and some reports cannot fail in a way you would recognise.
Before treating output as a finding, run the identical command against a project known to be healthy
and compare. It is one command and requires no thinking.

The case that earned this its own section: `:ft_production:dependencies --configuration cartridge`
returned two `FAILED` coordinates under a `BUILD SUCCESSFUL`. Read alone it looked like a latent
packaging defect, and two rounds of analysis followed, one of them proposing a fix to a build script
that was working correctly. The control run took a minute: the same report against a cartridge that
compiles cleanly returns **72** `FAILED` lines. The configuration is simply not resolvable standalone.
There was no defect, and three developer round trips had been spent on it.

Two general shapes to watch for, both present there:

- **A command that cannot fail.** `dependencies` and `dependencyInsight` exit zero while reporting
  failures. So does a dependency report with an unresolvable graph. Exit codes carry no signal in these,
  which makes the baseline the only signal available.
- **A property of the question mistaken for a property of the subject.** If the answer would be the
  same for every project, it says nothing about the one you asked about. Asking a second project is the
  cheapest way to find out which kind of answer you have.

Corollary for the write-up: when a finding is retracted, mark the original entry retracted rather than
editing it into correctness, and say what the reasoning error was. "I read a single-project result
without a baseline" is reusable; a silently corrected entry teaches nothing and invites the same
mistake.

## Verification, when Gradle is someone else's job

Under the usual constraint that the agent cannot run Gradle, the discipline is:

- **Never report a step done on a zero exit code.** Either a static check confirms it or a build does.
- **Static checks that are worth the keystrokes:** every `@Override` signature compared argument for
  argument against the interface in the mounted sources; a comment-stripped scan for surviving
  old-arity calls, since a stale call inside a `/* */` block is not an error and will otherwise waste a
  grep; and a repository-wide sweep for other implementors of any interface you changed.
- **Queue build commands with their output redirected to a log file in the project root**, gitignored,
  so the log can be read back in full rather than pasted through a terminal. Predict the outcome in
  writing before the run; a wrong prediction is the cheapest diagnostic available, and on this project
  the one wrong prediction in seven diagnosed itself.
- **Logs are not durable.** One was truncated to zero bytes by a machine restart. Record each run's
  findings in prose in the progress log, not by reference to a log file.

## Leave a handoff that survives a restart

Long migrations get interrupted. A handoff that names the next action, the state of the tree, the
decisions that are parked and the environment facts needed to resume is worth the ten minutes,
and writing one is also the cheapest way to notice that a "finished" analysis has a hole in it.

Record what was *wrong* in previous entries as well as what was right, and mark a superseded entry
rather than editing it into correctness. The record of which reasoning failed, and how it was caught,
is what makes the next project faster; a log that only records successes teaches nothing.
