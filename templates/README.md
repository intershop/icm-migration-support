# Starting kit

Copy these into a project on day one. They are the bookkeeping and the environment that the
[icm-migration playbook](../skills/icm-migration/SKILL.md) assumes exist, arrived at over a full 7.10 to
14.4 migration. The playbook tells an agent *what* to do; this is *where it works and where it writes
things down*. Without it every project reinvents the same structure badly.

Organised by kind. The table says where each one goes.

## `container/` the environment

| file | copy to | what it is |
|---|---|---|
| `Dockerfile` | project root | installs everything the playbook needs: JDK 21, Kotlin, Python, git, jq, ripgrep, and one agent CLI |
| `docker-compose.yml` | project root | the mounts, and the feed credentials that let the agent build |
| `.env.local.example` | `.env.local`, gitignored | the artifact feed token |

```sh
docker compose run --rm claude      # or: copilot
```

One image, one `AGENT` build argument, one service per agent, rather than a Dockerfile per agent that
drifts. `skills/icm-migration/references/agent-environment.md` grades every mount and tool by what its
absence actually costs.

## `agent/` the entry points

| file | copy to | what it is |
|---|---|---|
| `AGENTS.md` | project root | **the content**: working agreements, current state, how to reach the playbook |
| `CLAUDE.md` | project root | three lines pointing at `AGENTS.md` |
| `copilot-instructions.md` | `.github/copilot-instructions.md` | the same, for Copilot |

Each agent reads its own filename, so the redirects exist only to satisfy that. Keeping the substance in
one file means the rules cannot differ depending on who is working.

## `docs/` the bookkeeping

| file | copy to | what it is |
|---|---|---|
| `PROGRESS.md` | `docs/migration/PROGRESS.md` | what has happened, what is next, the command queue, the open items |
| `adr-0001-migration-approach.md` | `docs/adr/0001-migration-approach.md` | the per-project decisions |

## `scripts/` verification

| file | copy to | what it is |
|---|---|---|
| `smoke.py` | `scripts/smoke.py` | Tier 0 check once a server runs. Finds the playbook's `icm_client.py` itself |

---

Replace every `<placeholder>`. Nothing here is useful until it names the real project.

**Relative links here are written for the destination, not for this folder.** `copilot-instructions.md`
links `../AGENTS.md` because it lands in `.github/`, and `PROGRESS.md` links `../adr/...` because it
lands in `docs/migration/`. Both look broken from inside `templates/` and are correct once copied.

## The one rule that makes this worth keeping

**A finding that any 7.10 project would hit goes upstream, not into this project's notes.** Section 7 of
`PROGRESS.md` exists for exactly that, and the test is written at the top of it. Without that rule each
project accumulates its own private knowledge and the tool never improves, which is how the same defect
gets fixed twice.
