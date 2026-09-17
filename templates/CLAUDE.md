# <project>

Intershop ICM customization project, being migrated from **ICM 7.10 to <target release>**.

## Migration in progress

The repository is mid-migration and is not in a consistent state. Before changing anything, read
`docs/migration/PROGRESS.md`, starting at its section 0, "Resume here", which names the next action and
the open decisions. Per-project decisions are in `docs/adr/`.

The reusable how-to is the **`icm-migration` skill**, which ships with
[icm-migration-support](https://github.com/intershop/icm-migration-support) and is project-agnostic.
Findings that any 7.10 project would hit belong there or in the tool, not here.

Install it rather than copying it, so that improvements found on other projects reach this one:

```sh
claude plugin marketplace add intershop/icm-migration-support \
    --sparse .claude-plugin skills templates
claude plugin install icm-migration
```

The marketplace is read from that repository's **default branch**. If the playbook has not merged there
yet, name the branch that carries it, `intershop/icm-migration-support@<branch>`, otherwise the add
fails with `Marketplace file not found`.

`scripts/smoke.py` finds the plugin's `icm_client.py` automatically; set `ICM_CLIENT_DIR` if it cannot.

## Working agreements

- **Moves use `git mv`**, never copy-and-delete, verified byte-identical with `git hash-object` before
  and after.
- **Bulk regex edits on source are guarded**: predict the occurrence count, confirm the changed-line
  count matches, read the diff. Revert rather than patch a regex that overreached.
- **Never report a step complete on a zero exit code.** Either a static check confirms it, or a build
  run does. `$?` after a pipeline reports the last command's status, so use `${PIPESTATUS[0]}`.
- **ICM platform sources are the authority on API questions**, not inference from compiler output.
  Mount them read-only and check the real signatures. Never walk the whole tree: exclude `*/build/*`
  and `*/bin/*`, which hold stale duplicates of `src/`.
- **Pair every negative search with a positive control** that must return hits from the same command
  shape. A negative grep is evidence only once its control passes.
- **Ask the running server, do not only read its logs.** Once a server starts, an agent container can
  usually reach it over HTTPS, and checking that a step *created the configuration* beats checking that
  it logged `Success`.
- **Never commit credentials.**
- One migration step per commit; the tool's auto-commit stays on so each is revertable alone.

## Can the agent run Gradle?

<yes: say so, and delete the rest of this section>

<no: then queue commands in PROGRESS.md section 5 for a developer and record the results there. Give an
exact command line that writes its output to a `q<N>-*.log` file in the project root, which `.gitignore`
must cover, so the log can be read back.>
