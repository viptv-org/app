"""Locate the workspace playback-gateway clone: VIPTV_PLAYBACK_GATEWAY, or the
nearest `playback-gateway` next to or above this repository."""
from pathlib import Path
import os

root = Path(__file__).resolve().parents[1]


def repository(env=os.environ):
    if env.get("VIPTV_PLAYBACK_GATEWAY"):
        return Path(env["VIPTV_PLAYBACK_GATEWAY"])
    for parent in root.parents:
        candidate = parent / "playback-gateway"
        if (candidate / ".git").exists():
            return candidate
    raise SystemExit("playback-gateway clone not found; set VIPTV_PLAYBACK_GATEWAY")
