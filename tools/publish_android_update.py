#!/usr/bin/env python3
"""Publish an Android APK as the app's update feed (docs/APP_UPDATES.md).

    python3 tools/publish_android_update.py <apk> <feed-dir> --base-url <origin>/updates/android [--notes TEXT] [--min-supported CODE] [--keep N]

Reads the APK's package, versionCode and versionName with `aapt2 dump badging`,
copies it to <feed-dir>/viptv-<versionCode>.apk and then writes
<feed-dir>/update.json, both through a temporary file and a rename, so a client
never sees a feed that points at a missing or half-written APK. The feed's `url`
is absolute: --base-url plus the APK name. Older viptv-*.apk files beyond the
newest --keep (default 2) are removed.

Serve <feed-dir> at <server origin>/updates/android/.
"""
import argparse
import glob
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys

PACKAGE = "org.viptv.experiment"


def find_aapt2():
    found = shutil.which("aapt2")
    if found:
        return found
    for sdk in (os.environ.get("ANDROID_HOME"), os.environ.get("ANDROID_SDK_ROOT"),
                os.path.expanduser("~/.local/android-sdk"), os.path.expanduser("~/Android/Sdk")):
        if not sdk:
            continue
        candidates = sorted(glob.glob(os.path.join(sdk, "build-tools", "*", "aapt2")))
        if candidates:
            return candidates[-1]
    sys.exit("aapt2 not found: set ANDROID_HOME or put aapt2 on PATH")


def badging(apk):
    out = subprocess.run([find_aapt2(), "dump", "badging", apk], check=True, capture_output=True, text=True).stdout
    match = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']*)'", out, re.M)
    if not match:
        sys.exit("could not read the package line from aapt2 dump badging")
    return match.group(1), int(match.group(2)), match.group(3)


def sha256(path):
    digest = hashlib.sha256()
    with open(path, "rb") as source:
        for block in iter(lambda: source.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def write_atomically(path, data):
    temporary = path + ".tmp"
    with open(temporary, "wb") as target:
        target.write(data)
        target.flush()
        os.fsync(target.fileno())
    os.replace(temporary, path)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("apk")
    parser.add_argument("feed_dir")
    parser.add_argument("--notes")
    parser.add_argument("--min-supported", type=int)
    parser.add_argument("--base-url", required=True,
                        help="absolute URL the feed directory is served at, e.g. https://server.example/updates/android")
    parser.add_argument("--keep", type=int, default=2, help="APKs to keep, newest first (default 2)")
    parser.add_argument("--package", default=PACKAGE)
    args = parser.parse_args()

    base = args.base_url.lower()
    loopback = re.match(r"http://(127\.0\.0\.1|localhost)(:\d+)?/", base + "/")
    if not (base.startswith("https://") or loopback):
        sys.exit("--base-url must be https (plain http only on 127.0.0.1 or localhost)")
    package, version_code, version_name = badging(args.apk)
    if package != args.package:
        sys.exit(f"{args.apk} is {package}, not {args.package}")
    os.makedirs(args.feed_dir, exist_ok=True)
    name = f"viptv-{version_code}.apk"
    target = os.path.join(args.feed_dir, name)
    shutil.copyfile(args.apk, target + ".tmp")
    os.replace(target + ".tmp", target)
    feed = {
        "version_code": version_code,
        "version_name": version_name,
        "url": args.base_url.rstrip("/") + "/" + name,
        "sha256": sha256(target),
        "size": os.path.getsize(target),
    }
    if args.notes:
        feed["notes"] = args.notes
    if args.min_supported:
        feed["min_supported_version_code"] = args.min_supported
    write_atomically(os.path.join(args.feed_dir, "update.json"), (json.dumps(feed, indent=2) + "\n").encode())

    published = sorted(glob.glob(os.path.join(args.feed_dir, "viptv-*.apk")),
                       key=lambda path: int(re.search(r"viptv-(\d+)\.apk$", path).group(1)) if re.search(r"viptv-(\d+)\.apk$", path) else -1,
                       reverse=True)
    for stale in published[max(args.keep, 1):]:
        if os.path.basename(stale) != name:
            os.remove(stale)
    print(json.dumps(feed, indent=2))


if __name__ == "__main__":
    main()
