#!/usr/bin/env bash
# Trace the exact same scenario as tv_bench.sh and pull the atrace output.
# Usage: tools/tv_trace.sh [out] [presses] [delay]
set -u

# Hard guard: never let a device call block the shell. Every adb invocation
# goes through adb_t(), which fails fast instead of hanging on a dead device.
ADB_TIMEOUT=${ADB_TIMEOUT:-25}
adb_t() { timeout "$ADB_TIMEOUT" adb "$@"; }
SERIAL=${SERIAL:-192.168.88.30:5555}
PKG=${PKG:-org.viptv.experiment}
OUT=${1:-/tmp/tv_trace.txt}
PRESSES=${2:-40}
DELAY=${3:-0.14}

adb_t -s "$SERIAL" shell am force-stop "$PKG" >/dev/null 2>&1
adb_t -s "$SERIAL" shell am start -n "$PKG/.MainActivity" >/dev/null 2>&1
sleep 13
adb_t -s "$SERIAL" shell input keyevent 23 >/dev/null 2>&1
sleep 13
adb_t -s "$SERIAL" shell "input keyevent 20; sleep 0.8; input keyevent 20; sleep 1.5" >/dev/null 2>&1
adb_t -s "$SERIAL" exec-out screencap -p > /tmp/bench-state.png 2>/dev/null
adb_t -s "$SERIAL" shell "atrace -t 8 -b 65536 -a $PKG view gfx -o /data/local/tmp/tv_trace.txt" >/dev/null 2>&1 &
sleep 1.2
adb_t -s "$SERIAL" shell "i=0; n=0; while [ \$i -lt $PRESSES ]; do if [ \$((i % 16)) -lt 8 ]; then k=22; else k=21; fi; input keyevent \$k; i=\$((i+1)); sleep $DELAY; done" >/dev/null 2>&1
sleep 6
adb_t -s "$SERIAL" pull /data/local/tmp/tv_trace.txt "$OUT" >/dev/null 2>&1
echo "pulled $(wc -l < "$OUT") lines -> $OUT"