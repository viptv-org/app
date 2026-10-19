#!/usr/bin/env bash
# Build the release APK, install it on the ONN TV and run the frame benchmark.
# Usage: tools/tv_cycle.sh <label> [bench-cycles]
set -eu
cd "$(dirname "$0")/.."
LABEL=${1:-cycle}
CYCLES=${2:-3}
./gradlew :androidApp:assembleRelease --max-workers=2 --no-daemon -q
adb_t -s "$SERIAL" install -r androidApp/build/outputs/apk/release/androidApp-release.apk >/dev/null
tools/tv_bench.sh "$LABEL" "$CYCLES" 0.14