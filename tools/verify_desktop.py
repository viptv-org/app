"""Qualify the packaged launcher, bundled JVM and native SQLite bridge without opening UI."""
from pathlib import Path
import os
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
image = root / "composeApp/build/compose/binaries/main/app"
launchers = [path for path in image.rglob("*")
             if path.is_file() and path.name in ("VIPTV-Experiment", "VIPTV-Experiment.exe")
             and path.parent.name in ("bin", "MacOS", "VIPTV-Experiment")]
if len(launchers) != 1:
    raise SystemExit(f"Expected one packaged launcher, found {len(launchers)}")
with tempfile.TemporaryDirectory(prefix="viptv-packaged-smoke-") as temporary:
    result = Path(temporary) / "result.txt"
    environment = os.environ.copy()
    environment["VIPTV_ENGINE_SMOKE_TEST"] = "1"
    environment["VIPTV_ENGINE_SMOKE_RESULT"] = str(result)
    process = subprocess.run([str(launchers[0])], env=environment, capture_output=True, text=True, timeout=60)
    if process.returncode != 0 or any(marker in process.stderr.lower() for marker in ("pure virtual", "fatal error", "terminate called")):
        print(process.stdout)
        print(process.stderr)
        raise SystemExit("The packaged launcher failed or reported a native shutdown failure")
    if not result.is_file() or result.read_text().strip() != "verified-local-engine":
        raise SystemExit("The packaged native engine did not complete its diagnostic")
print("Packaged launcher, JVM and native SQLite engine verified")
