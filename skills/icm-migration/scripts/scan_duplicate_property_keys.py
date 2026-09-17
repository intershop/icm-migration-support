#!/usr/bin/env python3
"""Report duplicate keys in a project's .properties files.

ICM 7.10 loaded every .properties file through java.util.Properties, which keeps the *last*
occurrence of a duplicate key and never complains. ICM 14 parses the DBPrepare descriptors
(dbinit.properties, migration-to-*.properties) with a validating reader that aborts the whole
server start:

    ConfigurationValidationException: Duplicate property key detected in
    '.../<cartridge>-LOCAL.jar!/resources/<cartridge>/dbinit.properties' : Class50

So a duplicate that silently dropped a preparer for years becomes a hard startup failure at
migration time. Both classes matter: FATAL ones stop the server, WARN ones are live defects the
migration is a good moment to notice.

Parsing follows java.util.Properties, because two shortcuts produce false positives:
  * a line ending in an odd number of backslashes continues onto the next line, and DBPrepare
    descriptors are full of them (preparer arguments are indented continuations that contain '=');
  * files are frequently CRLF, so the terminator has to be stripped before the backslash test.

Usage:  scan_duplicate_property_keys.py [root]     (default: current directory)
Exit:   1 if any FATAL duplicate was found, else 0.
"""
import os
import sys

SKIP_DIRS = {".git", "build", "bin", "node_modules", ".gradle", ".idea"}
# Files DBPrepare parses with the validating reader.
FATAL = lambda n: n == "dbinit.properties" or (n.startswith("migration-to-") and n.endswith(".properties"))


def logical_lines(text):
    """Yield (line_number_of_first_physical_line, logical_line) per java.util.Properties."""
    physical = text.split("\n")
    i, n = 0, len(physical)
    while i < n:
        start = i
        line = physical[i].rstrip("\r")
        # A trailing odd number of backslashes continues the logical line.
        while len(line) - len(line.rstrip("\\")) & 1:
            line = line[:-1]
            i += 1
            if i >= n:
                break
            # Java strips leading whitespace of a continuation line.
            line += physical[i].rstrip("\r").lstrip(" \t\f")
        yield start + 1, line
        i += 1


def key_of(line):
    """Return the key of a natural line, or None for blanks and comments."""
    s = line.lstrip(" \t\f")
    if not s or s[0] in "#!":
        return None
    key, esc = [], False
    for ch in s:
        if esc:
            key.append(ch)
            esc = False
        elif ch == "\\":
            esc = True
        elif ch in "=: \t\f":
            break
        else:
            key.append(ch)
    return "".join(key) or None


def scan(path):
    with open(path, "rb") as fh:
        text = fh.read().decode("utf-8", "replace")
    seen, dups = {}, {}
    for lineno, line in logical_lines(text):
        k = key_of(line)
        if k is None:
            continue
        if k in seen:
            dups.setdefault(k, [seen[k]]).append(lineno)
        else:
            seen[k] = lineno
    return dups


def main():
    root = sys.argv[1] if len(sys.argv) > 1 else "."
    fatal_hits = warn_hits = 0
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        for name in sorted(filenames):
            if not name.endswith(".properties"):
                continue
            full = os.path.join(dirpath, name)
            try:
                dups = scan(full)
            except OSError as exc:
                print("  !! unreadable: %s (%s)" % (full, exc))
                continue
            if not dups:
                continue
            level = "FATAL" if FATAL(name) else "WARN "
            if level.strip() == "FATAL":
                fatal_hits += 1
            else:
                warn_hits += 1
            print("%s %s" % (level, full))
            for k, lines in sorted(dups.items()):
                print("        %-40s lines %s  (last one wins)" % (k, ", ".join(map(str, lines))))
    print("\n%d file(s) DBPrepare will reject, %d file(s) with silently dropped entries."
          % (fatal_hits, warn_hits))
    return 1 if fatal_hits else 0


if __name__ == "__main__":
    sys.exit(main())
