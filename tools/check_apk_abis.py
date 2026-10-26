#!/usr/bin/env python3
"""Verify an APK packages exactly the given ABIs, each with its engine and audio decoder.

Usage: check_apk_abis.py <apk> <comma-separated ABIs>
"""
import sys
import zipfile

REQUIRED = ["libviptv_core.so", "libffmpegJNI.so"]

if len(sys.argv) != 3:
    raise SystemExit(__doc__)
expected = sorted({abi.strip() for abi in sys.argv[2].split(",") if abi.strip()})
if not expected:
    raise SystemExit("No ABIs given\n" + __doc__)
with zipfile.ZipFile(sys.argv[1]) as apk:
    names = set(apk.namelist())
packaged = sorted({name.split("/")[1] for name in names if name.startswith("lib/") and name.count("/") >= 2})
if packaged != expected:
    raise SystemExit(f"FAIL: APK packages ABIs {packaged or 'none'}, expected {expected}")
for abi in expected:
    for library in REQUIRED:
        if f"lib/{abi}/{library}" not in names:
            raise SystemExit(f"FAIL: lib/{abi}/{library} is missing")
print(f"PASS: APK packages only {', '.join(expected)}, each with {', '.join(REQUIRED)}")
