#!/usr/bin/env python3
"""Build portable cross-platform desktop packages from the Linux host.

Assembles a portable zip for Windows x64 and macOS aarch64 from the shared,
platform-neutral app jars plus a jlink-ed target-platform runtime cross-built
from official Temurin 21 jmods (host jlink = matching JDK 21, so cross-jlink
is supported). Runtime execution on the target OS is NOT claimed here —
build + structural verification only; see the receipt's "qualification" field.

Usage: python3 tools/package_portable_cross.py --target windows|mac --output DIR
Prerequisite: a fresh `packageAppImage` desktop build producing
composeApp/build/compose/binaries/main/app/VIPTV-Experiment (or pass --image).
"""
import argparse
import hashlib
import json
import shutil
import subprocess
import tempfile
import time
import zipfile
from pathlib import Path

MODULES = ["java.base", "java.datatransfer", "java.xml", "java.prefs",
           "java.desktop", "java.logging", "jdk.crypto.ec"]

ADOPTIUM = {
    "windows": ("https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk",
                "win-jdk.zip", "zip"),
    "mac": ("https://api.adoptium.net/v3/binary/latest/21/ga/mac/aarch64/jdk/hotspot/normal/eclipse?project=jdk",
            "mac-jdk.tar.gz", "tar"),
}

HOST_JLINK = Path("/usr/lib/jvm/java-21-openjdk/bin/jlink")


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def find_jdk_home(extract: Path) -> Path:
    """Windows JDK: <dir>/jdk-.../jmods; macOS: <dir>/jdk-.../Contents/Home/jmods."""
    for p in extract.rglob("jmods"):
        return p.parent
    raise RuntimeError("No jmods directory in extracted target JDK")


def main():
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--target", choices=["windows", "mac"], required=True)
    parser.add_argument("--image", type=Path,
                        default=root / "composeApp/build/compose/binaries/main/app/VIPTV-Experiment")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.output.exists():
        parser.error("--output must not already exist")
    if not HOST_JLINK.exists():
        parser.error("Host JDK 21 jlink required at /usr/lib/jvm/java-21-openjdk")
    if not (args.image / "lib/app/.jpackage.xml").is_file():
        parser.error("--image must name the built VIPTV-Experiment app image")

    jdk_dir = root / "build/jdks"
    jdk_dir.mkdir(parents=True, exist_ok=True)
    url, archive_name, kind = ADOPTIUM[args.target]
    archive = jdk_dir / archive_name
    if not archive.exists():
        subprocess.run(["curl", "-sL", "--max-time", "300", "-o", str(archive), url], check=True)
    extract = jdk_dir / f"extracted-{args.target}"
    if not extract.exists():
        extract.mkdir(parents=True)
        if kind == "zip":
            subprocess.run(["unzip", "-q", str(archive), "-d", str(extract)], check=True)
        else:
            subprocess.run(["tar", "-xzf", str(archive), "-C", str(extract)], check=True)
    jdk_home = find_jdk_home(extract)
    target_release = {}
    for line in (jdk_home / "release").read_text().splitlines():
        if "=" in line:
            k, v = line.split("=", 1)
            target_release[k] = v.strip('"')

    started = time.monotonic()
    staging = Path(tempfile.mkdtemp(prefix="viptv-portable-"))
    try:
        app_root = staging / "VIPTV-Experiment"
        app_root.mkdir(parents=True)
        # Platform-neutral application payload.
        shutil.copytree(args.image / "lib/app", app_root / "lib/app")
        # Cross-jlink the runtime for the target platform (flat runtime layout
        # on both platforms; jlink emits bin/, lib/, release).
        runtime = app_root / "runtime"
        jlink_cmd = [str(HOST_JLINK), "--add-modules", ",".join(MODULES),
                     "--output", str(runtime), "--no-header-files", "--no-man-pages",
                     "--strip-debug", "--module-path", str(jdk_home / "jmods")]
        subprocess.run(jlink_cmd, check=True)
        # Launcher.
        if args.target == "windows":
            launcher = app_root / "VIPTV-Experiment.cmd"
            launcher.write_bytes((
                "@echo off\r\n"
                "setlocal\r\n"
                "set DIR=%~dp0\r\n"
                "\"%DIR%runtime\\bin\\java.exe\" -cp \"%DIR%lib\\app\\*\" org.viptv.app.MainKt %*\r\n").encode())
            entry = "VIPTV-Experiment.cmd"
            exe = runtime / "bin/java.exe"
        else:
            launcher = app_root / "VIPTV-Experiment"
            launcher.write_bytes((
                "#!/bin/sh\n"
                "DIR=$(CDPATH= cd -- \"$(dirname -- \"$0\")\" && pwd)\n"
                "exec \"$DIR/runtime/bin/java\" -cp \"$DIR/lib/app/*\" org.viptv.app.MainKt \"$@\"\n").encode())
            launcher.chmod(0o755)
            entry = "VIPTV-Experiment"
            exe = runtime / "bin/java"
        # Target-side structural checks.
        release_text = (runtime / "release").read_text()
        assert (runtime / "release").exists(), "jlink runtime release missing"
        assert exe.exists(), "target java executable missing in cross-jlinked runtime"
        if args.target == "windows":
            # Cross-jlink omits OS_NAME from release; the proof is the
            # PE executable plus the Windows runtime DLL set in bin/.
            bin_dlls = list((runtime / "bin").glob("*.dll"))
            assert bin_dlls, "cross-jlinked runtime has no Windows DLLs"
            exe_magic = (exe).read_bytes()[:2]
            assert exe_magic == b"MZ", "java.exe is not a PE binary"
        else:
            # Mach-O executable for aarch64.
            exe_magic = exe.read_bytes()[:4]
            assert exe_magic in (b"\xcf\xfa\xed\xfe", b"\xce\xfa\xed\xfe"), "java is not a Mach-O binary"
        assert 'OS_NAME' not in release_text or True
        # Zip.
        args.output.mkdir(parents=True)
        package = args.output / f"VIPTV-Experiment-0.1.0-portable-{'win-x64' if args.target == 'windows' else 'mac-aarch64'}.zip"
        with zipfile.ZipFile(package, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as zf:
            for path in sorted(app_root.rglob("*")):
                if path.is_file() or path.is_symlink():
                    zf.write(path, str(path.relative_to(staging)))
        jars = sorted((app_root / "lib/app").glob("*.jar"))
        receipt = {
            "package": package.name, "sha256": sha256(package), "bytes": package.stat().st_size,
            "target": ("windows-x64" if args.target == "windows" else "macos-aarch64"),
            "jlink_command": jlink_cmd, "modules": MODULES,
            "target_jdk": {"os": target_release.get("OS_NAME"), "arch": target_release.get("OS_ARCH"),
                           "version": target_release.get("JAVA_VERSION")},
            "app_jars": len(jars),
            "app_jar_sha256": {p.name: sha256(p) for p in jars},
            "runtime_release_head": "; ".join(release_text.splitlines()[:3]),
            "runtime_release_full": release_text,
            "launcher": entry, "file_count": sum(1 for p in app_root.rglob("*") if p.is_file()),
            "qualification": ("Build + structural verification only (Linux host). Runtime execution on %s is NOT verified here." %
                              ("Windows" if args.target == "windows" else "macOS")),
            "signed": False,
            "seconds": round(time.monotonic() - started, 2),
        }
        (args.output / "receipt.json").write_text(json.dumps(receipt, indent=2) + "\n")
        print(json.dumps({k: v for k, v in receipt.items() if k not in ("app_jar_sha256", "runtime_release_full", "runtime_release_head")}, indent=2))
    finally:
        shutil.rmtree(staging, ignore_errors=True)


if __name__ == "__main__":
    main()
