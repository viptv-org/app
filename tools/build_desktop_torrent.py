"""Package the pinned v2 worker; --source is an explicit, marked QA override."""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import tarfile
import tempfile

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--source", type=Path, default=os.getenv("VIPTV_TORRENT_CANDIDATE"))
args = parser.parse_args()
revision = (root / "TORRENT_RUNTIME_REF").read_text().strip()
repository = Path(os.getenv("VIPTV_PLAYBACK_GATEWAY", root.parent / "playback-gateway"))
system = platform.system()
prefix = {"Linux": "linux", "Windows": "win32", "Darwin": "darwin"}[system]
arch = "aarch64" if platform.machine().lower() in ("arm64", "aarch64") else "x86-64"
output = root / "composeApp/build/torrentResources/torrent-runtime" / f"{prefix}-{arch}"
worker = "torrent-worker.exe" if system == "Windows" else "torrent-worker"
go = os.getenv("GO") or shutil.which("go")
if not go:
    raise SystemExit("Set GO to a Go 1.25+ executable")
env = dict(os.environ, GOTOOLCHAIN="go1.25.11", GOMAXPROCS="2", CGO_ENABLED="1")
output.mkdir(parents=True, exist_ok=True)
with tempfile.TemporaryDirectory(prefix="viptv-desktop-torrent-") as temporary:
    if args.source:
        source = args.source.resolve()
        revision = subprocess.check_output(["git", "-C", str(source), "rev-parse", "HEAD"], text=True).strip()
    else:
        archive = subprocess.check_output(["git", "-C", str(repository), "archive", revision, "torrent-runtime", "LICENSE", "PROVENANCE.md"])
        with tarfile.open(fileobj=io.BytesIO(archive)) as tar:
            tar.extractall(temporary, filter="data")
        source = Path(temporary) / "torrent-runtime"
    if not (source / "go.mod").is_file():
        raise SystemExit("Expected torrent-runtime source directory")
    subprocess.run([go, "build", "-p", "1", "-trimpath", "-buildvcs=false", "-ldflags=-s -w", "-o", str(output / worker), "./cmd/torrent-worker"], cwd=source, env=env, check=True)
    sha = hashlib.sha256((output / worker).read_bytes()).hexdigest()
    manifest = {"protocol": 2, "revision": revision, "sha256": sha, "qualification_only": bool(args.source), "toolchain": "go1.25.11",
                "suspend_aware_clock": system != "Darwin" or (source / "clock_darwin.go").is_file()}
    (output / "worker.json").write_text(json.dumps(manifest, indent=2) + "\n")
    notices = output / "notices"
    if notices.exists(): shutil.rmtree(notices)
    notices.mkdir()
    for name in ("LICENSE", "PROVENANCE.md"):
        shutil.copy2(source.parent / name, notices / name)
    for name in ("go.mod", "go.sum"):
        shutil.copy2(source / name, notices / name)
    raw = subprocess.check_output([go, "list", "-m", "-json", "all"], cwd=source, env=env, text=True)
    decoder = json.JSONDecoder()
    while raw.strip():
        module, end = decoder.raw_decode(raw.lstrip()); raw = raw.lstrip()[end:]
        if module.get("Main") or not module.get("Dir"): continue
        for path in Path(module["Dir"]).rglob("*"):
            if path.is_file() and path.name.lower().startswith(("license", "copying", "notice")):
                target = notices / "dependencies" / module["Path"] / path.relative_to(module["Dir"])
                target.parent.mkdir(parents=True, exist_ok=True); shutil.copy2(path, target)
print("Desktop torrent worker", revision, "qualification-only" if args.source else "pinned", sha)
