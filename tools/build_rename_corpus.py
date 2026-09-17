#!/usr/bin/env python3
"""Flatten every old-name-to-new-name mapping in this tool into one corpus.

ICM 11, 12 and 13 are no longer deployment targets, so the per-version framing is
gone. The mappings themselves keep full value: a rename that happened in ICM 12 is
still a rename between 7.10 and the current release.

This script DERIVES the corpus from the step definitions and the OpenRewrite recipes,
so there is exactly one source of truth and the corpus can never drift from what the
tool actually applies. Regenerate it rather than editing the output.

Every entry carries a `since` tag naming the release the mapping came from. Nothing
consumes that tag today; it is what would let per-version sets be reconstructed if a
14-to-N upgrade path is ever wanted.

Usage:
    tools/build_rename_corpus.py [--out corpus/rename-corpus.json] [--check]

    --check  regenerate in memory and exit 1 if the committed corpus differs.
             For CI, so the corpus cannot silently fall behind the step data.
"""
import argparse
import json
import re
import sys
from pathlib import Path

try:
    import yaml
except ImportError:
    sys.exit("pyyaml is required: pip install pyyaml")

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "migration" / "src" / "main" / "resources"
STEPS_7X10 = RES / "migration" / "001_migration_7x10_to_11"
RECIPES = RES / "gradle" / "rewrite-7x10-to-current.yml"

# Recipes are named com.intershop.migration.icm12.* / icm13.*; the release is in the name.
SINCE_FROM_RECIPE = re.compile(r"\bicm(\d+)\b")


def load_step(name):
    path = STEPS_7X10 / name
    if not path.is_file():
        sys.exit(f"missing step definition: {path}")
    with path.open(encoding="utf-8") as handle:
        return yaml.safe_load(handle)


def load_recipes():
    if not RECIPES.is_file():
        sys.exit(f"missing recipe set: {RECIPES}")
    with RECIPES.open(encoding="utf-8") as handle:
        return [doc for doc in yaml.safe_load_all(handle) if doc]


def entry(kind, since, source, **fields):
    row = {"kind": kind, "since": since, "source": source}
    row.update(fields)
    return row


def from_steps():
    """The three data-driven steps of the 7.10-to-11 set."""
    rows = []

    step = load_step("040_RenamedDependencies.yml")
    for old, new in (step["options"].get("dependency-map") or {}).items():
        rows.append(entry("dependency", "11", "040_RenamedDependencies",
                          **{"from": old, "to": new}))

    step = load_step("045_RemovedDependencies.yml")
    for coordinate in (step["options"].get("dependencies") or []):
        rows.append(entry("dependency-removed", "11", "045_RemovedDependencies",
                          coordinate=coordinate))

    step = load_step("065_RenamedPackages.yml")
    extensions = step["options"].get("file-extension") or []
    for old, new in (step["options"].get("package-map") or {}).items():
        rows.append(entry("package", "11", "065_RenamedPackages",
                          applies_to=sorted(extensions), **{"from": old, "to": new}))

    return rows


# Each handler turns one OpenRewrite recipe invocation into corpus rows.
def _change_method_name(args):
    return [("method-renamed", {"from": args.get("methodPattern"),
                                "to": args.get("newMethodName")})]


def _change_type(args):
    return [("type-renamed", {"from": args.get("oldFullyQualifiedTypeName"),
                              "to": args.get("newFullyQualifiedTypeName")})]


def _change_package(args):
    return [("package", {"from": args.get("oldPackageName"),
                         "to": args.get("newPackageName")})]


def _signature_change(kind):
    def handler(args):
        detail = {k: v for k, v in args.items() if k != "methodPattern"}
        return [(kind, {"from": args.get("methodPattern"), "detail": detail})]
    return handler


def _replace_constant(args):
    return [("constant-replaced", {"from": args.get("literalValue") or args.get("existingFullyQualifiedConstantName"),
                                   "to": args.get("fullyQualifiedConstantName")})]


def _add_dependency(args):
    return [("dependency-added", {"to": f"{args.get('groupId')}:{args.get('artifactId')}"})]


HANDLERS = {
    "org.openrewrite.java.ChangeMethodName": _change_method_name,
    "org.openrewrite.java.ChangeType": _change_type,
    "org.openrewrite.java.ChangePackage": _change_package,
    "org.openrewrite.java.DeleteMethodArgument": _signature_change("method-arg-deleted"),
    "org.openrewrite.java.AddLiteralMethodArgument": _signature_change("method-arg-added"),
    "org.openrewrite.java.AddNullMethodArgument": _signature_change("method-arg-added"),
    "org.openrewrite.java.ReorderMethodArguments": _signature_change("method-args-reordered"),
    "org.openrewrite.java.spring.ChangeMethodParameter": _signature_change("method-param-changed"),
    "org.openrewrite.java.migrate.AddMissingMethodImplementation": _signature_change("method-must-implement"),
    "org.openrewrite.java.ReplaceStringLiteralWithConstant": _replace_constant,
    "org.openrewrite.java.ReplaceConstantWithAnotherConstant": _replace_constant,
    "org.openrewrite.gradle.AddDependency": _add_dependency,
    "org.openrewrite.xml.ChangeTagAttribute": _signature_change("xml-attribute-changed"),
}


def from_recipes():
    rows = []
    unhandled = set()

    for doc in load_recipes():
        name = doc.get("name", "")
        match = SINCE_FROM_RECIPE.search(name)
        since = match.group(1) if match else None

        for item in doc.get("recipeList") or []:
            # A plain string is a reference to another recipe in this file, not a mapping.
            if isinstance(item, str):
                continue
            for recipe_id, args in item.items():
                args = args or {}
                handler = HANDLERS.get(recipe_id)
                if handler is None:
                    unhandled.add(recipe_id)
                    continue
                for kind, fields in handler(args):
                    rows.append(entry(kind, since, name, **fields))

    return rows, unhandled


def build():
    rows = from_steps()
    recipe_rows, unhandled = from_recipes()
    rows.extend(recipe_rows)
    rows.sort(key=lambda r: (r["kind"], str(r.get("from") or r.get("coordinate") or r.get("to") or ""), r["source"]))

    counts = {}
    for row in rows:
        counts[row["kind"]] = counts.get(row["kind"], 0) + 1

    return {
        "type": "specs.intershop.com/v1beta/rename-corpus",
        "description": "Flattened old-name-to-new-name mappings for the route 7.10 to current. "
                       "Generated by tools/build_rename_corpus.py; do not edit by hand.",
        "route": "7.10 -> current",
        "counts": dict(sorted(counts.items())),
        "total": len(rows),
        "unhandled_recipe_types": sorted(unhandled),
        "entries": rows,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--out", default=str(ROOT / "corpus" / "rename-corpus.json"))
    parser.add_argument("--check", action="store_true",
                        help="exit 1 if the committed corpus differs from a fresh build")
    args = parser.parse_args()

    corpus = build()
    rendered = json.dumps(corpus, indent=2, ensure_ascii=False) + "\n"
    out = Path(args.out)

    if args.check:
        if not out.is_file():
            print(f"corpus missing: {out}", file=sys.stderr)
            return 1
        if out.read_text(encoding="utf-8") != rendered:
            print(f"corpus is stale: {out}\nregenerate with tools/build_rename_corpus.py", file=sys.stderr)
            return 1
        print(f"corpus up to date: {corpus['total']} entries")
        return 0

    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(rendered, encoding="utf-8")
    print(f"wrote {out} ({corpus['total']} entries)")
    for kind, count in corpus["counts"].items():
        print(f"  {kind}: {count}")
    if corpus["unhandled_recipe_types"]:
        print("\nunhandled recipe types (extend HANDLERS to cover them):", file=sys.stderr)
        for recipe_id in corpus["unhandled_recipe_types"]:
            print(f"  {recipe_id}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
