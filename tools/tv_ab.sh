#!/usr/bin/env bash
# Reliable A/B frame benchmark: one app session, several measured bursts,
# aggregated frame-duration percentiles and missed-vsync rate.
# Usage: tools/tv_ab.sh <label> [bursts] [presses_per_burst]
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

adb_t -s "$SERIAL" shell am force-stop "$PKG" >/dev/null 2>&1
adb_t -s "$SERIAL" shell am start -n "$PKG/.MainActivity" >/dev/null 2>&1
sleep 13
adb_t -s "$SERIAL" shell input keyevent 23 >/dev/null 2>&1
sleep 13
adb_t -s "$SERIAL" shell "input keyevent 20; sleep 0.9; input keyevent 20; sleep 2.5" >/dev/null 2>&1
i=0
while [ "$i" -lt "$BURSTS" ]; do
  adb_t -s "$SERIAL" shell dumpsys gfxinfo "$PKG" reset >/dev/null 2>&1
  adb_t -s "$SERIAL" shell "j=0; while [ \$j -lt $((PRESSES/2)) ]; do input keyevent 22; j=\$((j+1)); sleep 0.12; done; j=0; while [ \$j -lt $((PRESSES/2)) ]; do input keyevent 21; j=\$((j+1)); sleep 0.12; done" >/dev/null 2>&1
  sleep 1.0
  adb_t -s "$SERIAL" shell dumpsys gfxinfo "$PKG" > "/tmp/ab_${LABEL}_$i.txt" 2>/dev/null
  i=$((i+1))
done
python3 - "$LABEL" "$BURSTS" <<'PY'
import sys, re, statistics
label, n = sys.argv[1], int(sys.argv[2])
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
print(f"=== {label}: bursts={n}")
if p50:
    print(f"    cpu50 median={statistics.median(p50):.1f}ms  {sorted(p50)}")
    print(f"    cpu90 median={statistics.median(p90):.1f}ms")
    print(f"    gpu50 median={statistics.median(gpu):.1f}ms")
    print(f"    janky={statistics.mean(jank):.1f}%")
    if frames:
        tot=sum(f for f,_ in frames); miss=sum(m for _,m in frames)
        print(f"    frames={tot} missed_vsync={miss} ({100*miss/max(tot,1):.1f}%)")
PY