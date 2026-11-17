#!/usr/bin/env python3
# The adb CLI may start a daemon in the coding network namespace.
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
import zipfile


SHARED_SOCKET = Path('/run/adb/server.sock')
PACKAGE = 'org.viptv.subtitleprobe'
ACTIVITY = PACKAGE + '/org.viptv.app.player.SubtitlePcmProbeActivity'


def inspect_apk(apk, aapt2=None, require_native=False, expected_native=None):
    if apk.stat().st_size > 64 * 1024 * 1024:
        raise RuntimeError('Probe APK exceeds its upload bound')
    if aapt2 is None:
        tools = list((Path.home() / 'Android/Sdk/build-tools').glob('*/aapt2'))
        if not tools:
            raise RuntimeError('Provide --aapt2 to verify the owned package before install')
        aapt2 = sorted(tools, key=lambda value: tuple(int(part) for part in value.parent.name.split('.') if part.isdecimal()))[-1]
    result = subprocess.run([str(aapt2), 'dump', 'packagename', str(apk)], capture_output=True, check=True, timeout=10)
    if result.stdout.decode().strip() != PACKAGE:
        raise RuntimeError('Refuse to install a package other than the owned subtitle probe')
    metadata = {'package': PACKAGE}
    with zipfile.ZipFile(apk) as archive:
        name = 'lib/x86_64/libviptv_core.so'
        if name not in archive.namelist():
            if require_native or expected_native:
                raise RuntimeError('Matching native core is missing from the probe APK')
            return metadata
        if archive.namelist().count(name) != 1 or archive.getinfo(name).file_size > 64 * 1024 * 1024:
            raise RuntimeError('Invalid native core archive entry')
        digest = hashlib.sha256()
        with archive.open(name) as library:
            while chunk := library.read(64 * 1024):
                digest.update(chunk)
        metadata['nativeSoSha256'] = digest.hexdigest()
        metadata['nativeSoBytes'] = archive.getinfo(name).file_size
        if expected_native and metadata['nativeSoSha256'] != expected_native:
            raise RuntimeError('Native core does not match the declared build')
        metadata['nativeNoticePackaged'] = 'assets/licenses/webrtc-vad.LICENSE' in archive.namelist()
        if not metadata['nativeNoticePackaged']:
            raise RuntimeError('Native WebRTC notice is missing')
    return metadata


def exact_read(connection, size, deadline=None):
    chunks = bytearray()
    while len(chunks) < size:
        if deadline is not None:
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise TimeoutError('Shared ADB response deadline exceeded')
            connection.settimeout(remaining)
        block = connection.recv(size - len(chunks))
        if not block:
            raise RuntimeError('Shared ADB connection closed')
        chunks.extend(block)
    return bytes(chunks)


def service(connection, value, deadline=None):
    data = value.encode()
    connection.sendall(f'{len(data):04x}'.encode() + data)
    if exact_read(connection, 4, deadline) != b'OKAY':
        size = int(exact_read(connection, 4, deadline), 16)
        if size > 4096:
            raise RuntimeError('Invalid ADB rejection')
        raise RuntimeError(exact_read(connection, size, deadline).decode(errors='replace'))


def preflight(path=SHARED_SOCKET, namespace_table=Path('/proc/net/unix')):
    if not path.exists() or not stat.S_ISSOCK(path.stat().st_mode):
        raise RuntimeError('Shared ADB socket is missing; refuse to start a local server')
    for line in namespace_table.read_text().splitlines()[1:]:
        fields = line.split(maxsplit=7)
        if len(fields) == 8 and fields[7] == str(path) and int(fields[3], 16) & 0x10000:
            raise RuntimeError('Shared ADB listener belongs to the coding network namespace')
    with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as connection:
        deadline = time.monotonic() + 3
        connection.settimeout(3)
        connection.connect(str(path))
        service(connection, 'host:version', deadline)
        size = int(exact_read(connection, 4, deadline), 16)
        if size > 64:
            raise RuntimeError('Invalid shared ADB version response')
        version = exact_read(connection, size, deadline).decode()
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
            deadline = time.monotonic() + timeout
            connection.settimeout(timeout)
            connection.connect(str(self.path))
            service(connection, 'host:transport:' + self.serial, deadline)
            service(connection, 'shell,v2,raw:' + shlex.join(arguments), deadline)
            for offset in range(0, len(data), 64 * 1024):
                remaining = deadline - time.monotonic()
                if remaining <= 0:
                    raise TimeoutError('Probe upload deadline exceeded')
                connection.settimeout(remaining)
                block = data[offset:offset + 64 * 1024]
                connection.sendall(struct.pack('<BI', 0, len(block)) + block)
            connection.sendall(struct.pack('<BI', 4, 0))
            output = bytearray()
            errors = bytearray()
            while True:
                if time.monotonic() > deadline:
                    raise TimeoutError('Probe shell deadline exceeded')
                kind, size = struct.unpack('<BI', exact_read(connection, 5, deadline))
                if size > 1024 * 1024:
                    raise RuntimeError('ADB shell packet exceeds its bound')
                payload = exact_read(connection, size, deadline)
                if kind == 1:
                    output.extend(payload)
                elif kind == 2:
                    errors.extend(payload)
                elif kind == 3:
                    if len(payload) not in [1, 4]:
                        raise RuntimeError('Invalid ADB shell exit status')
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
    parser.add_argument('--console-tool', default=str(Path.home() / '.local/bin/server-emu'))
    parser.add_argument('--aapt2', type=Path)
    parser.add_argument('--native-so-sha256')
    parser.add_argument('--fixture', type=Path)
    parser.add_argument('--captions', type=Path)
    parser.add_argument('--expected-offset-us', type=int)
    options = parser.parse_args()
    if bool(options.fixture) != bool(options.captions):
        parser.error('--fixture and --captions must be provided together')
    metadata = inspect_apk(options.apk, options.aapt2, bool(options.fixture), options.native_so_sha256)
    version = preflight(options.socket)

    def console(action):
        command = [options.console_tool, action]
        if action != 'list':
            command += ['--device', 'phone']
        result = subprocess.run(command, capture_output=True, check=True, timeout=120)
        return json.loads(result.stdout)

    devices = console('list')['devices']
    if any(device['status'] != 'stopped' or device.get('viewers', 0) > 0 for device in devices):
        raise RuntimeError('Another actor owns a running shared emulator')
    adb = SharedAdb(options.socket)
    report = {'rawHostVersion': version, 'noCodingNamespaceListener': True, **metadata,
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
            try:
                console('stop')
            except Exception as error:
                report['stopErrorType'] = type(error).__name__
        try:
            report['finalDevices'] = [{'id': item['id'], 'status': item['status']} for item in console('list')['devices']]
            report['cleanupVerified'] = all(item['status'] == 'stopped' for item in report['finalDevices'])
        except Exception as error:
            report['cleanupVerified'] = False
            report['cleanupErrorType'] = type(error).__name__
        options.output.parent.mkdir(parents=True, exist_ok=True)
        options.output.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report))
    if not report.get('cleanupVerified'):
        raise RuntimeError('Owned probe cleanup did not verify stopped device state')
    fields = dict(item.split('=', 1) for item in report.get('result', '').split() if '=' in item)
    if fields.get('state') != 'ended':
        return 2
    if options.fixture:
        checks = ['defaultOff', 'manual', 'off', 'reset', 'stale', 'samePlayer']
        if any(fields.get(key) != 'true' for key in checks) or fields.get('mediaTransitions') != '1':
            return 2
        if int(fields.get('acceptedFrames', '0')) < 3000 or not fields.get('workerThread', '').startswith('DefaultDispatcher-worker-'):
            return 2
        offset = int(fields.get('acceptedOffsetUs', '0'))
        if options.expected_offset_us is not None and abs(offset - options.expected_offset_us) > 250_000:
            return 2
    elif any(fields.get(key) != value for key, value in [('ordered', 'true'), ('nonzero', 'true'), ('stage', '3')]):
        return 2
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
