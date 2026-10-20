#!/usr/bin/env python3
"""Print the focused node of a `uiautomator dump` XML as TSV on stdin -> stdout.

Output: x1<TAB>y1<TAB>x2<TAB>y2<TAB>content-desc   (numbers first, because
titles contain spaces and commas). Nothing on stdout when no node is focused.

Used by tools/tv_ab.sh to decide whether a measurement burst will actually
scroll a shelf instead of walking the top bar.
"""
import re
import sys

NODE = re.compile(r"<node[^>]*>")
BOUNDS = re.compile(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
DESC = re.compile(r'content-desc="([^"]*)"')


def main() -> int:
    text = sys.stdin.read()
    for node in NODE.findall(text):
        if 'focused="true"' not in node:
            continue
        bounds = BOUNDS.search(node)
        if not bounds:
            continue
        desc = DESC.search(node)
        x1, y1, x2, y2 = bounds.groups()
        sys.stdout.write("\t".join([x1, y1, x2, y2, desc.group(1) if desc else ""]))
        return 0
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
