"""Build the pinned Rust engine into platform packaging inputs."""
from pathlib import Path
import argparse
import os
import platform
import shutil
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument("target", choices=["desktop", "android"])
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
core = root / "native/core"
env = os.environ.copy()
env["CARGO_TARGET_DIR"] = str(root / "build/rust")
env.setdefault("CARGO_BUILD_JOBS", "2")
build = ["build", "--manifest-path", str(core / "Cargo.toml"), "--locked", "-p", "viptv-core", "--features", "native", "--lib"]
if args.target == "android":
    sdk = env.get("ANDROID_SDK_ROOT") or env.get("ANDROID_HOME")
    properties = root / "local.properties"
    if not sdk and properties.exists():
        sdk = next((v.split("=", 1)[1] for v in properties.read_text().splitlines() if v.startswith("sdk.dir=")), None)
    if not sdk:
        raise SystemExit("Android SDK location is missing")
    if "ANDROID_NDK_HOME" not in env:
        ndks = sorted((Path(sdk) / "ndk").iterdir(), key=lambda p: [int(x) for x in p.name.split('.')])
        if not ndks:
            raise SystemExit("Android NDK is missing")
        env["ANDROID_NDK_HOME"] = str(ndks[-1])
    output = root / "androidApp/build/generated/rustJni"
    subprocess.run(["cargo", "ndk", "-t", "armeabi-v7a", "-t", "arm64-v8a", "-t", "x86_64", "--platform", "26", "-o", str(output), *build], cwd=core, env=env, check=True)
else:
    subprocess.run(["cargo", *build], env=env, check=True)
    system = platform.system()
    arch = "aarch64" if platform.machine().lower() in ["arm64", "aarch64"] else "x86-64"
    prefix, library = {
        "Linux": ("linux", "libviptv_core.so"),
        "Darwin": ("darwin", "libviptv_core.dylib"),
        "Windows": ("win32", "viptv_core.dll"),
    }[system]
    output = root / "composeApp/build/nativeResources" / f"{prefix}-{arch}"
    output.mkdir(parents=True, exist_ok=True)
    shutil.copy2(root / "build/rust/debug" / library, output / library)
