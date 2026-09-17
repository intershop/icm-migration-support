# Starting kit

Copy these into a project on day one. They are the bookkeeping that the
[icm-migration skill](../skills/icm-migration/SKILL.md) assumes exists, arrived at over a full 7.10 to
14.4 migration. The skill tells an agent *what* to do; this is *where it writes things down*, and
without it every project reinvents the same structure badly.

| file | copy to | what it is |
|---|---|---|
| `CLAUDE.md` | project root | working agreements an agent must follow. Merge into an existing `CLAUDE.md` rather than replacing it |
| `PROGRESS.md` | `docs/migration/PROGRESS.md` | what has happened, what is next, the command queue, the open items |
| `adr-0001-migration-approach.md` | `docs/adr/0001-migration-approach.md` | the per-project decisions |
| `smoke.py` | `scripts/smoke.py` | Tier 0 verification once a server runs |
| `Dockerfile` | project root | installs everything the playbook needs: JDK 21, Kotlin, Python, git, jq, ripgrep |
| `docker-compose.yml` | project root | the mounts, and the feed credentials that let the agent build |

Replace every `<placeholder>`. Nothing here is useful until it names the real project.

## The one rule that makes this worth keeping

**A finding that any 7.10 project would hit goes upstream, not into this file.** Section 7 of
`PROGRESS.md` exists for exactly that, and the test is written at the top of it. Without that rule each
project accumulates its own private knowledge and the tool never improves, which is how the same defect
gets fixed twice.
