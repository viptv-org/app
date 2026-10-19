#!/usr/bin/env bash
# Parse `dumpsys gfxinfo <pkg> framestats` into a per-frame report.
# Reports frame-duration percentiles and the share of frames that missed their
# intended vsync (the metric that decides whether the display stalls).
set -u

# Hard guard: never let a device call block the shell. Every adb invocation
# goes through adb_t(), which fails fast instead of hanging on a dead device.
ADB_TIMEOUT=${ADB_TIMEOUT:-25}
adb_t() { timeout "$ADB_TIMEOUT" adb "$@"; }
SERIAL=${SERIAL:-192.168.88.30:5555}
PKG=${PKG:-org.viptv.experiment}
LABEL=${1:-frames}
adb_t -s "$SERIAL" shell dumpsys gfxinfo "$PKG" framestats > /tmp/framestats.txt 2>/dev/null
python3 - "$LABEL" <<'PY'
import sys, statistics
lines=open('/tmp/framestats.txt', errors='ignore').read().splitlines()
cols=None; rows=[]
started=False
for ln in lines:
    if ln.startswith('---PROFILEDATA---'):
        started = not started
        cols=None
        continue
    if not started: continue
    if ln.startswith('Flags,'):
        cols=[x for x in ln.strip().split(',') if x != '']; continue
    if not cols: continue
    p=[x for x in ln.strip().split(',') if x != '']
    if len(p)!=len(cols) or not p[0].lstrip('-').isdigit(): continue
    try: vals=[int(x) for x in p]
    except ValueError: continue
    rows.append(dict(zip(cols,vals)))
if not rows:
    print("no frame rows"); sys.exit()
dur=sorted((r['FrameCompleted']-r['IntendedVsync'])/1e6 for r in rows if r['FrameCompleted']>0)
miss=[r for r in rows if r['Vsync']-r['IntendedVsync']>1_000_000]
d=len(dur)
def pct(p): return dur[min(d-1,int(d*p))]
print(f"=== {sys.argv[1]}: frames={len(rows)} missed_vsync={len(miss)} ({100*len(miss)/len(rows):.0f}%)")
print(f"    duration median={statistics.median(dur):.1f}ms p75={pct(.75):.1f} p90={pct(.9):.1f} p99={pct(.99):.1f}")
# phase medians (ms from IntendedVsync)
def phase(a,b):
    xs=[(r[b]-r[a])/1e6 for r in rows if r[a] and r[b]]
    return statistics.median(xs) if xs else 0.0
print(f"    input={phase('IntendedVsync','HandleInputStart'):.1f} "
      f"anim={phase('AnimationStart','PerformTraversalsStart'):.1f} "
      f"layout+record={phase('PerformTraversalsStart','IssueDrawCommandsStart'):.1f} "
      f"submit={phase('IssueDrawCommandsStart','SwapBuffers'):.1f} "
      f"swap_wait={phase('SwapBuffers','FrameCompleted'):.1f}")
PY
