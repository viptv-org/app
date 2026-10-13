"""Prepare the pinned KitePlayer renderer modules in the app's owned Maven cache."""
from pathlib import Path
import hashlib
import json
import os
import shutil
import subprocess


root = Path(__file__).resolve().parents[1]
provenance = root / "native/kiteplayer"
pin = json.loads((provenance / "SOURCE.json").read_text())
revision = pin["fork_revision"]
if len(revision) != 40 or any(c not in "0123456789abcdef" for c in revision):
    raise SystemExit("Invalid player source revision")
bundle_name = pin["bundle"]
if Path(bundle_name).name != bundle_name:
    raise SystemExit("Invalid player source bundle path")
bundle = provenance / bundle_name
if hashlib.sha256(bundle.read_bytes()).hexdigest() != pin["bundle_sha256"]:
    raise SystemExit("Player source bundle checksum mismatch")

cache = root / "build/kiteplayer"
source = cache / f"source-{revision}"
repository = cache / "maven"
stamp = repository / "source-revision"
artifacts = json.loads((provenance / "ARTIFACTS.json").read_text())["sha256"]


def verified_artifacts():
    return all((repository / path).is_file()
               and hashlib.sha256((repository / path).read_bytes()).hexdigest() == checksum
               for path, checksum in artifacts.items())


if stamp.exists() and stamp.read_text().strip() == revision and verified_artifacts():
    raise SystemExit(0)


def git(*args):
    return subprocess.check_output(["git", "-C", str(source), *args], text=True).strip()


cache.mkdir(parents=True, exist_ok=True)
if source.exists():
    if git("rev-parse", "HEAD") != revision or git("status", "--porcelain"):
        raise SystemExit("Player source cache has a different revision or local edits")
else:
    subprocess.run(["git", "clone", "--filter=blob:none", "--no-checkout",
                    pin["upstream_repository"], str(source)], check=True)
    git("fetch", "origin", pin["base_revision"])
    git("checkout", "--detach", pin["base_revision"])
    git("bundle", "verify", str(bundle))
    git("fetch", str(bundle), revision)
    git("checkout", "--detach", revision)
if git("rev-parse", "HEAD") != revision:
    raise SystemExit("Player source revision verification failed")

env = os.environ.copy()
properties = root / "local.properties"
if not env.get("ANDROID_HOME") and not env.get("ANDROID_SDK_ROOT") and properties.exists():
    sdk = next((line.split("=", 1)[1] for line in properties.read_text().splitlines()
                if line.startswith("sdk.dir=")), None)
    if sdk:
        env["ANDROID_HOME"] = sdk.replace("\\:", ":").replace("\\\\", "\\")
wrapper = source / ("gradlew.bat" if os.name == "nt" else "gradlew")
tasks = [f":kiteplayer-compose-{module}:publish{variant}PublicationToViptvRendererRepository"
         for module in ("video", "ui") for variant in ("KotlinMultiplatform", "Jvm", "Android")]
subprocess.run([str(wrapper), "--no-configuration-cache", "-I",
                "verification/viptv-renderer/publish.gradle", f"-PVERSION={pin['version']}",
                *tasks], cwd=source, env=env, check=True)
shutil.copytree(source / "build/viptv-renderer-maven", repository, dirs_exist_ok=True)
if not verified_artifacts():
    raise SystemExit("Player renderer artifact checksum verification failed")
stamp.write_text(revision + "\n")
