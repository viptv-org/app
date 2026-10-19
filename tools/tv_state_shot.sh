#!/usr/bin/env bash
# Capture the deterministic TV home state used for 1:1 visual comparisons.
# Path: launch -> Default profile -> DPAD_DOWN x2 (first portrait shelf, first card).
# Usage: tools/tv_state_shot.sh /tmp/out.png
set -u

# Hard guard: never let a device call block the shell. Every adb invocation
# goes through adb_t(), which fails fast instead of hanging on a dead device.
ADB_TIMEOUT=${ADB_TIMEOUT:-25}
adb_t() { timeout "$ADB_TIMEOUT" adb "$@"; }
SERIAL=${SERIAL:-192.168.88.30:5555}
PKG=${PKG:-org.viptv.experiment}
OUT=${1:-/tmp/tv-state.png}
adb_t -s "$SERIAL" shell am force-stop "$PKG" >/dev/null 2>&1
adb_t -s "$SERIAL" shell am start -n "$PKG/.MainActivity" >/dev/null 2>&1
sleep 13
adb_t -s "$SERIAL" shell input keyevent 23 >/dev/null 2>&1
sleep 13
adb_t -s "$SERIAL" shell "input keyevent 20; sleep 0.9; input keyevent 20; sleep 2.5" >/dev/null 2>&1
adb_t -s "$SERIAL" exec-out screencap -p > "$OUT"
echo "$OUT"