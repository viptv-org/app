#!/usr/bin/env python3
"""Run the owned Android probe through a live shared ADB server only."""
import argparse
import hashlib
import json
from pathlib import Path
import shlex
import socket
import stat
import struct
import subprocess
import time


SHARED_SOCKET = Path('/run/adb/server.sock')
PACKAGE = 'org.viptv.subtitleprobe'
ACTIVITY = PACKAGE + '/org.viptv.app.player.SubtitlePcmProbeActivity'


def exact_read(connection, size):
    chunks = bytearray()
    while len(chunks) < size:
        block = connection.recv(size - len(chunks))
        if not block:
            raise RuntimeError('Shared ADB connection closed')
        chunks.extend(block)
    return bytes(chunks)


def service(connection, value):
    data = value.encode()
    connection.sendall(f'{len(data):04x}'.encode() + data)
    if exact_read(connection, 4) != b'OKAY':
        size = int(exact_read(connection, 4), 16)
        if size > 4096:
            raise RuntimeError('Invalid ADB rejection')
        raise RuntimeError(exact_read(connection, size).decode(errors='replace'))


def preflight(path=SHARED_SOCKET, namespace_table=Path('/proc/net/unix')):
    if not path.exists() or not stat.S_ISSOCK(path.stat().st_mode):
        raise RuntimeError('Shared ADB socket is missing; refuse to start a local server')
    for line in namespace_table.read_text().splitlines()[1:]:
        fields = line.split(maxsplit=7)
        if len(fields) == 8 and fields[7] == str(path) and int(fields[3], 16) & 0x10000:
            raise RuntimeError('Shared ADB listener belongs to the coding network namespace')
    with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as connection:
        connection.settimeout(3)
        connection.connect(str(path))
        service(connection, 'host:version')
        size = int(exact_read(connection, 4), 16)
        if size > 64:
            raise RuntimeError('Invalid shared ADB version response')
        version = exact_read(connection, size).decode()
        int(version, 16)
        return version


class SharedAdb:
    def __init__(self, path=SHARED_SOCKET, serial='emulator-5554'):
        self.path = path
        self.serial = serial

    def shell(self, *arguments, data=b'', timeout=30, check=True):
        preflight(self.path)
        if len(data) > 64 * 1024 * 1024:
            raise RuntimeError('Probe upload exceeds its bound')
        with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as connection:
            connection.settimeout(timeout)
            connection.connect(str(self.path))
            service(connection, 'host:transport:' + self.serial)
            service(connection, 'shell,v2,raw:' + shlex.join(arguments))
            for offset in range(0, len(data), 64 * 1024):
                block = data[offset:offset + 64 * 1024]
                connection.sendall(struct.pack('<BI', 0, len(block)) + block)
            connection.sendall(struct.pack('<BI', 4, 0))
            output = bytearray()
            errors = bytearray()
            deadline = time.monotonic() + timeout
            while True:
                if time.monotonic() > deadline:
                    raise TimeoutError('Probe shell deadline exceeded')
                kind, size = struct.unpack('<BI', exact_read(connection, 5))
                if size > 1024 * 1024:
                    raise RuntimeError('ADB shell packet exceeds its bound')
                payload = exact_read(connection, size)
                if kind == 1:
                    output.extend(payload)
                elif kind == 2:
                    errors.extend(payload)
                elif kind == 3:
                    code = int.from_bytes(payload, 'little')
                    if check and code != 0:
                        raise RuntimeError('Probe command failed: ' + errors.decode(errors='replace')[-1000:])
                    return code, bytes(output), bytes(errors)
                if len(output) > 16 * 1024 * 1024 or len(errors) > 256 * 1024:
                    raise RuntimeError('ADB shell output exceeds its bound')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--socket', type=Path, default=SHARED_SOCKET)
    parser.add_argument('--console-tool', default='/home/vynxc/.local/bin/server-emu')
    parser.add_argument('--fixture', type=Path)
    parser.add_argument('--captions', type=Path)
    options = parser.parse_args()
    if bool(options.fixture) != bool(options.captions):
        parser.error('--fixture and --captions must be provided together')
    version = preflight(options.socket)

    def console(action):
        command = [options.console_tool, action]
        if action != 'list':
            command += ['--device', 'phone']
        result = subprocess.run(command, capture_output=True, check=True, timeout=120)
        return json.loads(result.stdout)

    devices = console('list')['devices']
    if any(device['status'] not in ['stopped', 'error'] for device in devices):
        raise RuntimeError('Another actor owns a running shared emulator')
    adb = SharedAdb(options.socket)
    report = {'rawHostVersion': version, 'noCodingNamespaceListener': True,
              'apkSha256': hashlib.sha256(options.apk.read_bytes()).hexdigest()}
    owned = False
    try:
        started = time.monotonic()
        console('start')
        owned = True
        deadline = time.monotonic() + 90
        while True:
            try:
                if adb.shell('getprop', 'sys.boot_completed', timeout=5)[1].strip() == b'1':
                    break
            except (RuntimeError, OSError):
                pass
            if time.monotonic() > deadline:
                raise RuntimeError('Phone did not boot within the bounded attempt')
            time.sleep(1)
        report['bootSeconds'] = round(time.monotonic() - started, 3)
        report['api'] = adb.shell('getprop', 'ro.build.version.sdk')[1].decode().strip()
        apk = options.apk.read_bytes()
        adb.shell('cmd', 'package', 'install', '-r', '-S', str(len(apk)), data=apk, timeout=60)
        adb.shell('am', 'force-stop', PACKAGE)
        adb.shell('run-as', PACKAGE, 'rm', '-f', 'files/result.txt')
        absent = adb.shell('run-as', PACKAGE, 'cat', 'files/result.txt', check=False)[0] != 0
        report['resultAbsentBeforeLaunch'] = absent
        if not absent:
            raise RuntimeError('Owned probe result was not fresh')
        launch = ['am', 'start', '-W', '-n', ACTIVITY]
        if options.fixture:
            adb.shell('run-as', PACKAGE, 'sh', '-c', 'cat > files/sync.wav', data=options.fixture.read_bytes(), timeout=60)
            adb.shell('run-as', PACKAGE, 'sh', '-c', 'cat > files/sync.vtt', data=options.captions.read_bytes())
            launch += ['--ez', 'subtitleSync', 'true']
            report['fixtureSha256'] = hashlib.sha256(options.fixture.read_bytes()).hexdigest()
            report['captionsSha256'] = hashlib.sha256(options.captions.read_bytes()).hexdigest()
        adb.shell(*launch)
        started = time.monotonic()
        deadline = started + (180 if options.fixture else 40)
        while True:
            result = adb.shell('run-as', PACKAGE, 'cat', 'files/result.txt', check=False)
            if result[0] == 0:
                break
            if time.monotonic() > deadline:
                raise RuntimeError('Owned probe did not finish')
            time.sleep(.5)
        report['wallSeconds'] = round(time.monotonic() - started, 3)
        report['result'] = result[1].decode().strip()
        report['scope'] = 'Owned fixture only; synthetic wiring is not held-out speech accuracy'
    finally:
        if owned:
            console('stop')
        report['finalDevices'] = [{'id': item['id'], 'status': item['status']} for item in console('list')['devices']]
        options.output.parent.mkdir(parents=True, exist_ok=True)
        options.output.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report))
    return 0 if 'state=ended' in report.get('result', '') else 2


if __name__ == '__main__':
    raise SystemExit(main())
