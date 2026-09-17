#!/usr/bin/env python3
"""Report what the migration did NOT claim.

The operation log says what the tool touched. The more valuable report is the complement: content no
step claims is exactly what gets carried silently into a migrated project and discovered months later.
Every "the tool was silent about X" finding in this project's backlog would have appeared here on day
one.

This derives everything from the operation log rather than re-implementing each step's matching rules,
so it cannot drift from what the tool actually does.

Typical use, before committing to a migration:

    # 1. shadow run on a throwaway clone, so nothing touches the real project
    git clone <project> /tmp/shadow && git -C /tmp/shadow checkout -b shadow
    gradlew migration:migrateAll -Ptarget=/tmp/shadow -Psteps=<steps> -Preport=/tmp/shadow-report

    # 2. ask what it never touched
    tools/inventory.py --project /tmp/shadow --log /tmp/shadow-report/operations.jsonl

The baseline file list comes from git. By default it is the commit just before the first step commit,
which the log records, so the run configures this itself.
"""
import argparse
import json
import subprocess
import sys
from collections import Counter, defaultdict
from pathlib import Path

# Content that is worth a second look when a step deletes it. Source and tests are routinely bundled
# into assembly projects that step 005 removes wholesale.
RESCUE_SUFFIXES = {".java", ".groovy", ".kt", ".isml", ".pipeline", ".component", ".xml", ".properties"}
RESCUE_MARKERS = ("src/test", "src/remoteTest", "src/main/java", "/specs/", "/pages/")


def git(project, *args):
    result = subprocess.run(["git", "-C", str(project), *args],
                            capture_output=True, text=True, check=False)
    if result.returncode != 0:
        sys.exit(f"git {' '.join(args)} failed in {project}:\n{result.stderr.strip()}")
    return result.stdout


def load_log(path):
    records = []
    with open(path, encoding="utf-8") as handle:
        for number, line in enumerate(handle, 1):
            line = line.strip()
            if not line:
                continue
            try:
                records.append(json.loads(line))
            except json.JSONDecodeError as error:
                sys.exit(f"{path}:{number} is not valid JSON: {error}")
    return records


def baseline_ref(records, explicit):
    """The commit to list files from: the state before the migration started."""
    if explicit:
        return explicit
    for record in records:
        if record.get("event") == "step-end" and record.get("commit"):
            return record["commit"] + "~1"
    sys.exit("no step commit in the log, so the baseline cannot be derived. Pass --ref explicitly "
             "(auto-commit may have been disabled).")


def relative(path, project):
    """Log paths are absolute. Reduce to a project-relative path, or None if outside the project."""
    if not path:
        return None
    try:
        return Path(path).resolve().relative_to(project).as_posix()
    except ValueError:
        return None


def looks_worth_rescuing(path):
    return Path(path).suffix in RESCUE_SUFFIXES or any(marker in path for marker in RESCUE_MARKERS)


def build(project, log_path, ref):
    records = load_log(log_path)
    ref = baseline_ref(records, ref)

    baseline = {line for line in git(project, "ls-tree", "-r", "--name-only", ref).splitlines() if line}

    handled = set()      # a step reported SUCCESS on this path
    seen_unhandled = set()  # a step met it and reported UNKNOWN, WARNING or FAILED
    deleted = set()
    failed = []
    uncertain = []

    for record in records:
        if record.get("event") != "operation":
            continue
        source = relative(record.get("source"), project)
        target = relative(record.get("target"), project)
        bucket = handled if record.get("status") == "SUCCESS" else seen_unhandled
        for path in (source, target):
            if path:
                bucket.add(path)
        if record.get("type") == "DELETE" and record.get("status") == "SUCCESS" and source:
            deleted.add(source)
        if record.get("status") == "FAILED":
            failed.append({"step": record["step"], "path": source or target, "message": record.get("message")})
        if record.get("status") in ("UNKNOWN", "WARNING"):
            uncertain.append({"step": record["step"], "status": record["status"],
                              "path": source or target, "message": record.get("message")})

    # Steps that move a whole folder record the DIRECTORY, not each file inside it, so a file counts as
    # covered when it or any ancestor appears in the log. Matching file paths alone claimed 817 files
    # were still under staticfiles/ when the true number on disk was 16.
    def covered_by(path, paths):
        return path in paths or any(parent.as_posix() in paths for parent in Path(path).parents
                                    if parent.as_posix() != ".")

    unclaimed = sorted(path for path in baseline
                       if not covered_by(path, handled) and not covered_by(path, seen_unhandled))
    flagged = sorted(path for path in baseline
                     if not covered_by(path, handled) and covered_by(path, seen_unhandled))
    deleted_in_baseline = sorted(deleted & baseline)

    # Ground truth from the migrated tree rather than arithmetic on the log. staticfiles is a
    # backward-compatibility path in ICM 11+, not a target location, so whatever is still sitting there
    # once the step set has run is content no step knew how to move.
    left_in_staticfiles = sorted(
        line for line in git(project, "ls-files").splitlines()
        if line and (line.startswith("staticfiles/") or "/staticfiles/" in line))

    by_area = defaultdict(list)
    for path in unclaimed:
        by_area[path.split("/", 1)[0]].append(path)

    return {
        "baseline_ref": ref,
        "counts": {
            "baseline_files": len(baseline),
            "handled": len(baseline) - len(unclaimed) - len(flagged),
            "unclaimed": len(unclaimed),
            "seen_but_not_handled": len(flagged),
            "left_in_staticfiles": len(left_in_staticfiles),
            "deleted": len(deleted_in_baseline),
            "failed_operations": len(failed),
            "uncertain_operations": len(uncertain),
        },
        "seen_but_not_handled": flagged,
        "left_in_staticfiles": left_in_staticfiles,
        "left_in_staticfiles_by_area": dict(Counter(
            p.split("staticfiles/", 1)[1].split("/")[0] for p in left_in_staticfiles).most_common()),
        "unclaimed": unclaimed,
        "unclaimed_by_area": {area: sorted(paths) for area, paths in sorted(by_area.items())},
        "unclaimed_by_extension": dict(Counter(Path(p).suffix or "<none>" for p in unclaimed).most_common()),
        "deleted": deleted_in_baseline,
        "deleted_worth_review": sorted(p for p in deleted_in_baseline if looks_worth_rescuing(p)),
        "failed": failed,
        "uncertain": uncertain,
    }


def report(result, limit):
    counts = result["counts"]
    print(f"Baseline {result['baseline_ref']}: {counts['baseline_files']} files")
    print(f"  handled by a step    : {counts['handled']}")
    print(f"  UNCLAIMED            : {counts['unclaimed']}")
    print(f"  seen but not handled : {counts['seen_but_not_handled']}")
    print(f"  left under staticfiles/ after the run : {counts['left_in_staticfiles']}")
    print(f"  deleted              : {counts['deleted']}")
    print(f"  failed operations    : {counts['failed_operations']}")
    print(f"  unknown or warning   : {counts['uncertain_operations']}")

    if result["deleted_worth_review"]:
        print(f"\nDELETED CONTENT WORTH A LOOK ({len(result['deleted_worth_review'])})")
        print("  Source and tests are routinely bundled into assembly projects that a step removes")
        print("  wholesale. Rescue anything here before running the step set for real.")
        for path in result["deleted_worth_review"][:limit]:
            print(f"    {path}")
        if len(result["deleted_worth_review"]) > limit:
            print(f"    ... {len(result['deleted_worth_review']) - limit} more, see the JSON")

    if result["left_in_staticfiles"]:
        print(f"\nSTILL UNDER staticfiles/ AFTER THE RUN ({counts['left_in_staticfiles']})")
        print("  Read this first. staticfiles is a backward-compatibility path in ICM 11+, not a target")
        print("  location, so everything here is content no step knew how to move.")
        for area, count in result["left_in_staticfiles_by_area"].items():
            print(f"    staticfiles/{area}/  {count}")
        for path in result["left_in_staticfiles"][:limit]:
            print(f"      {path}")
        if len(result["left_in_staticfiles"]) > limit:
            print(f"      ... {len(result['left_in_staticfiles']) - limit} more, see the JSON")

    if result["unclaimed"]:
        print(f"\nALL UNCLAIMED, BY AREA ({counts['unclaimed']} files, context rather than a queue)")
        print("  No step matched these. Each is either fine, or content that will be carried into the")
        print("  migrated project and read by nothing. This is the review queue.")
        for area, paths in result["unclaimed_by_area"].items():
            print(f"    {area}/  {len(paths)}")
        print("\n  by extension:")
        for extension, count in list(result["unclaimed_by_extension"].items())[:15]:
            print(f"    {extension:<14} {count}")

    if result["failed"]:
        print(f"\nFAILED OPERATIONS ({len(result['failed'])})")
        for step, count in Counter(f["step"] for f in result["failed"]).most_common():
            print(f"    {step}: {count}")

    if result["uncertain"]:
        print(f"\nUNKNOWN OR WARNING ({len(result['uncertain'])})")
        print("  The tool saying it met something it does not handle. Read every one.")
        for item in result["uncertain"][:limit]:
            print(f"    [{item['step']}] {item['status']} {item['path']}: {item['message']}")
        if len(result["uncertain"]) > limit:
            print(f"    ... {len(result['uncertain']) - limit} more, see the JSON")


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--project", required=True, help="the migrated project (a git repository)")
    parser.add_argument("--log", required=True, help="operations.jsonl from the run")
    parser.add_argument("--ref", help="baseline commit; default is the parent of the first step commit")
    parser.add_argument("--json", help="also write the full result here")
    parser.add_argument("--limit", type=int, default=25, help="lines per list in the printed report")
    args = parser.parse_args()

    project = Path(args.project).resolve()
    result = build(project, args.log, args.ref)
    report(result, args.limit)

    if args.json:
        Path(args.json).write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
        print(f"\nfull result: {args.json}")

    # Unclaimed content is a review queue, not a failure, so this exits 0 unless something went wrong.
    return 0


if __name__ == "__main__":
    sys.exit(main())
