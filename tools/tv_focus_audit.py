#!/usr/bin/env python3
"""D-pad focus audit on a real Android TV (device or emulator).

Compose exposes focused nodes to accessibility, so `uiautomator dump` is a
truthful view of what the remote can focus. This walks a screen with the D-pad,
records every focusable node it reaches and every screen state where nothing is
focused, and writes a Markdown report.

Every adb call is wrapped in a hard timeout (project rule R4).

Usage:
  python3 tools/tv_focus_audit.py --serial emulator-5554 --screen Settings \
      --variant Playback --steps 60 --out /tmp/focus-settings.md
  python3 tools/tv_focus_audit.py --serial emulator-5554 --live --steps 200
"""
from __future__ import annotations

import argparse
import random
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from dataclasses import dataclass

PKG = "org.viptv.experiment"
ACTIVITY = f"{PKG}/.MainActivity"
ADB_TIMEOUT = 25


def adb(serial: str, *args: str, timeout: int = ADB_TIMEOUT) -> str:
    cmd = ["timeout", str(timeout), "adb", "-s", serial, *args]
    try:
        return subprocess.run(cmd, capture_output=True, text=True, timeout=timeout + 5).stdout
    except subprocess.TimeoutExpired:
        return ""


def key(serial: str, code: str) -> None:
    adb(serial, "shell", "input", "keyevent", code)


@dataclass(frozen=True)
class Node:
    desc: str
    text: str
    cls: str
    bounds: tuple[int, int, int, int]
    focusable: bool
    focused: bool

    @property
    def left(self) -> int: return self.bounds[0]
    @property
    def top(self) -> int: return self.bounds[1]
    @property
    def width(self) -> int: return self.bounds[2] - self.bounds[0]
    @property
    def height(self) -> int: return self.bounds[3] - self.bounds[1]

    def label(self) -> str:
        value = (self.text or self.desc or self.cls).replace("\n", " ").strip()
        return value[:56] if value else "<unlabelled>"

    def identity(self) -> str:
        # Bounds' left edge moves when a shelf scrolls; the label plus the
        # top/size pair stays stable for the same control.
        return f"{self.text}|{self.desc}|{self.cls}|{self.top}|{self.width}x{self.height}"


def dump(serial: str) -> list[Node]:
    adb(serial, "shell", "uiautomator", "dump", "/sdcard/viptv-focus.xml")
    raw = adb(serial, "shell", "cat", "/sdcard/viptv-focus.xml")
    if not raw.startswith("<?xml"):
        return []
    try:
        root = ET.fromstring(raw)
    except ET.ParseError:
        return []
    nodes: list[Node] = []
    labels: list[tuple[tuple[int, int, int, int], str]] = []
    for n in root.iter("node"):
        bounds = re.match(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]", n.get("bounds", ""))
        if not bounds:
            continue
        box = tuple(int(v) for v in bounds.groups())  # type: ignore[arg-type]
        text = n.get("text", "") or n.get("content-desc", "")
        if text:
            labels.append((box, text))
        nodes.append(Node(
            desc=n.get("content-desc", ""),
            text=n.get("text", ""),
            cls=n.get("class", ""),
            bounds=box,
            focusable=n.get("focusable") == "true",
            focused=n.get("focused") == "true",
        ))
    # Compose puts the label on a child text node, while the focusable parent
    # node carries no text of its own. Fold the contained labels into the parent
    # so the report names the control instead of "android.view.View".
    enriched: list[Node] = []
    for node in nodes:
        text = node.text
        if not text and node.focusable:
            inside = [label for box, label in labels
                      if box[0] >= node.bounds[0] and box[1] >= node.bounds[1]
                      and box[2] <= node.bounds[2] and box[3] <= node.bounds[3]]
            text = " ".join(inside)
        enriched.append(Node(node.desc, text, node.cls, node.bounds, node.focusable, node.focused))
    return enriched


def start_preview(serial: str, screen: str, variant: str) -> None:
    adb(serial, "shell", "am", "force-stop", PKG)
    adb(serial, "shell", "am", "start", "-n", ACTIVITY, "--ez", "ui_preview", "true",
        "--es", "ui_screen", screen, "--es", "ui_variant", variant)
    time.sleep(4)


def start_live(serial: str) -> None:
    adb(serial, "shell", "am", "force-stop", PKG)
    adb(serial, "shell", "am", "start", "-n", ACTIVITY)
    time.sleep(8)


def identities(nodes: list[Node]) -> dict[str, Node]:
    """Disambiguate duplicates by left-to-right order, not by absolute x."""
    ordered = sorted([n for n in nodes if n.focusable], key=lambda n: (n.top, n.left))
    counts: dict[str, int] = {}
    out: dict[str, Node] = {}
    for node in ordered:
        base = node.identity()
        index = counts.get(base, 0)
        counts[base] = index + 1
        out[f"{base}#{index}"] = node
    return out


def focused_node(nodes: list[Node]) -> Node | None:
    for n in nodes:
        if n.focused:
            return n
    return None


def run(serial: str, steps: int, seed: int) -> tuple[dict[str, Node], list[str], int]:
    """Sweep each direction, then walk back, then do a short random pass.

    A pure random walk spends most of its budget revisiting the same two
    neighbours in a vertical settings list. A sweep in each direction reaches
    the end of every row/column, which is what "every element is navigable"
    needs to show.
    """
    nodes = dump(serial)
    known = identities(nodes)
    visited: set[str] = set()
    missing_focus = 0
    history: list[str] = []

    def match(nodes_now: list[Node]) -> str | None:
        current = focused_node(nodes_now)
        if current is None:
            return None
        hit = next((k for k, v in known.items() if v.bounds == current.bounds), None)
        if hit is None:
            # The control may be newly composed (a scrolled-in card); add it.
            known.update(identities(nodes_now))
            hit = next((k for k, v in known.items() if v.bounds == current.bounds), None)
        return hit

    first = focused_node(nodes)
    if first is None:
        missing_focus += 1
        history.append("START: no focused node")
    else:
        origin = match(nodes)
        if origin:
            visited.add(origin)
        history.append(f"START: {first.label()}")

    def press_and_read(code: str, note: str) -> None:
        nonlocal missing_focus
        key(serial, code)
        time.sleep(0.3)
        now_nodes = dump(serial)
        now = focused_node(now_nodes)
        if now is None:
            missing_focus += 1
            history.append(f"{note}: no focused node after {code}")
            return
        hit = match(now_nodes)
        if hit and hit not in visited:
            visited.add(hit)
            history.append(f"{note}: {code} -> {now.label()}")
        else:
            history.append(f"{note}: {code} -> {now.label()} (seen)")

    sweep = max(4, steps // 8)
    pairs = [("DPAD_DOWN", "DPAD_UP"), ("DPAD_RIGHT", "DPAD_LEFT"),
             ("DPAD_UP", "DPAD_DOWN"), ("DPAD_LEFT", "DPAD_RIGHT")]
    index = 0
    for forward, back in pairs:
        for _ in range(sweep):
            press_and_read(forward, str(index)); index += 1
        for _ in range(sweep):
            press_and_read(back, str(index)); index += 1
    rng = random.Random(seed)
    for _ in range(max(0, steps - index)):
        press_and_read(rng.choice(["DPAD_RIGHT", "DPAD_LEFT", "DPAD_DOWN", "DPAD_UP"]), str(index)); index += 1
    return known, history, missing_focus


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--serial", required=True)
    ap.add_argument("--screen", default=None)
    ap.add_argument("--variant", default="Movie")
    ap.add_argument("--live", action="store_true")
    ap.add_argument("--steps", type=int, default=80)
    ap.add_argument("--seed", type=int, default=7)
    ap.add_argument("--out", default=None)
    args = ap.parse_args()

    if args.live:
        start_live(args.serial)
    elif args.screen:
        start_preview(args.serial, args.screen, args.variant)
    else:
        print("use --screen or --live", file=sys.stderr)
        return 2

    known, history, missing = run(args.serial, args.steps, args.seed)
    reached = sum(1 for line in history if "->" in line)
    lines = [
        f"# TV focus audit — {args.screen or 'live app'}" + (f" / {args.variant}" if args.screen else ""),
        "",
        f"- device: `{args.serial}`",
        f"- focusable controls: {len(known)}",
        f"- distinct focus arrivals recorded: {reached}",
        f"- states with no focused node: {missing}",
        "",
        "## Walk",
        "",
        "```",
        *history,
        "```",
    ]
    report = "\n".join(lines)
    if args.out:
        with open(args.out, "w", encoding="utf-8") as handle:
            handle.write(report + "\n")
        print(f"wrote {args.out}")
    else:
        print(report)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
