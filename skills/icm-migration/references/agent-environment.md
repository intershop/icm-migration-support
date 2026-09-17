# What an agent needs to migrate an ICM project

**Ours.** Written after a complete 7.10 to 14.4 migration run by an agent in a container, grading each
item by what its absence actually cost. It is a graded list, not a wish list: two of these change the
shape of the project, and several things that sound important are not needed at all.

The short version: **give the agent a JDK, Gradle and the artifact feed credentials, and mount the
platform sources read-only.** Those two decisions matter more than everything else combined.

## Tier 0: without these there is no agent work

| | why |
|---|---|
| the project, read-write, in a container | the agent edits it |
| `git`, with `user.name` and `user.email` set in the repo | the migration tool commits through JGit and fails without an identity |
| Python 3 | the tooling around the migration is Python: `inventory.py`, the corpus builder, the duplicate-key scanner, the HTTP client |
| network to the artifact feed and to GitHub | nothing resolves otherwise |

A container is the right shape, and not only for isolation. It lets you hand the agent broad permission
to use its tools without being asked to approve each command, which is where most of the speed comes
from.

## Tier 1: omit these and the project pays in weeks

### 1. Gradle, and the credentials to use it. This is the big one.

**The single largest constraint on the reference migration was that the agent could not run Gradle on
the project.** Every compile became a queued command for a developer to paste back: about forty of them
over the migration, each one a round trip measured in hours rather than seconds. The playbook's whole
"queue commands for a developer" protocol exists to cope with this, and it is a workaround, not a
design.

**The blocker was not Gradle.** A JDK and the wrapper were present and worked fine on other repositories
in the same container. What was missing was **authentication to the artifact feed**. An ICM project
resolves its platform from a private Maven repository, and the build reads the credentials as ordinary
Gradle properties:

```kotlin
maven {
    url = uri("https://pkgs.dev.azure.com/<org>/<project>/_packaging/<feed>/maven/v1")
    credentials { username = repoUser; password = repoPassword }
}
```

So supplying them is two environment variables, because Gradle maps `ORG_GRADLE_PROJECT_x` onto the
property `x`:

```yaml
environment:
  - ORG_GRADLE_PROJECT_repoUser=${REPO_USER}
  - ORG_GRADLE_PROJECT_repoPassword=${REPO_TOKEN}
```

Fed from a gitignored `env_file`, never written into the image, the compose file or the repository.
**Use a read-only feed token scoped to package read, not a personal password**, and rotate it on the
same schedule as any other CI credential. If your organisation cannot put a feed token in a developer's
container, that is a legitimate constraint, and the answer is the queued-command protocol; but decide it
deliberately rather than discovering it on day three.

Verify with the cheapest possible command before trusting it, because a feed failure looks like a
hundred other things:

```sh
gradlew --quiet :ft_production:dependencies --configuration cartridgeRuntime > /dev/null && echo ok
```

### 2. The platform sources for the target release, read-only

```yaml
- <path>/icm-as:/icm-as:ro
```

The highest-leverage practice in the whole playbook, for the reasons in
`compiler-driven-migration.md`. The compiler says a call is wrong; it does not say what replaced it, and
it occasionally misdiagnoses outright. Intershop always has these and partners usually do, handed over
as a plain file tree, so this is an ask rather than a build. Match the version to the target release.

### 3. The Gradle cache, read-WRITE, in its own directory

```yaml
- <path>/gradle_docker:/gradle-home        # note: NOT :ro
environment:
  - GRADLE_USER_HOME=/gradle-home
```

Two mistakes to avoid. **It must be writable**: Gradle writes its caches and lock files there, and a
read-only mount fails in ways that read like network errors. And **give the container its own
directory** rather than sharing the developer's `~/.gradle`: concurrent use causes lock contention, and
on Windows the path semantics differ between host and container.

It doubles as the second authority on API questions. It answers "what does this artifact actually
contain" by listing a jar, which sources cannot.

### 4. Kotlin on the PATH

Step 070 shells out to a Kotlin script. Without it that step fails once per cartridge: 36 failed
operations on the reference project, every one of them noise. On Windows it is invoked as `kotlin.bat`
literally, without `PATHEXT`, so a working `kotlin` command proves nothing; check `where kotlin.bat`.

## Tier 2: high value, pays for itself quickly

### 5. HTTPS reach to a running ICM instance

Once Phase 6 produces a server, this is the fastest verification loop in the migration and it produces
better evidence than log reading. That a preparation step *created the service configuration*, read back
from the backoffice, beats the step logging `Success`. The reference migration had **two defects where
the log looked fine**. Certificates are self-signed on dev instances, so verification is off and it must
never point at production.

### 6. The migration tool clone, read-write, mounted outside the project

```yaml
- <path>/icm-migration-support:/icm-migration-support:rw
```

Read-write because the point is to fix the tool as you go rather than working around it. **Outside the
project directory, never under it**: the ICM 11+ `settings.gradle.kts` makes a Gradle project of every
top-level directory without checking for a build script, so a clone inside the project becomes a phantom
cartridge that fails the product build with a missing cartridge descriptor. That cost the reference
migration three wrong diagnoses.

### 7. The ICM 7.10 platform sources, read-only

```yaml
- <path>/icm-7.10:/icm-7.10:ro     # the parent holding the component sets, see below
```

Not obvious, and worth it. The target-release sources tell you what exists now. The 7.10 sources tell
you what the customization was written against, and there are two questions only they answer.

**What a removed concept used to do.** The expensive deltas are not renamed methods, which the compiler
points straight at. They are deleted concepts, where the compiler reports a missing method and cannot
tell you what idea went with it. `FileUtils.getShareDirectory()` disappearing because `IS_SHARE` ceased
to be a server directory is the worked example: the fix was a data change across sixteen files, and
getting there meant reconstructing what the old method returned.

**What a class patch actually changed.** Projects that ship classes under platform fully-qualified names
are shipping decompiled platform code with a handful of lines altered. Without the 7.10 sources, working
out the intent means reading several hundred lines and inferring which part is the customization. With
them it is `diff`, and the answer takes seconds instead of an afternoon. The reference migration
analysed four such patches by hand.

**Mind the shape: 7.10 is not one tree.** 7.10 shipped the platform as separate **component sets**,
typically `p_platform`, `f_business` and `f_content`, each its own repository. ICM 11+ merged them into
a single repository to stop version bubbling, and they survive there as top-level directories. So the
target mount is one checkout and the 7.10 mount is several, which matters because **a search for a 7.10
class has to cover every set**, and one that covers only `p_platform` will come back empty for anything
in business or content. That is the failure mode the playbook warns about generally: an empty result
that means "not searched" rather than "not present".

Mount the parent directory holding the sets, or mount each set under a common root, so that one `find`
reaches all of them.

| 7.10 component set | where it is in the merged tree | cartridges at 14.4 |
|---|---|---:|
| `p_platform` | `/icm-as/platform` | 132 |
| `f_business` | `/icm-as/business` | 12 |
| `f_content` | `/icm-as/content` | 13 |
| (b2b) | `/icm-as/b2b` | 59 |

Counts measured on 14.4, and useful in their own right: a cartridge lives under exactly one of those
four, and `platform/` holds most of them, so it is the first place to look.

Second tier rather than first because most of a migration does not need it, and because the compiler
plus the target sources cover the common cases.

## Tier 3: situational, decide per project

**The ability to start and stop the server.** In the reference setup the agent could reach a running
instance but not start one, so every DBPrepare cycle was a developer round trip, exactly like the Gradle
problem. Granting it means giving the container access to the Docker socket, which is effectively root
on the host, so this is a real security decision and not one to take casually. If the environment is a
disposable developer VM, it closes the Phase 6 loop and is worth it. If the container shares a host with
anything that matters, it is not.

**A dump of the 7.10 database.** Not for the migration itself, for verifying it. See
`verifying-the-migration.md`: it is both the oracle for differential testing and the only way to
rehearse the actual cutover, which is the path production will take. Ask on day one; the approval is
slower than the copy.

**Web access to the vendor knowledge base.** The playbook refers to Intershop's API change guide for the
target release. If the container has no web access, fetch the relevant pages to a local directory first,
or the agent works from the compiler alone for exactly the deltas where that is weakest.

## What is not needed, and what to refuse

- **Production anything.** No production database, no production credentials, no production URL. The
  smoke check must never point at production, and a self-signed-certificate exception makes that worse.
- **Customer personal data.** A migration is a structural change; it does not need real customer records.
  If a dump is used for verification, scrub or restrict it first.
- **Write access to the platform sources.** Read-only is not caution, it is correctness: an accidental
  edit there silently changes the answer to every subsequent API question.
- **An IDE, a desktop, or anything graphical.** All of it is files, a compiler and HTTP.
- **Sources for the intermediate releases.** On the direct route, 7.10 and the target are the only two
  versions that matter.

## Ready-made files

`templates/container/` in this repository implements all of the above: a `Dockerfile`, a
`docker-compose.yml` with one service per agent, and an `.env.local.example`.
Copy both into the project, replace every `<path-to>`, and put `REPO_USER` and `REPO_TOKEN` in a
gitignored `.env.local`.

The image installs JDK 21, Kotlin, Python with the one third-party package the tooling needs, git, jq
and ripgrep, and ends with a check that fails the build rather than failing halfway through a migration.
The Kotlin download is verified against the checksum JetBrains publishes beside it, which catches a
truncated or corrupted download; it is not a defence against a compromised release.

The agent CLI is the last layer and the only agent-specific part, selected by an `AGENT` build argument
rather than by a second Dockerfile, because two copies of the same forty lines drift apart. Claude Code
and the GitHub Copilot CLI both publish musl builds, so the same Alpine base serves either:

```sh
docker compose run --rm claude      # or: copilot
```

An agent without a plugin mechanism reads the playbook straight off the mounted tool clone, at
`/icm-migration-support/skills/icm-migration/SKILL.md`, which is a further reason to mount it.

## The compose file in outline

```yaml
services:
  agent:
    image: <your agent image>
    env_file: .env.local            # gitignored; holds the feed token
    environment:
      - JAVA_HOME=/opt/java/openjdk
      - GRADLE_USER_HOME=/gradle-home
      - ORG_GRADLE_PROJECT_repoUser=${REPO_USER}
      - ORG_GRADLE_PROJECT_repoPassword=${REPO_TOKEN}
    volumes:
      - .:/workspace                                   # the project
      - <path>/gradle_docker:/gradle-home              # writable, its own directory
      - <path>/icm-as:/icm-as:ro                       # target release sources
      - <path>/icm-as-7.10:/icm-as-7.10:ro             # 7.10 sources, tier 2
      - <path>/icm-migration-support:/icm-migration-support:rw   # outside the project
```

## The one-line summary to take to whoever owns the environment

Everything above is ordinary developer access to a developer environment. The only item that needs a
real decision is the feed credential, and it is also the item that decides whether the migration takes
weeks or months.
