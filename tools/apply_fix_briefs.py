#!/usr/bin/env python3
"""Apply FILE/ANCHOR/REPLACE blocks from /tmp/fix-*.md briefs.

Read-only briefs produced by the DeepSeek agents. The applier is deliberately
conservative: it only edits when the anchor appears exactly once in the target
file, and it never creates files. Dry-run by default.

  python3 tools/apply_fix_briefs.py --dry-run /tmp/fix-*.md
  python3 tools/apply_fix_briefs.py --apply /tmp/fix-*.md
"""
import argparse
import glob
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))


def blocks(text):
    lines = text.splitlines()
    i = 0
    while i < len(lines):
        if not lines[i].startswith("FILE:"):
            i += 1
            continue
        path = lines[i].split("FILE:", 1)[1].strip().strip("`")
        i += 1
        anchor = replacement = None
        while i < len(lines) and not lines[i].startswith("FILE:"):
            if lines[i].startswith("ANCHOR:"):
                rest = lines[i].split("ANCHOR:", 1)[1].strip()
                i += 1
                parts = []
                if rest.startswith("```"):
                    i, parts = collect_fence(lines, i, rest)
                else:
                    while i < len(lines) and not lines[i].startswith(("REPLACE:", "WHY:", "FILE:")):
                        parts.append(lines[i]); i += 1
                anchor = "\n".join(parts)
                continue
            if lines[i].startswith("REPLACE:"):
                rest = lines[i].split("REPLACE:", 1)[1].strip()
                i += 1
                parts = []
                if rest.startswith("```"):
                    i, parts = collect_fence(lines, i, rest)
                else:
                    while i < len(lines) and not lines[i].startswith(("WHY:", "FILE:", "ANCHOR:", "##")):
                        parts.append(lines[i]); i += 1
                replacement = "\n".join(parts)
                continue
            i += 1
        if path and anchor and replacement is not None:
            yield path, anchor, replacement


def collect_fence(lines, i, opening):
    """Collect a fenced block; `i` points at the line after the opening fence."""
    parts = []
    while i < len(lines):
        if lines[i].strip().startswith("```"):
            return i + 1, parts
        parts.append(lines[i])
        i += 1
    return i, parts


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apply", action="store_true")
    ap.add_argument("files", nargs="+")
    args = ap.parse_args()
    targets = []
    for pattern in args.files:
        targets.extend(glob.glob(pattern))
    applied = skipped = 0
    seen = set()
    for brief in sorted(set(targets)):
        text = open(brief, encoding="utf-8").read()
        for rel, anchor, replacement in blocks(text):
            rel = rel.lstrip("/")
            path = os.path.normpath(os.path.join(ROOT, rel))
            key = (path, anchor, replacement)
            if key in seen:
                skipped += 1
                continue
            seen.add(key)
            if not path.startswith(ROOT + os.sep) or not os.path.isfile(path):
                print(f"MISS-FILE {rel}  ({os.path.basename(brief)})")
                skipped += 1
                continue
            src = open(path, encoding="utf-8").read()
            count = src.count(anchor)
            if count != 1:
                print(f"SKIP({count}) {rel}  ({os.path.basename(brief)}) anchor={anchor.splitlines()[0][:60]!r}")
                skipped += 1
                continue
            print(f"{'APPLY' if args.apply else 'MATCH'} {rel}  ({os.path.basename(brief)})")
            if args.apply:
                open(path, "w", encoding="utf-8").write(src.replace(anchor, replacement, 1))
            applied += 1
    print(f"\n{'applied' if args.apply else 'matched'}: {applied}  skipped: {skipped}")
    return 0 if applied else 1


if __name__ == "__main__":
    sys.exit(main())
