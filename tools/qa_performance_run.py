#!/usr/bin/env python3
"""Diagnostic full-app startup/calendar runs on the disposable named AVD only."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import select
import ssl
import subprocess
import time
import urllib.request
from urllib.parse import urlsplit


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--ca", type=Path, required=True)
    parser.add_argument("--origin", required=True)
    parser.add_argument("--iterations", type=int, default=3)
    parser.add_argument("--cancel-restore", action="store_true")
    parser.add_argument("--warm-activity", action="store_true")
    parser.add_argument("--serial", default="emulator-5586")
    parser.add_argument("--adb", default="/home/vynxc/Android/Sdk/platform-tools/adb")
    args = parser.parse_args()
    parsed = urlsplit(args.origin)
    assert parsed.scheme == "https" and parsed.hostname == "127.0.0.1" and parsed.port
    adb = [args.adb, "-s", args.serial]

    def run(*command):
        return subprocess.check_output(adb + list(command), stderr=subprocess.STDOUT).decode()

    assert run("emu", "avd", "name").splitlines()[0].strip() == "viptv-lifecycle-isolated", "Refusing to modify another AVD"
    args.root.mkdir(parents=True, exist_ok=True)
    run("reverse", f"tcp:{parsed.port}", f"tcp:{parsed.port}")
    run("install", "-r", str(args.apk))
    run("shell", "pm", "clear", "org.viptv.experiment")
    tls = ssl.create_default_context(cafile=str(args.ca))

    def status():
        with urllib.request.urlopen(args.origin + "/qa/status", context=tls) as response:
            return json.load(response)

    samples = []
    modes = ["cold-empty"] + ["cold-restored"] * args.iterations + (["warm-activity"] if args.warm_activity else []) + (["cancel-restored"] if args.cancel_restore else [])
    for index, mode in enumerate(modes):
        before = status()
        previous_pid = None
        if mode == "warm-activity":
            previous_pid = run("shell", "pidof", "org.viptv.experiment").strip()
            run("shell", "am", "start", "--activity-single-top", "-n", "org.viptv.experiment/.PerformanceQaActivity", "--ez", "qa_finish", "true")
            time.sleep(.35)
            assert run("shell", "pidof", "org.viptv.experiment").strip() == previous_pid, "Warm process did not survive Activity finish"
        else:
            run("shell", "am", "force-stop", "org.viptv.experiment")
        run("logcat", "-c")
        stream = subprocess.Popen(adb + ["logcat", "-v", "brief", "VIPTV_PERFORMANCE_QA:I", "AndroidRuntime:E", "ActivityTaskManager:I", "*:S"],
                                  stdout=subprocess.PIPE, text=True, bufsize=1)
        lines = []
        passed = False
        try:
            launch = run("shell", "am", "start", "-W", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER",
                         "-n", "org.viptv.experiment/.PerformanceQaActivity", "--es", "qa_origin", args.origin, "--es", "qa_mode", mode)
            deadline = time.monotonic() + 100
            while time.monotonic() < deadline:
                if not select.select([stream.stdout], [], [], 1)[0]:
                    continue
                line = stream.stdout.readline()
                lines.append(line)
                if "calendar_first_page_ms=" in line:
                    (args.root / f"{index}-calendar-first-page.png").write_bytes(subprocess.check_output(adb + ["exec-out", "screencap", "-p"]))
                if f"PASS mode={mode}" in line:
                    passed = True
                    break
                if "FAIL " in line or "FATAL EXCEPTION" in line:
                    break
        finally:
            stream.terminate()
            stream.wait(timeout=5)
            lines.append(run("logcat", "-d", "-v", "brief", "VIPTV_PERFORMANCE_QA:I", "AndroidRuntime:E", "ActivityTaskManager:I", "*:S"))
            (args.root / f"{index}-{mode}.log").write_text("".join(lines))
            (args.root / f"{index}-{mode}-launch.txt").write_text(launch)
        assert passed, f"App probe failed: {index}-{mode}"
        image_label = "connect" if mode == "cancel-restored" else "home"
        (args.root / f"{index}-{mode}-{image_label}.png").write_bytes(subprocess.check_output(adb + ["exec-out", "screencap", "-p"]))
        after = status()
        sample = after["measurements"][-1]
        assert sample["mode"] == mode
        sample["pid"] = run("shell", "pidof", "org.viptv.experiment").strip()
        if previous_pid is not None:
            assert sample["pid"] == previous_pid, "Warm Activity launch restarted the process"
            sample["previous_pid"] = previous_pid
        sample["requests"] = after["requests"][len(before["requests"]):]
        sample["request_counts"] = {key: value - before["counts"].get(key, 0) for key, value in after["counts"].items() if value != before["counts"].get(key, 0)}
        sample["am_total_ms"] = int(re.search(r"TotalTime:\s*(\d+)", launch).group(1))
        sample["am_wait_ms"] = int(re.search(r"WaitTime:\s*(\d+)", launch).group(1))
        launch_state = re.search(r"LaunchState:\s*(\w+)", launch)
        if launch_state:
            sample["framework_launch_state"] = launch_state.group(1)
        fully = re.findall(r"Fully drawn org\.viptv\.experiment/[^:]+: \+([^\s]+)", "".join(lines))
        if fully:
            duration = re.fullmatch(r"(?:(\d+)m)?(?:(\d+)s)?(?:(\d+)ms)?", fully[-1])
            if duration:
                minutes, seconds, millis = [int(value or 0) for value in duration.groups()]
                sample["system_fully_drawn_ms"] = minutes * 60_000 + seconds * 1000 + millis
        (args.root / f"{index}-{mode}.json").write_text(json.dumps(sample, indent=2))
        samples.append(sample)
    result = {"artifact_sha256": hashlib.sha256(args.apk.read_bytes()).hexdigest(), "device": run("shell", "getprop", "ro.build.fingerprint").strip(),
              "fixture": "Synthetic HTTPS facade; prepared120 Home cards,1200 calendar entries; configured delay in server command",
              "limits": "Software emulator diagnostic measurements; physical-TV reference-target acceptance is not established",
              "samples": samples}
    (args.root / "performance-result.json").write_text(json.dumps(result, indent=2))
    print(json.dumps({"runs": len(samples), "artifact_sha256": result["artifact_sha256"], "output": str(args.root / "performance-result.json")}), flush=True)


if __name__ == "__main__":
    main()
