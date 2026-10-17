#!/usr/bin/env python3
"""Run only on the explicitly owned lifecycle/16KiB/API26 AVDs, never user devices."""
import argparse
import json
from pathlib import Path
import re
import select
import ssl
import subprocess
import time
import urllib.request
import xml.etree.ElementTree as ET


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--sustain-ms", type=int, default=0)
    parser.add_argument("--avd", choices=["viptv-lifecycle-isolated", "viptv-16k-isolated", "viptv-api26-isolated"], default="viptv-lifecycle-isolated")
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--ca", type=Path, required=True)
    parser.add_argument("--origin", required=True)
    parser.add_argument("--serial", default="emulator-5586")
    parser.add_argument("--adb", default="/home/vynxc/Android/Sdk/platform-tools/adb")
    args = parser.parse_args()
    from urllib.parse import urlsplit
    origin = urlsplit(args.origin)
    assert origin.scheme == "https" and origin.hostname == "127.0.0.1" and origin.port
    adb = [args.adb, "-s", args.serial]

    def run(*command):
        expected = "emulator-5592" if args.avd == "viptv-api26-isolated" else "emulator-5586"
        assert args.serial == expected
        observed = subprocess.check_output(adb + ["emu", "avd", "name"], stderr=subprocess.STDOUT).decode().splitlines()[0].strip()
        assert observed == args.avd, f"Owned AVD guard failed: {observed!r}"
        if args.avd == "viptv-api26-isolated":
            assert subprocess.check_output(adb + ["shell", "getprop", "ro.build.version.sdk"]).decode().strip() == "26"
        return subprocess.check_output(adb + list(command), stderr=subprocess.STDOUT).decode()

    name = run("emu", "avd", "name").splitlines()[0].strip()
    expected_serial = "emulator-5592" if args.avd == "viptv-api26-isolated" else "emulator-5586"
    assert args.serial == expected_serial and name == args.avd, f"Refusing to modify non-fixture AVD {name!r}"
    if args.avd == "viptv-api26-isolated":
        assert run("shell", "getprop", "ro.build.version.sdk").strip() == "26"
    assert 0 <= args.sustain_ms <= 360_000
    if args.avd == "viptv-16k-isolated":
        assert run("shell", "getconf", "PAGE_SIZE").strip() == "16384"
    args.root.mkdir(parents=True, exist_ok=True)
    run("reverse", f"tcp:{origin.port}", f"tcp:{origin.port}")
    run("install", "-r", str(args.apk))
    run("shell", "pm", "clear", "org.viptv.experiment")
    if args.avd != "viptv-api26-isolated":
        run("logcat", "-c")
    log_flags = ["-b", "main", "-T", "1"] if args.avd == "viptv-api26-isolated" else []
    tag = "VIPTV_API26_QA" if args.avd == "viptv-api26-isolated" else "VIPTV_LIFECYCLE_QA"
    activity = "Api26LifecycleQaActivity" if args.avd == "viptv-api26-isolated" else "LifecycleQaActivity"
    launch = ["shell", "am", "start", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER",
              "-f", "0x10200000", "-n", f"org.viptv.experiment/.{activity}", "--es", "qa_origin", args.origin, "--el", "qa_sustain_ms", str(args.sustain_ms)]
    logs = subprocess.Popen(adb + ["logcat", *log_flags, "-v", "brief", f"{tag}:I", "AndroidRuntime:E", "*:S"],
                            stdout=subprocess.PIPE, bufsize=0)
    lines = []
    passed = False
    captured = False
    elapsed_verified = False
    deadline = time.monotonic() + 180 + args.sustain_ms / 1000
    try:
        (args.root / "launch.txt").write_text(run(*launch[:3], "-W", *launch[3:]))
        while time.monotonic() < deadline:
            if not select.select([logs.stdout], [], [], 1)[0]:
                continue
            line = logs.stdout.readline().decode()
            if not line:
                break
            lines.append(line)
            if "capture_elapsed expected=" in line:
                expected = re.search(r"expected=([0-9:]+)", line).group(1)
                (args.root / "player-clock.png").write_bytes(subprocess.check_output(adb + ["exec-out", "screencap", "-p"]))
                run("shell", "uiautomator", "dump", "/sdcard/lifecycle-clock.xml")
                xml = run("exec-out", "cat", "/sdcard/lifecycle-clock.xml")
                (args.root / "player-clock.xml").write_text(xml)
                labels = [node.get("text", "") for node in ET.fromstring(xml).iter("node")]
                elapsed_verified = expected in labels
                captured = True
            if "await_home paused=" in line:
                run("shell", "input", "keyevent", "KEYCODE_HOME")
            if "background_paused position_ms=" in line:
                time.sleep(.35)
                run(*launch)
            if ("PASS sdk=26 normal_mode=true profile_selection=true" if args.avd == "viptv-api26-isolated" else "PASS profile_focus=true") in line:
                passed = True
                break
            if "FAIL " in line or "FATAL EXCEPTION" in line:
                lines.append(run("logcat", *(["-b", "main"] if args.avd == "viptv-api26-isolated" else []), "-d", "-v", "brief", f"{tag}:I", "AndroidRuntime:E", "*:S"))
                break
    finally:
        logs.terminate()
        logs.wait(timeout=5)
        (args.root / "activity.log").write_text("".join(lines))
    with urllib.request.urlopen(args.origin + "/qa/status", context=ssl.create_default_context(cafile=str(args.ca))) as response:
        status = json.load(response)
    (args.root / "server-status.json").write_text(json.dumps(status, indent=2))
    result = {"passed": passed, "captured": captured, "elapsed_accessibility_verified": elapsed_verified,
              "serial": args.serial, "avd": args.avd, "sustain_requested_ms": args.sustain_ms, "fixture": "synthetic HTTPS facade", "counts": status["counts"]}
    if passed:
        # Direct nonzero-start assertions need a fresh timeline, not a position
        # that has expired from the preceding endurance playlist window.
        reset = urllib.request.Request(args.origin + "/qa/reset-media", data=b"{}", method="POST")
        with urllib.request.urlopen(reset, context=ssl.create_default_context(cafile=str(args.ca))) as response:
            assert response.status == 200
        def direct_start(live, position):
            if args.avd != "viptv-api26-isolated":
                run("logcat", "-c")
            stream = subprocess.Popen(adb + ["logcat", *log_flags, "-v", "brief", "VIPTV_PLAYBACK_QA:I", "AndroidRuntime:E", "*:S"],
                                      stdout=subprocess.PIPE, bufsize=0)
            observed = []
            first_position = None
            timeline = None
            latest_position = None
            try:
                run("shell", "am", "start", "-S", "-n", "org.viptv.experiment/.NativePlaybackQaActivity",
                    "--es", "qa_media_uri", args.origin + "/media/index.m3u8", "--ez", "qa_live", str(live).lower(),
                    "--el", "qa_position_ms", str(position))
                until = time.monotonic() + 35
                while time.monotonic() < until:
                    if not select.select([stream.stdout], [], [], 1)[0]:
                        continue
                    line = stream.stdout.readline().decode()
                    observed.append(line)
                    match = re.search(r"position_ms=(\d+) duration_ms=", line)
                    if match:
                        latest_position = int(match.group(1))
                    if "timeline_placeholder=false" in line:
                        timeline = line
                    if "presentation=NativeRendererPresented" in line:
                        first_position = latest_position
                        break
                    if "failed=true" in line or "FATAL EXCEPTION" in line:
                        break
            finally:
                stream.terminate()
                stream.wait(timeout=5)
                label = "live-edge" if live else "nonzero-start"
                (args.root / f"{label}.log").write_text("".join(observed))
            assert first_position is not None and timeline, "Direct renderer did not present"
            if live:
                assert "timeline_live=true" in timeline and first_position >= 10_000, "True live edge changed"
            else:
                assert "timeline_live=false" in timeline and abs(first_position - position) <= 250, "Nonzero start changed"
            return first_position

        result["nonzero_start_ms"] = direct_start(False, 8_000)
        result["live_edge_start_ms"] = direct_start(True, 0)
    (args.root / "result.json").write_text(json.dumps(result, indent=2))
    print(json.dumps(result), flush=True)
    if not (passed and captured and elapsed_verified):
        raise SystemExit(1)


if __name__ == "__main__":
    main()
