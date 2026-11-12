import socket
import struct
import tempfile
import threading
import unittest
from pathlib import Path
from unittest.mock import patch

import run_android_probe as probe


class SharedAdbPreflightTest(unittest.TestCase):
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
