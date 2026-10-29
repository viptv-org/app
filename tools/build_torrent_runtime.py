"""Build the pinned shared torrent runtime (playback-gateway/torrent-runtime) for Android.

The revision is TORRENT_RUNTIME_REF. Sources come from a playback-gateway clone:
VIPTV_PLAYBACK_GATEWAY, or the workspace's `playback-gateway` next to (or above) this
repository. Needs Go >= 1.25 (GO, or a toolchain `go` can fetch) and the Android NDK.
--abis builds only the listed ABIs; androidApp passes -Pviptv.abis through here and the
default is every shipped ABI.
Output: androidApp/build/generated/torrentRuntime/jniLibs/<abi>/*.so
"""
from pathlib import Path
import argparse
import json
import os
import shutil
import subprocess
import tempfile

import playback_gateway

# Every shipped Android ABI.
ANDROID_ABIS = ["armeabi-v7a", "arm64-v8a", "x86_64"]
parser = argparse.ArgumentParser()
parser.add_argument("--abis", default=",".join(ANDROID_ABIS), help="comma-separated Android ABIs to build")
args = parser.parse_args()
abis = [abi.strip() for abi in args.abis.split(",") if abi.strip()]
unknown = sorted(set(abis) - set(ANDROID_ABIS))
if not abis or unknown:
    parser.error(f"--abis: unknown ABI {', '.join(unknown) or '(empty)'}; supported: {', '.join(ANDROID_ABIS)}")
abis = [abi for abi in ANDROID_ABIS if abi in abis]

GO_TOOLCHAIN = "go1.25.11"
root = Path(__file__).resolve().parents[1]
revision = (root / "TORRENT_RUNTIME_REF").read_text().strip()
output = root / "androidApp/build/generated/torrentRuntime"
stamp = output / "build.json"


def go_binary(env):
    if env.get("GO"):
        return env["GO"]
    go = shutil.which("go")
    if not go:
        raise SystemExit("Go is missing; set GO to a Go >= 1.25 executable")
    version = subprocess.check_output([go, "env", "GOVERSION"], text=True).strip()
    if [int(part) for part in version.removeprefix("go").split(".")[:2]] >= [1, 25]:
        return go
    # Older host Go can still select (and cache) the pinned toolchain.
    goroot = subprocess.check_output([go, "env", "GOROOT"], env=dict(env, GOTOOLCHAIN=GO_TOOLCHAIN), text=True).strip()
    return str(Path(goroot) / "bin/go")


def ndk_home(env):
    if env.get("ANDROID_NDK_HOME"):
        return env["ANDROID_NDK_HOME"]
    sdk = env.get("ANDROID_SDK_ROOT") or env.get("ANDROID_HOME")
    properties = root / "local.properties"
    if not sdk and properties.exists():
        sdk = next((v.split("=", 1)[1] for v in properties.read_text().splitlines() if v.startswith("sdk.dir=")), None)
    ndks = sorted((Path(sdk) / "ndk").iterdir(), key=lambda p: [int(x) for x in p.name.split(".")]) if sdk else []
    if not ndks:
        raise SystemExit("Android NDK is missing")
    return str(ndks[-1])


LAYOUT = 2  # jniLibs + notice assets
stamped = json.loads(stamp.read_text()) if stamp.exists() else {}
if stamped.get("revision") == revision and stamped.get("layout") == LAYOUT and stamped.get("abis") == abis:
    print("Torrent runtime", revision, "is up to date")
    raise SystemExit(0)

env = os.environ.copy()
repository = playback_gateway.repository(env)
if subprocess.run(["git", "-C", str(repository), "cat-file", "-e", revision + "^{commit}"]).returncode != 0:
    subprocess.run(["git", "-C", str(repository), "fetch", "--quiet", "origin", revision], check=True)
env.update(GO=go_binary(env), ANDROID_NDK_HOME=ndk_home(env))
with tempfile.TemporaryDirectory(prefix="viptv-torrent-runtime-") as scratch:
    checkout = Path(scratch) / "source"
    artifacts = Path(scratch) / "artifacts"
    subprocess.run(["git", "-C", str(repository), "worktree", "add", "--detach", "--quiet", str(checkout), revision], check=True)
    try:
        runtime = checkout / "torrent-runtime"
        # The app compiles its own copy of the JNI class; it must match the bridge it binds.
        shipped = root / "androidApp/src/main/java/org/playbackgateway/runtime/NativeRuntime.java"
        if shipped.read_bytes() != (runtime / "platform/android/NativeRuntime.java").read_bytes():
            raise SystemExit("NativeRuntime.java differs from torrent-runtime " + revision)
        subprocess.run(["python3", str(runtime / "scripts/build-native.py"), "android", "--output", str(artifacts), "--abis", ",".join(abis)], env=env, check=True)
        built = json.loads((artifacts / "build.json").read_text())
        if built["revision"] != revision or built["dirty"]:
            raise SystemExit("Torrent runtime build is not the clean pinned revision")
        if set(built.get("abis") or []) != set(abis):
            raise SystemExit("Torrent runtime build ABIs differ from " + ",".join(abis))
        if output.exists():
            shutil.rmtree(output)
        for library in sorted((artifacts / "jni").glob("*/*.so")):
            target = output / "jniLibs" / library.parent.name / library.name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(library, target)
        # Ship the runtime's own and every dependency's notices (GPL-2.0 runtime, Go/C modules, NDK).
        notices = output / "assets/torrent-runtime"
        notices.mkdir(parents=True)
        shutil.copy2(artifacts / "NDK-TOOLCHAIN-NOTICE.txt", notices / "NDK-TOOLCHAIN-NOTICE.txt")
        for name in ["LICENSE", "PROVENANCE.md"]:
            shutil.copy2(checkout / name, notices / name)
        for name in ["go.mod", "go.sum"]:
            shutil.copy2(runtime / name, notices / name)
        raw = subprocess.check_output([env["GO"], "list", "-m", "-json", "all"], cwd=runtime, env=dict(env, GOFLAGS="-mod=mod"), text=True)
        decoder = json.JSONDecoder()
        while raw.strip():
            module, end = decoder.raw_decode(raw.lstrip())
            raw = raw.lstrip()[end:]
            if module.get("Main") or not module.get("Dir"):
                continue
            for path in Path(module["Dir"]).rglob("*"):
                if path.is_file() and path.name.lower().startswith(("license", "copying", "notice")):
                    target = notices / "licenses" / module["Path"] / path.relative_to(module["Dir"])
                    target.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copyfile(path, target)
        stamp.write_text(json.dumps({"revision": revision, "layout": LAYOUT, "abis": abis, "files": {
            str(p.relative_to(output)): p.stat().st_size for p in sorted(output.rglob("*.so"))}}, indent=2) + "\n")
    finally:
        subprocess.run(["git", "-C", str(repository), "worktree", "remove", "--force", str(checkout)], check=False)
print("Torrent runtime", revision, "->", output)
