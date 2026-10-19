#!/usr/bin/env bash
# Repeatable TV frame-pacing benchmark for the physical ONN device.
# Deterministic path: force-stop -> launch -> select Default profile ->
# ProfilePin-less home -> DPAD_DOWN x2 lands on the first two portrait shelves.
# Then horizontal focus sweeping inside the shelf, both directions, so the
# sample covers continuous LazyRow scroll animation.
#
# Usage: tools/tv_bench.sh [label] [cycles] [press_delay]
set -u

# Hard guard: never let a device call block the shell. Every adb invocation
# goes through adb_t(), which fails fast instead of hanging on a dead device.
ADB_TIMEOUT=${ADB_TIMEOUT:-25}
adb_t() { timeout "$ADB_TIMEOUT" adb "$@"; }
SERIAL=${SERIAL:-192.168.88.30:5555}
PKG=${PKG:-org.viptv.experiment}
LABEL=${1:-bench}
CYCLES=${2:-4}
DELAY=${3:-0.14}
STEP=${STEP:-8}

adb_t -s "$SERIAL" shell am force-stop "$PKG" >/dev/null 2>&1
adb_t -s "$SERIAL" shell am start -n "$PKG/.MainActivity" >/dev/null 2>&1
sleep 13
adb_t -s "$SERIAL" shell input keyevent 23 >/dev/null 2>&1   # select Default profile
sleep 13
adb_t -s "$SERIAL" shell "input keyevent 20; sleep 0.8; input keyevent 20; sleep 1.5" >/dev/null 2>&1
adb_t -s "$SERIAL" exec-out screencap -p > /tmp/bench-state.png 2>/dev/null
adb_t -s "$SERIAL" shell dumpsys gfxinfo "$PKG" reset >/dev/null 2>&1
i=0
while [ "$i" -lt "$CYCLES" ]; do
  adb_t -s "$SERIAL" shell "j=0; while [ \$j -lt $STEP ]; do input keyevent 22; j=\$((j+1)); sleep $DELAY; done; sleep 0.5
j=0; while [ \$j -lt $STEP ]; do input keyevent 21; j=\$((j+1)); sleep $DELAY; done; sleep 0.5" >/dev/null 2>&1
  i=$((i+1))
done
sleep 1.5
echo "=== $LABEL (cycles=$CYCLES delay=$DELAY step=$STEP)"
adb_t -s "$SERIAL" shell dumpsys gfxinfo "$PKG" | grep -E "Total frames|Janky frames:|50th|90th|99th|Missed Vsync|Slow UI thread|Slow issue draw" | sed 's/^ *//'