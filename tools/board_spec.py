#!/usr/bin/env python3
"""Summarise a VIPTV design board into implementable geometry.

The boards are generated HTML with absolute-positioned elements and inline
styles, so they already carry exact pixel specs. Reading them as text is far
more precise than eyeballing a screenshot (and cheaper than an image model).

Usage:
  python3 tools/board_spec.py <board-id> [--platform tv] [--full]
  python3 tools/board_spec.py Tv3MoviesFilters
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2] / "design" / "viptv-design-system" / "reference" / "screens" / "html"

STYLE_KEYS = (
    "left", "top", "right", "width", "height", "background", "color", "font-size",
    "font-weight", "border-radius", "box-shadow", "padding", "gap", "opacity",
    "letter-spacing", "display", "align-items", "justify-content", "border",
    "backdrop-filter", "transform", "inset",
)


def parse_style(text: str) -> dict[str, str]:
    out = {}
    for part in re.split(r";(?![^()]*\))", text or ""):
        if ":" not in part:
            continue
        key, value = part.split(":", 1)
        key = key.strip().lower()
        if key in STYLE_KEYS:
            out[key] = value.strip()
    return out


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("board")
    ap.add_argument("--platform", default="tv")
    ap.add_argument("--full", action="store_true", help="print every element instead of only labelled ones")
    args = ap.parse_args()

    path = ROOT / args.platform / f"{args.board}.html"
    if not path.exists():
        matches = sorted(ROOT.glob(f"*/{args.board}.html"))
        if not matches:
            print(f"board not found: {args.board}", file=sys.stderr)
            return 1
        path = matches[0]

    try:
        from bs4 import BeautifulSoup
    except ImportError:
        print("python3-bs4 is required", file=sys.stderr)
        return 1

    soup = BeautifulSoup(path.read_text(encoding="utf-8"), "html.parser")
    body = soup.body or soup
    print(f"# {args.board}  ({path.name})")
    title = soup.title.string if soup.title else ""
    print(f"# {title.strip()}")
    print()

    for el in body.find_all(True):
        style = parse_style(el.get("style", ""))
        if not style or ("left" not in style and "top" not in style):
            continue
        label = ""
        if el.name in ("input", "button", "a", "img", "svg"):
            label = el.get("aria-label") or el.get("alt") or el.get("placeholder") or ""
        own = "".join(child for child in el.find_all(string=True, recursive=False)).strip()
        if not own and el.name not in ("img", "svg", "input"):
            own = el.get_text(" ", strip=True)
        text = re.sub(r"\s+", " ", (own or label)).strip()
        if not text and not args.full:
            continue
        geom = " ".join(f"{k}={v}" for k, v in style.items()
                        if k in ("left", "top", "right", "width", "height", "font-size", "font-weight",
                                 "color", "background", "border-radius", "letter-spacing", "gap", "padding"))
        extra = " ".join(f"{k}={v}" for k, v in style.items()
                         if k in ("box-shadow", "backdrop-filter", "border", "opacity", "transform", "inset"))
        role = f"[{el.get('role')}] " if el.get("role") else ""
        print(f"{role}{el.name}: {text[:110]}")
        print(f"    {geom}" + (f"\n    {extra}" if extra else ""))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
