import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET


NATIVE_INPUTS = (
    "native/core", "TORRENT_RUNTIME_REF", "tools/build_engine.py",
    "tools/build_desktop_torrent.py", "tools/playback_gateway.py",
    "tools/source_archive.py", "tools/macos_signing.py",
    ".github/workflows/desktop-regression.yml",
)


def classes_in(root):
    found = set()
    for source_set in ("desktopTest", "commonTest"):
        for path in (root / "composeApp/src" / source_set / "kotlin").rglob("*Test.kt"):
            text = path.read_text()
            package = re.search(r"^package\s+([\w.]+)", text, re.M)
            if package:
                for name in re.findall(r"^(?:(?:internal|public|open|abstract)\s+)*class\s+(\w+)", text, re.M):
                    if name == path.stem:
                        found.add(f"{package[1]}.{name}")
    return found


def select_classes(raw, available):
    requested = list(dict.fromkeys(filter(None, re.split(r"[\s,]+", raw.strip()))))
    if not 1 <= len(requested) <= 8 or len(raw) > 2000:
        raise ValueError("Select between one and eight exact test classes.")
    if any(not re.fullmatch(r"[A-Za-z_]\w*(?:\.[A-Za-z_]\w*)+", name) or name not in available for name in requested):
        raise ValueError("Every selector must name an existing fully-qualified test class; wildcards and methods are not accepted.")
    guards = sorted(name for name in available if name.endswith(("ScanTest", "GuardTest")))
    return requested, list(dict.fromkeys(requested + guards))


def git(root, *args):
    return subprocess.check_output(["git", "-C", str(root), *args], text=True).strip()


def native_key(inputs, image, architecture):
    if not image or not architecture:
        raise ValueError("Hosted runner image and architecture are required.")
    data = json.dumps([inputs, image, architecture], sort_keys=True).encode()
    return "desktop-regression-native-v1-" + hashlib.sha256(data).hexdigest()


def prepare(root, output, raw):
    requested, filters = select_classes(raw, classes_in(root))
    inputs = {path: git(root, "rev-parse", f"HEAD:{path}") for path in NATIVE_INPUTS}
    metadata = {
        "source": git(root, "rev-parse", "HEAD"),
        "requested": requested,
        "filters": filters,
        "native_inputs": inputs,
        "native_key": native_key(inputs, os.environ.get("ImageOS"), os.environ.get("RUNNER_ARCH")),
        "qualification": "targeted desktop regression only",
    }
    output.mkdir(parents=True, exist_ok=True)
    (output / "selection.json").write_text(json.dumps(metadata, indent=2) + "\n")
    (output / "filters.txt").write_text("\n".join(filters) + "\n")
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a") as stream:
            stream.write(f"native_key={metadata['native_key']}\n")
    print(json.dumps(metadata, indent=2))


def test_results(directory, filters):
    counts = {name: {"tests": 0, "skipped": 0, "failed": 0} for name in filters}
    unexpected = set()
    for path in directory.glob("TEST-*.xml"):
        for case in ET.parse(path).getroot().iter("testcase"):
            name = case.get("classname")
            if name not in counts:
                unexpected.add(name)
                continue
            count = counts[name]
            count["tests"] += 1
            count["skipped"] += case.find("skipped") is not None
            count["failed"] += case.find("failure") is not None or case.find("error") is not None
    passed = not unexpected and all(c["tests"] > c["skipped"] and c["failed"] == 0 for c in counts.values())
    return {"classes": counts, "unexpected_classes": sorted(str(n) for n in unexpected), "passed": passed}


def report(root, output):
    selection = json.loads((output / "selection.json").read_text())
    result = test_results(root / "composeApp/build/test-results/desktopTest", selection["filters"])
    result.update(source=selection["source"], qualification=selection["qualification"],
                  native_cache=os.environ.get("NATIVE_CACHE_HIT", "unknown"))
    (output / "result.json").write_text(json.dumps(result, indent=2) + "\n")
    summary = ["## Targeted desktop regression", "", f"Source: `{selection['source']}`", "",
               "This result does not replace full PR or edge qualification.", "",
               f"Native cache hit: `{result['native_cache']}`; native inputs are recorded in selection.json.", "",
               "| Class | Cases | Skipped | Failed |", "| --- | ---: | ---: | ---: |"]
    summary += [f"| `{name}` | {c['tests']} | {c['skipped']} | {c['failed']} |" for name, c in result["classes"].items()]
    summary += ["", f"Selection result: {'PASS' if result['passed'] else 'FAIL (missing, skipped-only, failed or unexpected cases)'}", ""]
    text = "\n".join(summary)
    (output / "summary.md").write_text(text)
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as stream:
            stream.write(text)
    print(text)
    return 0 if result["passed"] else 1


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=("prepare", "report"))
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    try:
        if args.action == "prepare":
            prepare(root, args.output, os.environ.get("REGRESSION_CLASSES", ""))
        else:
            raise SystemExit(report(root, args.output))
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        parser.exit(2, f"{error}\n")
