#!/usr/bin/env python3
"""Exercise packaged audio on an explicitly owned 16KiB or API26 guest."""
import argparse
import json
from pathlib import Path
import re
import select
import subprocess
import time
from urllib.parse import urlsplit

p = argparse.ArgumentParser()
p.add_argument('--origin', required=True)
p.add_argument('--avd', choices=['viptv-16k-isolated', 'viptv-api26-isolated'], default='viptv-16k-isolated')
p.add_argument('--serial', default='emulator-5586')
p.add_argument('--root', type=Path, required=True)
p.add_argument('--adb', default='/home/vynxc/Android/Sdk/platform-tools/adb')
a = p.parse_args()
u = urlsplit(a.origin)
assert u.scheme == 'https' and u.hostname == '127.0.0.1' and u.port
adb = [a.adb, '-s', a.serial]
def run(*args):
    assert a.serial == ("emulator-5592" if a.avd == "viptv-api26-isolated" else "emulator-5586")
    observed = subprocess.check_output(adb + ["emu", "avd", "name"], stderr=subprocess.STDOUT).decode().splitlines()[0].strip()
    assert observed == a.avd, f"Owned AVD guard failed: {observed!r}"
    if a.avd == "viptv-api26-isolated":
        assert subprocess.check_output(adb + ["shell", "getprop", "ro.build.version.sdk"]).decode().strip() == "26"
    return subprocess.check_output(adb + list(args), stderr=subprocess.STDOUT).decode()
assert a.serial == ('emulator-5592' if a.avd == 'viptv-api26-isolated' else 'emulator-5586')
assert run('emu', 'avd', 'name').splitlines()[0] == a.avd
page_size = None if a.avd == 'viptv-api26-isolated' else int(run('shell', 'getconf', 'PAGE_SIZE').strip())
if a.avd == 'viptv-api26-isolated':
    assert run('shell', 'getprop', 'ro.build.version.sdk').strip() == '26'
else:
    assert page_size == 16384
a.root.mkdir(parents=True, exist_ok=True)
run('reverse', f'tcp:{u.port}', f'tcp:{u.port}')
if a.avd != 'viptv-api26-isolated':
    run('logcat', '-c')
log_flags = ['-b', 'main', '-T', '1'] if a.avd == 'viptv-api26-isolated' else []
stream = subprocess.Popen(adb + ['logcat', *log_flags, '-v', 'brief', 'VIPTV_PLAYBACK_QA:I', 'AndroidRuntime:E', '*:S'], stdout=subprocess.PIPE, bufsize=0)
lines = []
positions = []
started = time.monotonic()
try:
    run('shell', 'am', 'start', '-S', '-n', 'org.viptv.experiment/.NativePlaybackQaActivity',
        '--es', 'qa_media_uri', a.origin + '/media/format-eac3.mkv', '--es', 'qa_title', 'Synthetic-EAC3-qualification')
    app_pid = run('shell', 'pidof', 'org.viptv.experiment').strip()
    assert app_pid.isdigit()
    deadline = time.monotonic() + 50
    while time.monotonic() < deadline:
        if not select.select([stream.stdout], [], [], 1)[0]:
            continue
        line = stream.stdout.readline().decode()
        if not re.search(r'\(\s*' + app_pid + r'\):', line):
            continue
        lines.append(line)
        match = re.search(r'position_ms=(\d+)', line)
        if match:
            positions.append(int(match.group(1)))
        if 'failed=true' in line or 'FATAL EXCEPTION' in line:
            break
        if positions and max(positions) >= 20_000:
            break
finally:
    stream.terminate()
    stream.wait(timeout=5)
    run('shell', 'am', 'force-stop', 'org.viptv.experiment')
    (a.root / 'eac3.log').write_text(''.join(lines))
body = ''.join(lines)
result = {'avd': a.avd, 'page_size': page_size,
          'fixture': 'synthetic H264/EAC3 Matroska,320x180,stereo48kHz',
          'first_frame': 'presentation=NativeRendererPresented' in body,
          'ffmpeg_available': 'ffmpeg_audio_available=true' in body,
          'ffmpeg_decoder': re.findall(r'audio_decoder=(ffmpeg\S+-eac3)', body),
          'max_position_ms': max(positions, default=0), 'wall_ms': round((time.monotonic()-started)*1000),
          'failed': 'failed=true' in body or 'FATAL EXCEPTION' in body}
result['passed'] = result['first_frame'] and result['ffmpeg_available'] and bool(result['ffmpeg_decoder']) and result['max_position_ms'] >= 20_000 and not result['failed']
(a.root / 'eac3-result.json').write_text(json.dumps(result, indent=2))
print(json.dumps(result), flush=True)
if not result['passed']:
    raise SystemExit(1)
