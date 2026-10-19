#!/usr/bin/env bash
# Real display-cadence measurement from SurfaceFlinger frame timestamps.
# Reports presented frames and the distribution of consecutive present gaps
# (16.6ms = perfect 60fps cadence, 33ms = one dropped frame).
set -u

# Hard guard: never let a device call block the shell. Every adb invocation
# goes through adb_t(), which fails fast instead of hanging on a dead device.
ADB_TIMEOUT=${ADB_TIMEOUT:-25}
adb_t() { timeout "$ADB_TIMEOUT" adb "$@"; }
SERIAL=${SERIAL:-192.168.88.30:5555}
PKG=${PKG:-org.viptv.experiment}
LAYER=${LAYER:-$(adb -s ${SERIAL:-192.168.88.30:5555} shell "dumpsys SurfaceFlinger --list" | grep -E "^org.viptv.experiment/org.viptv.experiment.MainActivity" | tail -1 | tr -d "\r")}
PRESSES=${1:-60}
DELAY=${2:-0.09}
LABEL=${3:-cadence}

adb_t -s "$SERIAL" shell dumpsys SurfaceFlinger --latency-clear "$LAYER" >/dev/null 2>&1
adb_t -s "$SERIAL" shell "i=0; while [ \$i -lt $PRESSES ]; do if [ \$((i % 16)) -lt 8 ]; then k=22; else k=21; fi; input keyevent \$k; i=\$((i+1)); sleep $DELAY; done" >/dev/null 2>&1
sleep 1.5
adb_t -s "$SERIAL" shell dumpsys SurfaceFlinger --latency "$LAYER" > /tmp/cadence.txt 2>/dev/null
python3 - "$LABEL" <<'PY'
import sys, statistics
rows=[]
for ln in open('/tmp/cadence.txt'):
    p=ln.split()
    if len(p)!=3: continue
    try: a,b,c=(int(x) for x in p)
    except ValueError: continue
    if a==0 or b==0 or c==0: continue
    rows.append((a,b,c))
rows=[r for r in rows if r[1] > 4.7e13]  # keep monotonic present timestamps only
if len(rows)<5:
    print("no frames"); sys.exit()
rows.sort(key=lambda r:r[1])
present=[r[1]/1e6 for r in rows]
gaps=[present[i+1]-present[i] for i in range(len(present)-1)]
gaps=[g for g in gaps if g < 200]  # ignore idle gaps between presses
ok=sum(1 for g in gaps if g<24)
dropped=sum(max(round(g/16.6667)-1,0) for g in gaps)
print(f"=== {sys.argv[1]}: presents={len(rows)} intervals={len(gaps)}")
print(f"    clean16ms={100*ok/max(len(gaps),1):.0f}% dropped_vsyncs={dropped} "
      f"median_gap={statistics.median(gaps):.1f}ms p90={sorted(gaps)[int(len(gaps)*.9)]:.1f}ms p99={sorted(gaps)[int(len(gaps)*.99)]:.1f}ms")
PY