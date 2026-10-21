#!/usr/bin/env bash
# Force full ahead-of-time compilation of the installed app on the TV.
#
# Why: on the ONN 4K box (4-core 32-bit userspace), the stock JIT/verify path
# leaves the UI thread competing with the JIT compiler. Measured with
# tools/tv_ab.sh (release build, same shelf, 3 runs):
#   stock:  ~9-10 % missed vsync horizontal, ~17 % vertical
#   full AOT: 0.3-0.4 % missed vsync horizontal, ~15-16 % vertical
# The shipped baseline profile (androidApp/src/release/generated/...) narrows
# the gap for everyone else; on this box, full AOT is the measured win.
#
# ART resets compilation state on every install/update, so re-run this after
# each `adb install`. Safe to re-run; takes about a minute.
set -eu
ADB_TIMEOUT=${ADB_TIMEOUT:-25}
adb_t() { timeout "$ADB_TIMEOUT" adb "$@"; }
SERIAL=${SERIAL:-192.168.88.30:5555}
PKG=${PKG:-org.viptv.experiment}

adb_t -s "$SERIAL" shell cmd package compile -f -m speed "$PKG"
adb_t -s "$SERIAL" shell dumpsys package "$PKG" | grep -A2 "Dexopt state"
