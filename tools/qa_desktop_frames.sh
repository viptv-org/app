#!/usr/bin/env bash
# Plays a 1080p60 H.264 fixture through the desktop player QA window on the software path and the GL
# path, and prints the last path/drawn/dropped diagnostic of each run with the drawn ratio.
# Usage: tools/qa_desktop_frames.sh <launcher> [seconds]
# The GL run needs an X display with a hardware GLX driver: Skiko refuses llvmpipe, so under Xvfb
# both runs report path=mpv-software.
set -euo pipefail
launcher=${1:?usage: tools/qa_desktop_frames.sh <launcher> [seconds]}
seconds=${2:-30}
[ -n "${DISPLAY:-}" ] || { echo "DISPLAY is not set" >&2; exit 2; }
out=$(mktemp -d /tmp/viptv-frames.XXXXXX)
ffmpeg -nostdin -hide_banner -loglevel error -f lavfi -i "testsrc2=s=1920x1080:r=60:d=$((seconds + 10))" \
    -c:v libx264 -preset veryfast -pix_fmt yuv420p "$out/1080p60.mp4"
for gl in false true; do
    VIPTV_DESKTOP_PLAYER_QA=1 VIPTV_DESKTOP_QA_URI="file://$out/1080p60.mp4" VIPTV_DESKTOP_QA_SECONDS="$seconds" \
        JAVA_TOOL_OPTIONS="-Dviptv.mpvGl=$gl" "$launcher" > "$out/gl-$gl.log" 2>&1 || true
    line=$(grep 'VIPTV_DESKTOP_QA: path=' "$out/gl-$gl.log" | tail -1 || true)
    [ -n "$line" ] || { echo "viptv.mpvGl=$gl: no diagnostic, see $out/gl-$gl.log"; continue; }
    echo "viptv.mpvGl=$gl ${line#VIPTV_DESKTOP_QA: }"
    echo "$line" | tr ' ' '\n' | awk -F= '$1=="drawn"{d=$2} $1=="position_ms"{p=$2}
        END { f=p*60/1000; if (f>0) printf "  drawn %d of %d source frames played (%.1f%%), target >= 99%%\n", d, f, 100*d/f }'
done
echo "logs: $out"
