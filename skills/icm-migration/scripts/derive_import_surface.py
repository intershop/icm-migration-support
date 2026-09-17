#!/usr/bin/env python3
"""Derive a project's Intershop import surface and map it to cartridge coordinates.

Read-only. Scans .java files for `import com.intershop...` statements, resolves each
observed package to a cartridge coordinate via the reference lookup tables, and writes
a TSV report. It never modifies source files.

The point is to turn Phase 3 dependency reconstruction from "read 400 files" into
"review a table and chase the UNMAPPED rows", identically on every project.

Usage:
    derive_import_surface.py <project-root> [-o report.tsv] [--refs <dir>]

Output columns:
    package            observed import package (class name stripped)
    hits               how many import statements referenced it
    cartridges         which cartridges in this project import it
    coordinate         resolved group:artifact, or UNMAPPED
    configuration      cartridge(...) or implementation(...), or empty when unmapped
    source             which lookup table resolved it

Exit code is 0 even with unmapped rows; they are expected and are the review queue.
"""

from __future__ import annotations

import argparse
import os
import re
import sys
from collections import defaultdict
from pathlib import Path

IMPORT_RE = re.compile(r"^\s*import\s+(?:static\s+)?(com\.intershop\.[A-Za-z0-9_.]+)\s*;")
PACKAGE_RE = re.compile(r"^\s*package\s+([A-Za-z0-9_.]+)\s*;")
# rows look like: | `com.intershop.adapter.bmecat.*` | `cartridge("com.intershop.business:ac_bmecat")` |
MD_ROW_RE = re.compile(
    r"^\|\s*`(com\.intershop\.[A-Za-z0-9_.]*?)(?:\.\*)?`\s*\|\s*`(\w+)\(\"([^\"]+)\"\)`\s*\|"
)
# lines look like: business:ac_bmecat=com.intershop.adapter.bmecat
PROP_LINE_RE = re.compile(r"^(b2b|business|content|platform):([A-Za-z0-9_]+)\s*=\s*(com\.intershop\.[A-Za-z0-9_.]+)\s*$")

SKIP_DIRS = {".git", "build", "bin", "out", "target", ".gradle", "node_modules", "migration"}


def load_properties(path: Path) -> dict[str, tuple[str, str, str]]:
    """package prefix -> (coordinate, configuration, source)"""
    table: dict[str, tuple[str, str, str]] = {}
    if not path.is_file():
        return table
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        m = PROP_LINE_RE.match(line)
        if not m:
            continue
        group, artifact, package = m.groups()
        table[package] = (f"com.intershop.{group}:{artifact}", "cartridge", path.name)
    return table


def load_markdown(path: Path) -> dict[str, tuple[str, str, str]]:
    table: dict[str, tuple[str, str, str]] = {}
    if not path.is_file():
        return table
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        m = MD_ROW_RE.match(line.strip())
        if not m:
            continue
        package, configuration, coordinate = m.groups()
        table[package] = (coordinate, configuration, path.name)
    return table


def scan_imports(root: Path) -> tuple[dict[str, int], dict[str, set[str]], dict[str, set[str]]]:
    """Returns (import hits, importing cartridges, packages declared locally -> owning cartridges)."""
    hits: dict[str, int] = defaultdict(int)
    owners: dict[str, set[str]] = defaultdict(set)
    declared: dict[str, set[str]] = defaultdict(set)
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS and not d.startswith(".")]
        for name in filenames:
            if not name.endswith(".java"):
                continue
            f = Path(dirpath) / name
            try:
                rel = f.relative_to(root)
                cartridge = rel.parts[0] if len(rel.parts) > 1 else "<root>"
            except ValueError:
                cartridge = "<unknown>"
            try:
                text = f.read_text(encoding="utf-8", errors="replace")
            except OSError as exc:
                print(f"warning: cannot read {f}: {exc}", file=sys.stderr)
                continue
            for line in text.splitlines():
                m = IMPORT_RE.match(line)
                if m:
                    fqcn = m.group(1)
                    package = fqcn.rsplit(".", 1)[0]  # strip the class (or member) name
                    hits[package] += 1
                    owners[package].add(cartridge)
                    continue
                m = PACKAGE_RE.match(line)
                if m:
                    declared[m.group(1)].add(cartridge)
    return hits, owners, declared


def resolve_local(package: str, declared: dict[str, set[str]]) -> set[str] | None:
    """Longest declared-package prefix wins, so subpackages resolve to their owning cartridge."""
    best: str | None = None
    for prefix in declared:
        if package == prefix or package.startswith(prefix + "."):
            if best is None or len(prefix) > len(best):
                best = prefix
    return declared[best] if best is not None else None


def resolve(package: str, table: dict[str, tuple[str, str, str]]) -> tuple[str, str, str]:
    """Longest matching package prefix wins."""
    best: str | None = None
    for prefix in table:
        if package == prefix or package.startswith(prefix + "."):
            if best is None or len(prefix) > len(best):
                best = prefix
    if best is None:
        return ("UNMAPPED", "", "")
    return table[best]


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("project_root", type=Path)
    ap.add_argument("-o", "--output", type=Path, default=Path("import-surface.tsv"))
    ap.add_argument("--refs", type=Path, default=Path(__file__).resolve().parent.parent / "references")
    args = ap.parse_args()

    if not args.project_root.is_dir():
        print(f"error: {args.project_root} is not a directory", file=sys.stderr)
        return 2

    table = load_properties(args.refs / "cartridge-packages.txt")
    table.update(load_markdown(args.refs / "import-to-cartridge.md"))
    if not table:
        print(f"error: no lookup entries loaded from {args.refs}", file=sys.stderr)
        return 2

    hits, owners, declared = scan_imports(args.project_root)
    if not hits:
        print("no com.intershop imports found", file=sys.stderr)

    rows = []
    for package in sorted(hits):
        local = resolve_local(package, declared)
        if local:
            # defined inside this project: the dependency is on a sibling cartridge, not an artifact
            targets = sorted(local)
            coordinate = " ".join(f'project(":{c}")' for c in targets)
            configuration, source = "cartridge", "local"
        else:
            coordinate, configuration, source = resolve(package, table)
        importers = sorted(owners[package])
        # a cartridge importing its own package is not a dependency at all
        if source == "local" and len(importers) == 1 and local == set(importers):
            source = "local(self)"
        rows.append((package, hits[package], ",".join(importers), coordinate, configuration, source))

    with args.output.open("w", encoding="utf-8") as fh:
        fh.write("package\thits\tcartridges\tcoordinate\tconfiguration\tsource\n")
        for r in rows:
            fh.write("\t".join(str(x) for x in r) + "\n")

    selfrefs = sum(1 for r in rows if r[5] == "local(self)")
    localrefs = sum(1 for r in rows if r[5] == "local")
    external = sum(1 for r in rows if r[5] not in ("local", "local(self)") and r[3] != "UNMAPPED")
    unmapped = sum(1 for r in rows if r[3] == "UNMAPPED")
    coords = {r[3] for r in rows if r[5] not in ("local", "local(self)") and r[3] != "UNMAPPED"}
    print(f"lookup entries loaded    : {len(table)}")
    print(f"local packages declared  : {len(declared)}")
    print(f"packages imported        : {len(rows)}")
    print(f"  own cartridge          : {selfrefs}   (no dependency needed)")
    print(f"  sibling cartridge      : {localrefs}   -> cartridge(project(\":...\"))")
    print(f"  resolved to artifact   : {external}   -> {len(coords)} distinct coordinates")
    print(f"  UNMAPPED               : {unmapped}   <- the review queue")
    print(f"report                   : {args.output}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
