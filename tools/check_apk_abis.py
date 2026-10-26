#!/usr/bin/env python3
"""Verify an APK packages exactly the expected ABIs, each with its engine and audio decoder.

Usage: check_apk_abis.py <apk> [comma-separated ABIs]
Without an ABI list it expects every shipped ABI (the default build).
"""
import sys
import zipfile

SHIPPED = ["armeabi-v7a", "arm64-v8a", "x86_64"]
REQUIRED = ["libviptv_core.so", "libffmpegJNI.so"]

if len(sys.argv) not in (2, 3):
    raise SystemExit(__doc__)
expected = sys.argv[2].split(",") if len(sys.argv) == 3 else SHIPPED
unknown = sorted(set(expected) - set(SHIPPED))
if unknown:
    raise SystemExit(f"Unknown ABI {', '.join(unknown)}; supported: {', '.join(SHIPPED)}")
with zipfile.ZipFile(sys.argv[1]) as apk:
    names = apk.namelist()
packaged = sorted({name.split("/")[1] for name in names if name.startswith("lib/") and name.count("/") >= 2})
if packaged != sorted(expected):
    raise SystemExit(f"FAIL: APK packages ABIs {packaged or 'none'}, expected {sorted(expected)}")
for abi in expected:
    for library in REQUIRED:
        if f"lib/{abi}/{library}" not in names:
            raise SystemExit(f"FAIL: lib/{abi}/{library} is missing")
print(f"PASS: APK packages only {', '.join(sorted(expected))}, each with {', '.join(REQUIRED)}")
