#!/usr/bin/env bash
# Build the release APK, install it on the TV and run the frame benchmark.
# Usage: tools/tv_cycle.sh <label> [ab-bursts]
#
# R3: perf claims come from a RELEASE build measured with tv_ab.sh (interleaved
# A/B, missed-vsync rate), not from tv_bench.sh's percentiles.
set -eu
cd "$(dirname "$0")/.."

# Every device call is hard-timeout guarded (project rule R4). These helpers were
# previously referenced but never defined, so the script died before installing.
ADB_TIMEOUT=${ADB_TIMEOUT:-25}
adb_t() { timeout "$ADB_TIMEOUT" adb "$@"; }
SERIAL=${SERIAL:-192.168.88.30:5555}
PKG=${PKG:-org.viptv.experiment}

LABEL=${1:-cycle}
BURSTS=${2:-5}

./gradlew :androidApp:assembleRelease --max-workers=2 --no-daemon -q
adb_t -s "$SERIAL" install -r androidApp/build/outputs/apk/release/androidApp-release.apk >/dev/null
# tv_ab.sh launches the installed package itself and reports missed-vsync rate.
SERIAL="$SERIAL" PKG="$PKG" tools/tv_ab.sh "$LABEL" "$BURSTS" 16
