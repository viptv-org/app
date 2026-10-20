#!/usr/bin/env python3
"""CPU + frame benchmark for the KMP app on the served Android TV emulator.

The emulator renders with swiftshader, so GPU work runs on the host inside the
qemu process. Frame numbers alone therefore miss most of the cost: this script
records host qemu CPU, guest CPU, app-process CPU and gfxinfo frame stats for a
fixed sequence of windows, so two builds can be compared like for like.

Usage: tools/tv_emu_perf.py <label> [--out DIR] [--serial emulator-5580] [--reps N]

Windows (each one is measured separately):
  startup  force-stop, `am start -W`, then 25 s without input (lands on Home)
  idle_hero  20 s on Home as launched (hero carousel), no input
  idle_shelf 20 s with a shelf card focused, no input (ideal: 0 frames)
  hscroll  3 bursts x 16 D-pad presses right/left on a paging shelf
  vscroll  Down x6 then Up x6 at 0.45 s (row-to-row, pulls in new art)
  idle_back  15 s without input after Up x6 (back at the top of Home)

Every device call has a timeout. uiautomator (which wakes accessibility and
costs CPU) is used only while setting up, never inside a measured window.
Raw gfxinfo dumps and a JSON summary are written to --out.
"""
import argparse
import json
import os
import re
import statistics
import subprocess
import sys
import time

ADB = os.environ.get("ADB", "/home/vm/.local/android-sdk/platform-tools/adb")
PKG = os.environ.get("PKG", "org.viptv.experiment")
HZ = os.sysconf("SC_CLK_TCK")


def adb(serial, *args, timeout=30):
    try:
        return subprocess.run([ADB, "-s", serial, *args], capture_output=True,
                              text=True, timeout=timeout).stdout
    except subprocess.TimeoutExpired:
        return ""


def sh(serial, cmd, timeout=60):
    return adb(serial, "shell", cmd, timeout=timeout)


def qemu_pid(serial):
    port = serial.split("-")[-1]
    out = subprocess.run(["pgrep", "-f", f"qemu-system.*-port {port}"],
                         capture_output=True, text=True).stdout.split()
    return int(out[0]) if out else None


def host_ticks(pid):
    f = open(f"/proc/{pid}/stat").read().rsplit(")", 1)[1].split()
    return int(f[11]) + int(f[12])  # utime + stime


def guest_snapshot(serial, app_pid):
    # Each tracked process is tagged with the name pidof found it by: the
    # comm in /proc/<pid>/stat can be a thread name (media.swcodec shows
    # "binder:583_2"), so it cannot identify the process.
    out = sh(serial, f"head -1 /proc/stat; echo app $(cat /proc/{app_pid}/stat 2>/dev/null); "
                     "for n in surfaceflinger screenrecord media.swcodec; do "
                     "for p in $(pidof $n); do echo $n $(cat /proc/$p/stat); done; done")
    lines = out.strip().splitlines()
    cpu = [int(x) for x in lines[0].split()[1:]]
    busy, total = sum(cpu) - cpu[3] - cpu[4], sum(cpu)
    app = 0
    procs = {}  # pid -> (name, ticks); screenrecord restarts every few minutes
    for ln in lines[1:]:
        tag, _, stat = ln.partition(" ")
        if ")" not in stat:
            continue
        f = stat.rsplit(")", 1)[1].split()
        t = int(f[11]) + int(f[12])
        if tag == "app":
            app = t
        else:
            procs[stat.split()[0]] = (tag, t)
    return {"busy": busy, "total": total, "app": app, "procs": procs}


def proc_delta(g0, g1, names):
    """Ticks used by the processes called `names` between two snapshots.
    A pid absent from the first snapshot started inside the window, so all of
    its ticks count; a pid that exited is lost (its ticks are not negative).
    The viewer's screenrecord encodes H.264 in media.swcodec, so that process
    is reported too: its load follows the number of frames the app renders."""
    total = 0
    for pid, (name, t) in g1["procs"].items():
        if name not in names:
            continue
        total += t - g0["procs"].get(pid, (name, 0))[1]
    return total


def parse_gfx(txt):
    def g(pat, cast=float):
        m = re.search(pat, txt)
        return cast(m.group(1)) if m else None
    r = {
        "frames": g(r"Total frames rendered: (\d+)", int),
        "janky_pct": g(r"Janky frames: \d+ \(([\d.]+)%\)"),
        "missed_vsync": g(r"Number Missed Vsync: (\d+)", int),
        "slow_ui": g(r"Number Slow UI thread: (\d+)", int),
        "slow_bitmap": g(r"Number Slow bitmap uploads: (\d+)", int),
        "slow_draw": g(r"Number Slow issue draw commands: (\d+)", int),
    }
    for p in (50, 90, 95, 99):
        r[f"p{p}"] = g(rf"\n{p}th percentile: (\d+)ms", int)
        r[f"gpu_p{p}"] = g(rf"{p}th gpu percentile: (\d+)ms", int)
    return r


def measure(ctx, name, action):
    s, qpid, app_pid = ctx["serial"], ctx["qpid"], ctx["app_pid"]
    sh(s, f"dumpsys gfxinfo {PKG} reset")
    h0, g0, t0 = host_ticks(qpid), guest_snapshot(s, app_pid), time.time()
    action()
    h1, g1, t1 = host_ticks(qpid), guest_snapshot(s, app_pid), time.time()
    txt = sh(s, f"dumpsys gfxinfo {PKG}")
    open(os.path.join(ctx["out"], f"{name}.gfxinfo.txt"), "w").write(txt)
    dt = t1 - t0
    gt = max(g1["total"] - g0["total"], 1)
    res = {
        "window": name,
        "seconds": round(dt, 2),
        "host_qemu_cpu_pct": round(100 * (h1 - h0) / HZ / dt, 1),
        "guest_cpu_pct_of_all": round(100 * (g1["busy"] - g0["busy"]) / gt, 1),
        "app_cpu_pct": round(100 * (g1["app"] - g0["app"]) / HZ / dt, 1),
        "surfaceflinger_cpu_pct": round(100 * proc_delta(g0, g1, ("surfaceflinger",)) / HZ / dt, 1),
        "screenrecord_cpu_pct": round(100 * proc_delta(g0, g1, ("screenrecord",)) / HZ / dt, 1),
        "swcodec_cpu_pct": round(100 * proc_delta(g0, g1, ("media.swcodec",)) / HZ / dt, 1),
    }
    res.update(parse_gfx(txt))
    if res["frames"] is not None:
        res["fps"] = round(res["frames"] / dt, 1)
        if res["frames"]:
            # host CPU per rendered frame: the swiftshader raster cost of one frame
            res["qemu_ms_per_frame"] = round(1000 * (h1 - h0) / HZ / res["frames"], 1)
    print(json.dumps(res), flush=True)
    return res


def dump_focus(s):
    sh(s, "uiautomator dump /sdcard/tvperf.xml >/dev/null 2>&1")
    return sh(s, "cat /sdcard/tvperf.xml")


def focused(xml):
    for node in re.findall(r"<node[^>]*>", xml):
        if 'focused="true"' in node:
            b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
            d = re.search(r'content-desc="([^"]*)"', node)
            if b:
                x1, y1, x2, y2 = map(int, b.groups())
                return (d.group(1) if d else ""), y2 - y1
    return "", 0


def is_card(desc, h):
    return bool(re.search(r", \d{4}", desc)) and h >= 150


def key(s, code, wait):
    sh(s, f"input keyevent {code}")
    time.sleep(wait)


def reach_home(s):
    for _ in range(8):
        xml = dump_focus(s)
        if re.search(r"Continue Watching|My List|Cinemeta", xml):
            return True
        key(s, 23, 6)  # profile picker: pick the focused profile
    return False


def focus_shelf(s):
    for _ in range(10):
        desc, h = focused(dump_focus(s))
        if not is_card(desc, h):
            key(s, 20, 1.0)
            continue
        key(s, 22, 1.2)
        probe, _ = focused(dump_focus(s))
        key(s, 21, 1.0)
        if probe and probe != desc:
            return desc
        key(s, 20, 1.2)
    return None


def run_once(args, rep):
    s = args.serial
    out = os.path.join(args.out, f"rep{rep}")
    os.makedirs(out, exist_ok=True)
    qpid = qemu_pid(s)
    sh(s, f"am force-stop {PKG}")
    time.sleep(3)
    h0, t0 = host_ticks(qpid), time.time()
    g0 = guest_snapshot(s, 0)
    start = sh(s, f"am start -W -n {PKG}/.MainActivity", timeout=90)
    total_time = re.search(r"TotalTime: (\d+)", start)
    app_pid = sh(s, f"pidof {PKG}").strip().split()[0]
    time.sleep(25)
    h1, t1 = host_ticks(qpid), time.time()
    g1 = guest_snapshot(s, app_pid)
    txt = sh(s, f"dumpsys gfxinfo {PKG}")
    open(os.path.join(out, "startup.gfxinfo.txt"), "w").write(txt)
    dt = t1 - t0
    startup = {
        "window": "startup", "seconds": round(dt, 2),
        "am_start_total_ms": int(total_time.group(1)) if total_time else None,
        "host_qemu_cpu_pct": round(100 * (h1 - h0) / HZ / dt, 1),
        "guest_cpu_pct_of_all": round(100 * (g1["busy"] - g0["busy"]) / max(g1["total"] - g0["total"], 1), 1),
        "app_cpu_pct": round(100 * g1["app"] / HZ / dt, 1),
    }
    startup.update(parse_gfx(txt))
    print(json.dumps(startup), flush=True)

    if not reach_home(s):
        print("ABORT: Home never rendered", file=sys.stderr)
        return None
    ctx = {"serial": s, "qpid": qpid, "app_pid": app_pid, "out": out}
    time.sleep(5)
    results = [startup]
    results.append(measure(ctx, "idle_hero", lambda: time.sleep(20)))
    shelf = focus_shelf(s)
    if not shelf:
        open(os.path.join(out, "abort.uiautomator.xml"), "w").write(dump_focus(s))
        print("ABORT: no paging shelf", file=sys.stderr)
        return None
    print(f"# shelf={shelf!r}", flush=True)
    time.sleep(6)  # let uiautomator/accessibility and image loads settle

    results.append(measure(ctx, "idle_shelf", lambda: time.sleep(20)))
    burst = ("j=0; while [ $j -lt 8 ]; do input keyevent 22; j=$((j+1)); sleep 0.12; done; "
             "j=0; while [ $j -lt 8 ]; do input keyevent 21; j=$((j+1)); sleep 0.12; done; sleep 1")
    results.append(measure(ctx, "hscroll", lambda: [sh(s, burst) for _ in range(3)]))
    vert = ("j=0; while [ $j -lt 6 ]; do input keyevent 20; j=$((j+1)); sleep 0.45; done; "
            "j=0; while [ $j -lt 6 ]; do input keyevent 19; j=$((j+1)); sleep 0.45; done; sleep 1.5")
    results.append(measure(ctx, "vscroll", lambda: sh(s, vert)))
    results.append(measure(ctx, "idle_back", lambda: time.sleep(15)))
    for r in results:
        r["shelf"] = shelf
    return results


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("label")
    ap.add_argument("--serial", default="emulator-5580")
    ap.add_argument("--out")
    ap.add_argument("--reps", type=int, default=3)
    args = ap.parse_args()
    args.out = args.out or f"/tmp/tvperf/{args.label}"
    os.makedirs(args.out, exist_ok=True)
    s = args.serial
    apk = sh(s, f"pm path {PKG}").strip().replace("package:", "").splitlines()[0]
    meta = {
        "label": args.label, "serial": s, "time": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
        "apk_sha256": sh(s, f"sha256sum {apk}").split()[0],
        "pkg_flags": re.findall(r"pkgFlags=\[[^\]]*\]", sh(s, f"dumpsys package {PKG}"))[:1],
        "screenrecord_procs": len(sh(s, "pidof screenrecord").split()),
        "host_loadavg": open("/proc/loadavg").read().split()[:3],
    }
    print(json.dumps(meta), flush=True)
    reps = []
    attempt = 0
    while len(reps) < args.reps and attempt < args.reps + 2:
        # A rep that cannot reach Home or a paging shelf is retried, so every
        # label gets the same number of valid reps (an abort is not a result).
        res = run_once(args, attempt)
        attempt += 1
        if res:
            reps.append(res)
    summary = {}
    for w in ("startup", "idle_hero", "idle_shelf", "hscroll", "vscroll", "idle_back"):
        rows = [x for rep in reps for x in rep if x["window"] == w]
        keys = [k for k in rows[0] if isinstance(rows[0][k], (int, float))] if rows else []
        summary[w] = {k: round(statistics.median([x[k] for x in rows if x.get(k) is not None]), 2)
                      for k in keys if any(x.get(k) is not None for x in rows)}
    json.dump({"meta": meta, "reps": reps, "median": summary},
              open(os.path.join(args.out, "summary.json"), "w"), indent=1)
    print("\n=== median over", len(reps), "reps (", attempt, "attempts ) ===")
    cols = ["host_qemu_cpu_pct", "guest_cpu_pct_of_all", "app_cpu_pct", "surfaceflinger_cpu_pct",
            "frames", "fps", "qemu_ms_per_frame", "janky_pct", "missed_vsync", "p50", "p90", "p99", "gpu_p50"]
    print("window     " + " ".join(f"{c[:12]:>12}" for c in cols))
    for w, m in summary.items():
        print(f"{w:10} " + " ".join(f"{m.get(c, ''):>12}" for c in cols))


if __name__ == "__main__":
    main()
