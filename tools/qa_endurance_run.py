#!/usr/bin/env python3
"""Real >=40-minute clock/renderer/tracking QA on the owned 16KiB guest only."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import select
import ssl
import subprocess
import time
import urllib.request
from urllib.parse import urlsplit
import xml.etree.ElementTree as ET

p = argparse.ArgumentParser()
p.add_argument('--apk', type=Path, required=True)
p.add_argument('--root', type=Path, required=True)
p.add_argument('--ca', type=Path, required=True)
p.add_argument('--origin', required=True)
p.add_argument('--sustain-ms', type=int, default=2_460_000)
p.add_argument('--short-capture-proof', action='store_true', help='30–120s handshake proof only; never long-endurance evidence')
a = p.parse_args()
assert (30_000 <= a.sustain_ms <= 120_000) if a.short_capture_proof else (2_400_000 <= a.sustain_ms <= 2_700_000)
u = urlsplit(a.origin)
assert u.scheme == 'https' and u.hostname == '127.0.0.1' and u.port == 18586
adb = ['/home/vynxc/Android/Sdk/platform-tools/adb', '-s', 'emulator-5586']
a.root.mkdir(parents=True, exist_ok=True)
ctx = ssl.create_default_context(cafile=str(a.ca))

def raw(*x):
    return subprocess.check_output(adb + list(x), stderr=subprocess.STDOUT).decode()

def guard():
    assert raw('emu', 'avd', 'name').splitlines()[0] == 'viptv-16k-isolated'
    assert raw('shell', 'getprop', 'ro.build.version.sdk').strip() == '36'
    assert raw('shell', 'getconf', 'PAGE_SIZE').strip() == '16384'
    assert raw('shell', 'getprop', 'bionic.linker.16kb.app_compat.enabled').strip() == 'false'
    assert raw('shell', 'getprop', 'pm.16kb.app_compat.disabled').strip() == 'true'

def run(*x):
    guard()
    return raw(*x)

def status():
    with urllib.request.urlopen(a.origin + '/qa/status', context=ctx, timeout=8) as r:
        return json.load(r)

def atomic(path, value):
    temporary = path.with_suffix(path.suffix + '.tmp')
    temporary.write_text(json.dumps(value, indent=2))
    temporary.replace(path)

started = time.monotonic()
start_utc = datetime.now(timezone.utc).isoformat()
base = {'driver_pid': os.getpid(), 'avd': 'viptv-16k-isolated', 'serial': 'emulator-5586',
        'apk_sha256': hashlib.sha256(a.apk.read_bytes()).hexdigest(), 'sustain_requested_ms': a.sustain_ms,
        'started_utc': start_utc, 'host_started_monotonic': started, 'qualification_kind': 'short capture mechanism only, not endurance' if a.short_capture_proof else 'actual >=40-minute endurance with original-player OSD acknowledgement', 'fixture': 'synthetic H264/AAC rolling HLS; actual normal-speed renderer; host offset is not elapsed time'}
atomic(a.root / 'start.json', base)
guard()
assert status()['counts'].get('POST /api/auth/device/login', 0) == 0, 'Use a fresh owned fixture'
run('reverse', 'tcp:18586', 'tcp:18586')
run('install', '-r', str(a.apk))
run('shell', 'pm', 'clear', 'org.viptv.experiment')
run('logcat', '-c')
launch = ['shell', 'am', 'start', '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.LAUNCHER',
          '-f', '0x10200000', '-n', 'org.viptv.experiment/.LifecycleQaActivity', '--es', 'qa_origin', a.origin,
          '--el', 'qa_sustain_ms', str(a.sustain_ms), '--ez', 'qa_final_capture', 'true']
stream = subprocess.Popen(adb + ['logcat', '-v', 'brief', 'VIPTV_LIFECYCLE_QA:I', 'AndroidRuntime:E', '*:S'],
                          stdout=subprocess.PIPE, bufsize=0)
passed = False
initial_capture = final_capture = False
initial_accessibility = final_accessibility = False
capture_acknowledged = False
capture_proof = {}
sustain_started = None
sustain_host_ms = None
last_sample_at = None
last_heartbeat = time.monotonic()
latest = {}
samples = []
seeks = []
summary = {}
error = None
log = (a.root / 'activity.log').open('w', buffering=1)
sample_log = (a.root / 'samples.jsonl').open('w', buffering=1)

def live(stage):
    atomic(a.root / 'live-progress.json', dict(base, stage=stage, host_wall_ms=round((time.monotonic()-started)*1000),
           host_sustain_wall_ms=round((time.monotonic()-sustain_started)*1000) if sustain_started else None,
           last_sample_age_ms=round((time.monotonic()-last_sample_at)*1000) if last_sample_at else None,
           samples=len(samples), seeks=len(seeks), latest=latest))

def capture(expected, final):
    name = 'player-endurance-clock' if final else 'player-clock'
    (a.root / (name+'.png')).write_bytes(subprocess.check_output(adb + ['exec-out', 'screencap', '-p']))
    run('shell', 'uiautomator', 'dump', '/sdcard/endurance-clock.xml')
    xml = run('exec-out', 'cat', '/sdcard/endurance-clock.xml')
    (a.root / (name+'.xml')).write_text(xml)
    return expected in [n.get('text', '') for n in ET.fromstring(xml).iter('node')]


def acknowledge_final_capture(capture_id, expected):
    snap = status()
    requested = snap['captures'][capture_id]['request']
    assert requested['paused'] and requested['app_pid'] == int(app_pid)
    assert not any(k.startswith('DELETE /api/v2/playback/') and v for k,v in snap['counts'].items())
    assert capture(expected, True), 'Original-player elapsed accessibility text missing'
    xml = ET.parse(a.root / 'player-endurance-clock.xml')
    assert any(n.get('class') == 'android.widget.SeekBar' for n in xml.iter('node'))
    assert any(n.get('content-desc') == 'Playback position' for n in xml.iter('node'))
    semantics = requested['semantics']
    assert expected in semantics['texts'] and len(semantics['sliders']) == 1
    slider = semantics['sliders'][0]
    fraction = requested['position_ms'] / requested['duration_ms']
    assert slider['min'] == 0 and slider['max'] == 1 and abs(slider['current']-fraction) <= 0.00001
    assert abs(requested['renderer_title_ms']-requested['position_ms']) <= 250
    assert abs(requested['progress_ms']-requested['renderer_title_ms']) <= 250
    after = status()
    assert not any(k.startswith('DELETE /api/v2/playback/') and v for k,v in after['counts'].items())
    ack = {'id': capture_id, 'position_ms': requested['position_ms'], 'player_id': requested['player_id'],
           'slider_fraction': slider['current'], 'text_verified': True, 'slider_verified': True,
           'accessibility_verified': True, 'screenshot_written': True}
    proof = {'request': requested, 'ack': ack, 'lease_closes_before_ack': 0,
             'screenshot_sha256': hashlib.sha256((a.root/'player-endurance-clock.png').read_bytes()).hexdigest(),
             'xml_sha256': hashlib.sha256((a.root/'player-endurance-clock.xml').read_bytes()).hexdigest(),
             'original_app_pid_still_present': run('shell','pidof','org.viptv.experiment').strip() == app_pid}
    assert proof['original_app_pid_still_present']
    atomic(a.root/'capture-proof.json', proof)
    req = urllib.request.Request(a.origin+'/qa/capture/ack', data=json.dumps(ack).encode(), method='POST', headers={'Content-Type':'application/json'})
    with urllib.request.urlopen(req,context=ctx,timeout=8) as response:
        assert response.status == 200
    return proof

try:
    live('launching')
    (a.root / 'launch.txt').write_text(run(*launch[:3], '-W', *launch[3:]))
    app_pid = run('shell', 'pidof', 'org.viptv.experiment').strip()
    assert app_pid.isdigit()
    base['app_pid'] = int(app_pid)
    deadline = time.monotonic() + a.sustain_ms/1000 + 180
    while time.monotonic() < deadline:
        if time.monotonic() - last_heartbeat >= 60:
            live('sustaining' if sustain_started else 'baseline lifecycle')
            snap = status()
            atomic(a.root / 'server-status-live.json', snap)
            print(json.dumps({'heartbeat': True, 'host_wall_ms': round((time.monotonic()-started)*1000),
                  'host_sustain_wall_ms': round((time.monotonic()-sustain_started)*1000) if sustain_started else None,
                  'samples': len(samples), 'seeks': len(seeks), 'latest': latest,
                  'progress_writes': len(snap['progress']), 'leases': len(snap['leases'])}), flush=True)
            last_heartbeat = time.monotonic()
        if last_sample_at and time.monotonic() - last_sample_at > 75:
            raise TimeoutError('No actual endurance clock sample for 75 seconds')
        if not select.select([stream.stdout], [], [], 1)[0]:
            continue
        line = stream.stdout.readline().decode()
        if not line:
            raise RuntimeError('Owned logcat stream ended')
        if 'AndroidRuntime(' in line and not re.search(r'\(\s*' + app_pid + r'\):', line):
            with (a.root / 'unrelated-runtime.log').open('a') as unrelated:
                unrelated.write(line)
            continue
        log.write(line)
        if 'sustain_begin=true' in line:
            sustain_started = time.monotonic()
            base['host_sustain_started_monotonic'] = sustain_started
            last_sample_at = sustain_started
            live('sustaining')
        if 'sustain elapsed_ms=' in line:
            fields = dict(re.findall(r'(\w+)=(-?[0-9.]+)', line))
            latest = {k: float(v) if '.' in v else int(v) for k,v in fields.items()}
            latest['host_sustain_wall_ms'] = round((time.monotonic()-sustain_started)*1000)
            samples.append(latest.copy()); sample_log.write(json.dumps(latest)+'\n')
            last_sample_at = time.monotonic(); live('sustaining')
        if 'seek_verified ' in line:
            fields = dict(re.findall(r'(target_ms|position_ms|input_offset_ms|window_ms)=(\d+)', line))
            seeks.append({k:int(v) for k,v in fields.items()})
        if 'sustain_pass=true' in line:
            sustain_host_ms = round((time.monotonic()-sustain_started)*1000)
            summary = {k:int(v) for k,v in re.findall(r'(\w+)=(\d+)', line)}
            live('final capture and tracking')
        if 'capture_elapsed expected=' in line:
            initial_capture = True
            initial_accessibility = capture(re.search(r'expected=([0-9:]+)', line).group(1), False)
        if 'capture_endurance_ready id=' in line:
            final_capture = True
            capture_id = re.search(r'id=([^ ]+)',line).group(1)
            expected = re.search(r'expected=([0-9:]+)',line).group(1)
            capture_proof = acknowledge_final_capture(capture_id, expected)
            final_accessibility = True
        if 'capture_endurance_acknowledged id=' in line:
            capture_acknowledged = True
        if 'await_home paused=' in line:
            run('shell', 'input', 'keyevent', 'KEYCODE_HOME')
        if 'background_paused position_ms=' in line:
            time.sleep(.35); run(*launch)
        if 'FAIL ' in line or 'FATAL EXCEPTION' in line:
            raise RuntimeError('Actual application qualification failed; inspect activity.log')
        if 'PASS profile_focus=true' in line:
            passed = True
            break
    else:
        raise TimeoutError('Endurance driver deadline exceeded')
except Exception as e:
    error = f'{type(e).__name__}: {e}'
finally:
    stream.terminate(); stream.wait(timeout=5); log.close(); sample_log.close()
    snap = status(); atomic(a.root / 'server-status.json', snap)
    starts = [v['position_millis'] for v in snap['events'] if v['event']=='start']
    exact_seeks = all(v['target_ms'] in starts for v in seeks)
    result = dict(base, passed=bool(passed and initial_accessibility and final_accessibility and capture_acknowledged and
                  sustain_host_ms is not None and sustain_host_ms >= (a.sustain_ms if a.short_capture_proof else 2_400_000) and summary.get('wall_ms',0)>=a.sustain_ms and exact_seeks),
                  error=error, initial_capture=initial_capture, initial_accessibility=initial_accessibility,
                  final_capture=final_capture, final_accessibility=final_accessibility, capture_acknowledged=capture_acknowledged, capture_proof=capture_proof,
                  host_total_wall_ms=round((time.monotonic()-started)*1000), host_sustain_wall_ms=sustain_host_ms,
                  guest_sustain=summary, samples=len(samples), seeks=seeks, each_seek_tracking_start_exact=exact_seeks,
                  max_sampled_clock_difference_ms=max([abs(v['clock_error_ms']) for v in samples], default=None),
                  counts=snap['counts'], progress_writes=len(snap['progress']))
    atomic(a.root / 'result.json', result); live('passed' if result['passed'] else 'failed')
    print(json.dumps(result), flush=True)
if not result['passed']:
    raise SystemExit(1)
