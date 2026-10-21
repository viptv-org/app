#!/usr/bin/env bash
# Reliable A/B frame benchmark: one app session, several measured bursts,
# aggregated frame-duration percentiles and missed-vsync rate.
# Usage: tools/tv_ab.sh <label> [bursts] [presses_per_burst] [focus_mode]
#   focus_mode=shelf (default) walks Down to a shelf that provably pages.
#   focus_mode=blind reproduces the pre-2026-10-20 behaviour (Down x2 from the
#   hero) and exists ONLY to show why its numbers are not comparable: which
#   control it lands on is whatever the randomised Home puts there.
set -u

# Hard guard: never let a device call block the shell. Every adb invocation
# goes through adb_t(), which fails fast instead of hanging on a dead device.
ADB_TIMEOUT=${ADB_TIMEOUT:-25}
adb_t() { timeout "$ADB_TIMEOUT" adb "$@"; }
SERIAL=${SERIAL:-192.168.88.30:5555}
PKG=${PKG:-org.viptv.experiment}
LABEL=${1:-ab}
BURSTS=${2:-5}
PRESSES=${3:-16}
FOCUS_MODE=${4:-shelf}
# PATTERN=horizontal (default) pages one shelf right/left; PATTERN=vertical
# crosses rows (down/up) — the row-swap + backdrop-swap path.
PATTERN=${5:-horizontal}
# CADENCE: seconds between presses for horizontal bursts. 0.12 pages faster than
# the billboard can follow (outline-only); 0.6 lets each swap load + play out.
CADENCE=${CADENCE:-0.12}
FOCUS_HELPER="$(cd "$(dirname "$0")" && pwd)/tv_focus_node.py"

adb_t -s "$SERIAL" shell am force-stop "$PKG" >/dev/null 2>&1
adb_t -s "$SERIAL" shell am start -n "$PKG/.MainActivity" >/dev/null 2>&1
sleep 13

# A cold start lands on the profile picker. Until Home has rendered, every burst
# reports 4950 ms / 0 frames, which is not a measurement. Confirm Home (a shelf
# heading is on screen) before sampling, retrying the profile OK press.
on_home() {
  adb_t -s "$SERIAL" shell uiautomator dump /sdcard/ab_home.xml >/dev/null 2>&1
  adb_t -s "$SERIAL" shell cat /sdcard/ab_home.xml 2>/dev/null | grep -q "Continue Watching\|Cinemeta\|My List"
}
i=0
while [ "$i" -lt 6 ]; do
  adb_t -s "$SERIAL" shell input keyevent 23 >/dev/null 2>&1
  sleep 6
  if on_home; then break; fi
  i=$((i+1))
done
if ! on_home; then
  echo "=== $LABEL: ABORTED - Home never rendered (app not on a scrolling screen) ===" >&2
  exit 2
fi
sleep 7

# Focus a KNOWN shelf. Pressing Right/Left only measures anything when the
# focused control is a content card that scrolls its row; on the top bar it just
# walks tabs and every burst reports ~0 frames. So: walk Down until the focused
# node looks like a media card, then prove the row pages (Right must change the
# focused card). Abort instead of reporting a number otherwise.
# A focused node in a uiautomator dump looks like
#   <node ... content-desc="Some Title, 2024" focused="true" bounds="[96,564][328,988]"/>
# -> "x1<TAB>y1<TAB>x2<TAB>y2<TAB>desc" (numbers first: titles contain spaces).
#
# Shape, not position, identifies a card: an earlier pass gated on "y >= 400",
# which aborted on a legitimate Home because the *x* of the first card is 96 and
# the field order had been read transposed. A card is "title, year" and >= 150
# px tall; the nav tabs, filter chips and shelf headings are none of those.
# A row with a single item (a one-title My List) also cannot page, so a failed
# Right probe moves down to the next row instead of giving up.
focused_node() {
  adb_t -s "$SERIAL" shell uiautomator dump /sdcard/ab_home.xml >/dev/null 2>&1
  adb_t -s "$SERIAL" shell cat /sdcard/ab_home.xml 2>/dev/null | python3 "$FOCUS_HELPER"
}
is_card() { # desc, height
  case "$1" in *", "[0-9][0-9][0-9][0-9]*) ;;
    *) return 1 ;; esac
  [ "${2:-0}" -ge 150 ]
}
key() { adb_t -s "$SERIAL" shell "input keyevent $1; sleep $2" >/dev/null 2>&1; }
read_node() { # $1 = helper output -> f_x1 f_y1 f_x2 f_y2 f_desc
  IFS=$'\t' read -r f_x1 f_y1 f_x2 f_y2 f_desc <<<"$1"
}

f_x1=""; f_y1=""; f_x2=""; f_y2=""; f_desc=""; probe_desc=""
shelf_ok=0
if [ "$FOCUS_MODE" = blind ]; then
  # Legacy path: no claim about what holds focus. Kept for A/B archaeology only.
  echo "    focus_mode=blind (legacy): NOT a comparable measurement" >&2
  adb_t -s "$SERIAL" shell "input keyevent 20; sleep 0.9; input keyevent 20; sleep 2.5" >/dev/null 2>&1
  node=$(focused_node)
  [ -n "$node" ] && read_node "$node"
  shelf_ok=1
fi
for _step in 1 2 3 4 5 6 7 8 9 10; do
  [ "$shelf_ok" -eq 1 ] && break
  node=$(focused_node)
  [ -n "$node" ] && read_node "$node" || f_desc=""
  if ! is_card "$f_desc" "$(( ${f_y2:-0} - ${f_y1:-0} ))"; then
    key 20 1.0
    continue
  fi
  key 22 1.2
  probe=$(focused_node)
  probe_desc=""
  [ -n "$probe" ] && IFS=$'\t' read -r _px _py _px2 _py2 probe_desc <<<"$probe"
  key 21 1.0
  if [ -n "$probe_desc" ] && [ "$probe_desc" != "$f_desc" ]; then
    shelf_ok=1
    break
  fi
  # Single-item row: nothing to page. Try the shelf below.
  key 20 1.2
done
if [ "$shelf_ok" -ne 1 ]; then
  echo "=== $LABEL: ABORTED - no paging shelf found (last focused: '${f_desc:-none}' at ${f_x1:-?},${f_y1:-?}) ===" >&2
  exit 3
fi
if [ "$FOCUS_MODE" = blind ]; then
  echo "    focus='${f_desc:-unknown}' (blind: paging not asserted)"
else
  echo "    shelf='$f_desc' -> pages to '$probe_desc'"
fi
i=0
while [ "$i" -lt "$BURSTS" ]; do
  adb_t -s "$SERIAL" shell dumpsys gfxinfo "$PKG" reset >/dev/null 2>&1
  if [ "$PATTERN" = vertical ]; then
    # Three row descents (row swap + backdrop swap each, cadence lets the
    # 350 ms row motion finish), then Up to the billboard and Down back to
    # the first shelf: a self-resetting loop that never bottoms out at the
    # last row, where Down is a dead end by design.
    adb_t -s "$SERIAL" shell "j=0; while [ \$j -lt $PRESSES ]; do input keyevent 20; sleep 0.5; input keyevent 20; sleep 0.5; input keyevent 20; sleep 0.5; input keyevent 19; sleep 0.8; input keyevent 20; sleep 0.8; j=\$((j+1)); done" >/dev/null 2>&1
  else
    adb_t -s "$SERIAL" shell "j=0; while [ \$j -lt $((PRESSES/2)) ]; do input keyevent 22; j=\$((j+1)); sleep $CADENCE; done; j=0; while [ \$j -lt $((PRESSES/2)) ]; do input keyevent 21; j=\$((j+1)); sleep $CADENCE; done" >/dev/null 2>&1
  fi
  sleep 1.0
  adb_t -s "$SERIAL" shell dumpsys gfxinfo "$PKG" > "/tmp/ab_${LABEL}_$i.txt" 2>/dev/null
  i=$((i+1))
done
python3 - "$LABEL" "$BURSTS" "$PATTERN" <<'PY'
import sys, re, statistics
label, n, pattern = sys.argv[1], int(sys.argv[2]), sys.argv[3]
p50=[]; p90=[]; gpu=[]; jank=[]; frames=[]
for i in range(n):
    txt=open(f'/tmp/ab_{label}_{i}.txt',errors='ignore').read()
    def g(pat, cast=float):
        m=re.search(pat, txt)
        return cast(m.group(1)) if m else None
    def pct(p):
        m=re.search(rf"{p}th percentile: (\d+)ms", txt); return int(m.group(1)) if m else None
    d=pct(50); d90=pct(90); gp=g(r"50th gpu percentile: (\d+)ms", int); j=g(r"Janky frames: \d+ \(([\d.]+)%\)", float)
    f=g(r"Total frames rendered: (\d+)", int); mv=g(r"Number Missed Vsync: (\d+)", int)
    if d: p50.append(d)
    if d90: p90.append(d90)
    if gp: gpu.append(gp)
    if j is not None: jank.append(j)
    if f and mv is not None: frames.append((f,mv))
print(f"=== {label}: bursts={n} pattern={pattern}")
if p50:
    print(f"    cpu50 median={statistics.median(p50):.1f}ms  {sorted(p50)}")
    print(f"    cpu90 median={statistics.median(p90):.1f}ms")
    print(f"    gpu50 median={statistics.median(gpu):.1f}ms")
    print(f"    janky={statistics.mean(jank):.1f}%")
    if frames:
        tot=sum(f for f,_ in frames); miss=sum(m for _,m in frames)
        print(f"    frames={tot} missed_vsync={miss} ({100*miss/max(tot,1):.1f}%)")
PY