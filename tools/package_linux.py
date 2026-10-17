#!/usr/bin/env python3
"""Package an already-built Linux x86_64 app image without compiling/installing.

Use a verified portable JDK21 to build the app image first (see
docs/DESKTOP_VAULT_RECOVERY.md).
This wrapper supplies observed Debian requirements when the build host cannot
infer Debian package ownership. It refuses unknown SONAMEs or higher ABI floors.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import tempfile
import time
import zipfile

from audit_linux_runtime import audit


DEPENDENCIES = {
    "ld-linux-x86-64.so.2": "libc6 (>= 2.34)",
    "libc.so.6": "libc6 (>= 2.34)",
    "libdl.so.2": "libc6 (>= 2.34)",
    "libm.so.6": "libc6 (>= 2.34)",
    "libpthread.so.0": "libc6 (>= 2.34)",
    "librt.so.1": "libc6 (>= 2.34)",
    "libstdc++.so.6": "libstdc++6 (>= 6.1)",
    "libgcc_s.so.1": "libgcc-s1 (>= 4.2)",
    "libGL.so.1": "libgl1",
    "libX11.so.6": "libx11-6",
    "libXext.so.6": "libxext6",
    "libXi.so.6": "libxi6",
    "libXrender.so.1": "libxrender1",
    "libXtst.so.6": "libxtst6",
    "libasound.so.2": "libasound2t64 | libasound2",
    "libdbus-1.so.3": "libdbus-1-3",
    "libfontconfig.so.1": "libfontconfig1",
    "libz.so.1": "zlib1g",
}
FLOORS = {"GLIBC": (2, 34), "GLIBCXX": (3, 4, 22), "CXXABI": (1, 3, 9), "GCC": (4, 2, 0)}


def main():
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--image", type=Path, default=root / "composeApp/build/compose/binaries/main/app/VIPTV-Experiment")
    parser.add_argument("--java-home", type=Path, required=True)
    parser.add_argument("--dpkg-home", type=Path, help="Optional unpacked, independently verified dpkg tool prefix; never installed")
    parser.add_argument("--output", type=Path, required=True, help="New directory; existing candidates are never overwritten")
    args = parser.parse_args()
    if platform.system() != "Linux" or platform.machine() not in ("x86_64", "AMD64"):
        parser.error("This dependency baseline qualifies only Linux x86_64")
    args.image = args.image.resolve()
    args.java_home = args.java_home.resolve()
    if not (args.image / "lib/app/.jpackage.xml").is_file():
        parser.error("--image must name the built VIPTV-Experiment app image")
    if args.output.exists():
        parser.error("--output must not already exist")
    environment = os.environ.copy()
    if args.dpkg_home:
        args.dpkg_home = args.dpkg_home.resolve()
        environment["PATH"] = str(args.dpkg_home / "usr/bin") + os.pathsep + environment["PATH"]
        environment["DPKG_DATADIR"] = str(args.dpkg_home / "usr/share/dpkg")
    environment["DPKG_DEB_THREADS_MAX"] = "2"
    environment["JAVA_TOOL_OPTIONS"] = "-Xmx512m"
    for tool in ("dpkg", "dpkg-deb", "fakeroot"):
        if not shutil.which(tool, path=environment["PATH"]):
            parser.error(f"Missing build tool {tool}; use verified isolated tools, not a system installation")
    result = audit(args.image)
    unknown = set(result["external_sonames"]) - DEPENDENCIES.keys()
    if unknown:
        parser.error(f"Unmapped external libraries: {sorted(unknown)}; review official package ownership before packaging")
    for family, requirement in result["minimum_symbol_versions"].items():
        version = tuple(map(int, requirement["version"].split("_", 1)[1].split(".")))
        if family not in FLOORS or version > FLOORS[family]:
            parser.error(f"ABI floor {requirement['version']} exceeds reviewed dependency baseline; rebuild with portable JDK or review minimum distribution")
    subprocess.run(["python3", str(root / "tools/verify_desktop.py"), "--image", str(args.image)], check=True)
    # jpackage adds xdg-utils itself when --linux-shortcut is enabled.
    dependencies = ", ".join(sorted({DEPENDENCIES[name] for name in result["external_sonames"]}))
    args.output.mkdir(parents=True)
    # JDK21's stock postinst calls xdg-desktop-menu, whose generic system mode
    # requires this standard directory even when installing only a .desktop file.
    # Minimal Debian does not supply it. Preserve the selected JDK's full script
    # and substitutions, adding only an idempotent directory prerequisite.
    with zipfile.ZipFile(args.java_home / "jmods/jdk.jpackage.jmod") as archive:
        original_postinst = archive.read("classes/jdk/jpackage/internal/resources/template.postinst").decode()
    configure = "    configure)\n"
    if original_postinst.count(configure) != 1 or "DESKTOP_COMMANDS_INSTALL" not in original_postinst:
        raise RuntimeError("Selected JDK postinst template changed; review before overriding")
    directory_guard = ("        if [ ! -d /usr/share/desktop-directories ]; then\n"
                       "            install -d -m0755 /usr/share/desktop-directories\n"
                       "        fi\n")
    postinst = original_postinst.replace(configure, configure + directory_guard, 1)
    resource_directory = args.output.resolve() / "jpackage-resources"
    resource_directory.mkdir()
    (resource_directory / "postinst").write_text(postinst)
    command = [str(args.java_home / "bin/jpackage"), "--type", "deb", "--app-image", str(args.image),
               "--dest", str(args.output.resolve()), "--name", "VIPTV-Experiment", "--app-version", "0.1.0",
               "--linux-package-name", "viptv-experiment", "--linux-app-release", "2", "--linux-shortcut",
               "--linux-package-deps", dependencies, "--resource-dir", str(resource_directory)]
    started = time.monotonic()
    with (args.output / "jpackage.log").open("w") as log:
        subprocess.run(command, env=environment, stdout=log, stderr=subprocess.STDOUT, check=True)
    packages = list(args.output.glob("*.deb"))
    if len(packages) != 1:
        raise RuntimeError("Expected one Debian artifact")
    package = packages[0]
    control = subprocess.check_output(["dpkg-deb", "-f", str(package)], env=environment, text=True)
    (args.output / "control.txt").write_text(control)
    if "xdg-utils" not in subprocess.check_output(
        ["dpkg-deb", "-f", str(package), "Depends"], env=environment, text=True
    ):
        raise RuntimeError("Desktop menu integration requires xdg-utils")
    with tempfile.TemporaryDirectory(prefix="viptv-deb-qualification-") as extracted:
        control_directory = Path(extracted) / "control"
        subprocess.run(["dpkg-deb", "-e", str(package), str(control_directory)], env=environment, check=True)
        packaged_postinst = (control_directory / "postinst").read_text()
        if directory_guard not in packaged_postinst or packaged_postinst.index(directory_guard) > packaged_postinst.index("xdg-desktop-menu install"):
            raise RuntimeError("Debian installer directory prerequisite was not packaged before menu installation")
        (args.output / "postinst.txt").write_text(packaged_postinst)
        subprocess.run(["dpkg-deb", "-x", str(package), extracted], env=environment, check=True)
        extracted_root = Path(extracted)
        launchers = list(extracted_root.glob("opt/*/bin/VIPTV-Experiment"))
        if len(launchers) != 1:
            raise RuntimeError("Expected one extracted launcher")
        extracted_image = launchers[0].parents[1]
        extracted_audit = audit(extracted_image)
        for key in ("elf_count", "external_sonames"):
            if extracted_audit[key] != result[key]:
                raise RuntimeError(f"Packaged runtime differs from app image: {key}")
        if {entry["file"]: entry for entry in extracted_audit["elf_files"]} != {
            entry["file"]: entry for entry in result["elf_files"]
        }:
            raise RuntimeError("Packaged ELF bytes or requirements differ from app image")
        subprocess.run(["python3", str(root / "tools/verify_desktop.py"), "--image", str(extracted_image),
                        "--receipt", str(args.output / "extracted-runtime.json")], check=True)
        desktop_files = [path for path in extracted_image.rglob("*.desktop") if path.is_file()]
        if len(desktop_files) != 1:
            raise RuntimeError("Expected packaged desktop menu shortcut")
        desktop_entry = desktop_files[0].read_text()
        if "Exec=/opt/viptv-experiment/bin/VIPTV-Experiment" not in desktop_entry:
            raise RuntimeError("Desktop menu shortcut does not launch the packaged executable")
        (args.output / "desktop-entry.txt").write_text(desktop_entry)
        (args.output / "extracted-abi.json").write_text(json.dumps(extracted_audit, indent=2) + "\n")
    receipt = {"package": package.name, "sha256": hashlib.sha256(package.read_bytes()).hexdigest(),
               "bytes": package.stat().st_size, "jpackage_command": command, "dependencies": dependencies,
               "jdk_release": (args.java_home / "release").read_text(), "abi_audit": result,
               "seconds": round(time.monotonic() - started, 2), "package_installed": False,
               "extracted_runtime_verified": True, "extracted_elf_inventory_matches_input": True,
               "desktop_menu_entry_verified": True,
               "debian_revision": 2, "installer_directory_prerequisite_verified": True,
               "jdk_postinst_template_sha256": hashlib.sha256(original_postinst.encode()).hexdigest(),
               "postinst_resource_sha256": hashlib.sha256(postinst.encode()).hexdigest(),
               "signed_distribution": False,
               "qualification": "Extracted-image execution on current Linux host only. Debian install, Windows/macOS, hardware/GPU and distribution signing remain unqualified."}
    (args.output / "receipt.json").write_text(json.dumps(receipt, indent=2) + "\n")
    print(json.dumps({key: value for key, value in receipt.items() if key not in ("abi_audit", "jdk_release")}, indent=2))


if __name__ == "__main__":
    main()
