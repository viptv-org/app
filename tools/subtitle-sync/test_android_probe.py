import socket
import hashlib
import subprocess
import zipfile
import struct
import tempfile
import json
import sys
import threading
import unittest
from pathlib import Path
from unittest.mock import patch

import run_android_probe as probe


class SharedAdbPreflightTest(unittest.TestCase):
    def test_wrong_apk_package_is_rejected_before_any_device_action(self):
        with tempfile.TemporaryDirectory() as root:
            apk = Path(root) / 'wrong.apk'
            apk.write_bytes(b'owned test')
            with patch.object(probe.subprocess, 'run', return_value=subprocess.CompletedProcess([], 0, b'other.package', b'')):
                with self.assertRaisesRegex(RuntimeError, 'other than the owned'):
                    probe.inspect_apk(apk, Path('aapt2'))

    def test_native_apk_requires_matching_binary_and_packaged_notice(self):
        with tempfile.TemporaryDirectory() as root:
            apk = Path(root) / 'owned.apk'
            data = b'owned native artifact'
            with zipfile.ZipFile(apk, 'w') as archive:
                archive.writestr('lib/x86_64/libviptv_core.so', data)
                archive.writestr('assets/licenses/webrtc-vad.LICENSE', b'owned test notice')
            with patch.object(probe.subprocess, 'run', return_value=subprocess.CompletedProcess([], 0, probe.PACKAGE.encode(), b'')):
                expected = hashlib.sha256(data).hexdigest()
                self.assertTrue(probe.inspect_apk(apk, Path('aapt2'), True, expected)['nativeNoticePackaged'])
                with self.assertRaisesRegex(RuntimeError, 'does not match'):
                    probe.inspect_apk(apk, Path('aapt2'), True, '0' * 64)

    def test_successful_playback_cannot_hide_unverified_device_cleanup(self):
        with tempfile.TemporaryDirectory() as root:
            apk = Path(root) / 'owned.apk'
            apk.write_bytes(b'owned APK')
            output = Path(root) / 'result.json'
            console_results = [
                {'devices': [{'id': 'phone', 'status': 'stopped'}]},
                {'ok': True}, {'ok': True},
                {'devices': [{'id': 'phone', 'status': 'error'}]},
            ]
            cats = 0

            def shell(*args, **kwargs):
                nonlocal cats
                if args[:2] == ('getprop', 'sys.boot_completed'): return (0, b'1', b'')
                if args[:2] == ('getprop', 'ro.build.version.sdk'): return (0, b'35', b'')
                if args[-2:] == ('cat', 'files/result.txt'):
                    cats += 1
                    return (1, b'', b'') if cats == 1 else (0, b'state=ended ordered=true nonzero=true stage=3', b'')
                return (0, b'', b'')

            def command(*args, **kwargs):
                return subprocess.CompletedProcess([], 0, json.dumps(console_results.pop(0)).encode(), b'')

            argv = ['probe', '--apk', str(apk), '--output', str(output)]
            with patch.object(sys, 'argv', argv), patch.object(probe, 'inspect_apk', return_value={'package': probe.PACKAGE}), \
                    patch.object(probe, 'preflight', return_value='0029'), patch.object(probe.subprocess, 'run', side_effect=command), \
                    patch.object(probe.SharedAdb, 'shell', side_effect=shell), patch('builtins.print'):
                with self.assertRaisesRegex(RuntimeError, 'cleanup did not verify'):
                    probe.main()
            self.assertFalse(json.loads(output.read_text())['cleanupVerified'])
            self.assertEqual([], console_results)

    def test_missing_socket_refuses_without_launching_any_process(self):
        with tempfile.TemporaryDirectory() as root, patch.object(probe.subprocess, 'run') as launch:
            with self.assertRaisesRegex(RuntimeError, 'missing'):
                probe.preflight(Path(root) / 'missing.sock')
            launch.assert_not_called()

    def test_coding_namespace_listener_is_rejected_before_use(self):
        with tempfile.TemporaryDirectory() as root, socket.socket(socket.AF_UNIX) as server:
            path = Path(root) / 'adb.sock'
            server.bind(str(path))
            server.listen()
            with self.assertRaisesRegex(RuntimeError, 'coding network namespace'):
                probe.preflight(path)

    def test_live_remote_socket_requires_framed_adb_version(self):
        with tempfile.TemporaryDirectory() as root, socket.socket(socket.AF_UNIX) as server:
            path = Path(root) / 'adb.sock'
            table = Path(root) / 'unix'
            table.write_text('Num RefCount Protocol Flags Type St Inode Path\n')
            server.bind(str(path))
            server.listen()
            received = []

            def reply():
                with server.accept()[0] as client:
                    size = int(probe.exact_read(client, 4), 16)
                    received.append(probe.exact_read(client, size))
                    client.sendall(b'OKAY00040029')

            thread = threading.Thread(target=reply)
            thread.start()
            self.assertEqual('0029', probe.preflight(path, table))
            thread.join(timeout=2)
            self.assertFalse(thread.is_alive())
            self.assertEqual([b'host:version'], received)

    def test_shell_v2_uses_raw_transport_and_preserves_output_and_status(self):
        with tempfile.TemporaryDirectory() as root, socket.socket(socket.AF_UNIX) as server:
            path = Path(root) / 'adb.sock'
            server.bind(str(path))
            server.listen()
            received = []

            def reply():
                with server.accept()[0] as client:
                    for _ in range(2):
                        size = int(probe.exact_read(client, 4), 16)
                        received.append(probe.exact_read(client, size))
                        client.sendall(b'OKAY')
                    received.append(probe.exact_read(client, 5))
                    client.sendall(struct.pack('<BI', 1, 2) + b'1\n' + struct.pack('<BI', 3, 1) + b'\0')

            thread = threading.Thread(target=reply)
            thread.start()
            with patch.object(probe, 'preflight', return_value='0029') as guard:
                result = probe.SharedAdb(path).shell('getprop', 'sys.boot_completed')
            thread.join(timeout=2)
            self.assertFalse(thread.is_alive())
            guard.assert_called_once_with(path)
            self.assertEqual((0, b'1\n', b''), result)
            self.assertEqual([b'host:transport:emulator-5554', b'shell,v2,raw:getprop sys.boot_completed', struct.pack('<BI', 4, 0)], received)


if __name__ == '__main__':
    unittest.main()
