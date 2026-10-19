#!/usr/bin/env bash
# Measure VIPTV TV frame pacing with gfxinfo on the physical ONN device.
# Usage: tools/tv_frames.sh <keycode> <count> [delay_seconds] [label]
#   keycodes: 22 = DPAD_RIGHT, 21 = LEFT, 20 = DOWN, 19 = UP
# The app must already be running on the home screen. Nothing is reinstalled.
set -u

# Hard guard: never let a device call block the shell. Every adb invocation
# goes through adb_t(), which fails fast instead of hanging on a dead device.
ADB_TIMEOUT=${ADB_TIMEOUT:-25}
adb_t() { timeout "$ADB_TIMEOUT" adb "$@"; }
SERIAL=${SERIAL:-192.168.88.30:5555}
PKG=${PKG:-org.viptv.experiment}
KEY=${1:-22}
COUNT=${2:-24}
DELAY=${3:-0.12}
LABEL=${4:-nav}

adb_t -s "$SERIAL" shell dumpsys gfxinfo "$PKG" reset >/dev/null 2>&1
adb_t -s "$SERIAL" shell "i=0; while [ \$i -lt $COUNT ]; do input keyevent $KEY; i=\$((i+1)); sleep $DELAY; done"
sleep 1.2
echo "=== $LABEL: key=$KEY count=$COUNT delay=$DELAY"
adb_t -s "$SERIAL" shell dumpsys gfxinfo "$PKG" | grep -E "Total frames|Janky frames|50th|90th|95th|99th|Number Missed Vsync|Number Slow UI thread|Number Slow issue draw|Number Slow bitmap upload|Number Frame deadline missed|HISTOGRAM" | sed 's/^ *//'