#!/usr/bin/env python3
"""Inventory Linux x86_64 ELF dependencies, including native resources in JARs.

Static symbol floors are necessary conditions, not a distribution-install test.
Does not resolve optional dlopen drivers/plugins or test hardware acceleration.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile
import zipfile


def audit(image: Path) -> dict:
    image = image.resolve()
    entries = []
    provided = set()
    with tempfile.TemporaryDirectory(prefix="viptv-elf-audit-") as temporary:
        files = []
        for path in image.rglob("*"):
            if path.is_file():
                with path.open("rb") as stream:
                    if stream.read(4) == b"\x7fELF":
                        files.append((str(path.relative_to(image)), path))
        for jar in image.rglob("*.jar"):
            with zipfile.ZipFile(jar) as archive:
                for name in archive.namelist():
                    if name.endswith(".so") and any(part in name for part in ("linux-x64/", "linux-x86-64/")):
                        path = Path(temporary) / str(len(files))
                        path.write_bytes(archive.read(name))
                        files.append((str(jar.relative_to(image)) + "!" + name, path))
        for label, path in files:
            dynamic = subprocess.check_output(["readelf", "-d", str(path)], text=True)
            needed = re.findall(r"\(NEEDED\).*?\[(.+?)\]", dynamic)
            sonames = re.findall(r"\(SONAME\).*?\[(.+?)\]", dynamic)
            provided.update(sonames or [Path(label.split("!")[-1]).name])
            versions = subprocess.check_output(["readelf", "--version-info", str(path)], text=True)
            requirements = {}
            provider = None
            in_needs = False
            for line in versions.splitlines():
                if line.startswith("Version needs section"):
                    in_needs = True
                elif line.startswith("Version ") and "section" in line:
                    in_needs = False
                if not in_needs:
                    continue
                match = re.search(r"File: (\S+)", line)
                if match:
                    provider = match[1]
                match = re.search(r"Name: ((?:GLIBCXX|GLIBC|CXXABI|GCC)_[\w.]+)", line)
                if match:
                    requirements.setdefault(provider, []).append(match[1])
            entries.append({"file": label, "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
                            "needed": needed, "version_needs": requirements})
    maxima = {}
    for entry in entries:
        entry["external_needed"] = [name for name in entry["needed"] if name not in provided]
        for provider, versions in entry["version_needs"].items():
            if provider in provided:
                continue
            for version in versions:
                family, number = version.split("_", 1)
                if not re.fullmatch(r"\d+(?:\.\d+)*", number):
                    continue
                key = tuple(map(int, number.split(".")))
                if family not in maxima or key > maxima[family][0]:
                    maxima[family] = (key, version, entry["file"])
    return {"image": str(image), "elf_count": len(entries),
            "external_sonames": sorted({name for entry in entries for name in entry["external_needed"]}),
            "minimum_symbol_versions": {family: {"version": value[1], "required_by": value[2]}
                                        for family, value in maxima.items()},
            "elf_files": entries,
            "scope": "Static Linux x86_64 ELF requirements; bundled providers excluded from external floors. Optional dlopen drivers/plugins and actual distribution installation are not qualified."}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--image", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = audit(args.image)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + "\n")
    print(json.dumps({key: value for key, value in result.items() if key != "elf_files"}, indent=2))
