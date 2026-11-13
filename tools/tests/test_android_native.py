import importlib.util
from pathlib import Path
import tempfile
import unittest

SPEC = importlib.util.spec_from_file_location('android_native', Path(__file__).parents[1] / 'android_native.py')
native = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(native)


class NativeCacheTest(unittest.TestCase):
    def test_fingerprint_is_relocatable_and_ignores_server_and_kotlin(self):
        with tempfile.TemporaryDirectory() as first, tempfile.TemporaryDirectory() as second:
            for directory in (first, second):
                root = Path(directory)
                (root / 'native/core').mkdir(parents=True)
                (root / 'native/core/Cargo.toml').write_text('[workspace]\n')
                (root / 'tools').mkdir()
                (root / 'tools/build_engine.py').write_text('build()\n')
            tools = {'rust': 'rustc 1.98.0', 'ndk': '28.2', 'cargo-ndk': '4.1.2'}
            expected = native.fingerprint(Path(first), 'engine', ['x86_64'], tools)
            root = Path(second)
            (root / 'gradle.properties').write_text('viptv.defaultServer=https://example.com\n')
            (root / 'Main.kt').write_text('fun main() {}\n')
            self.assertEqual(expected, native.fingerprint(root, 'engine', ['x86_64'], tools))
            (root / 'native/core/Cargo.toml').write_text('[workspace]\nresolver="2"\n')
            self.assertNotEqual(expected, native.fingerprint(root, 'engine', ['x86_64'], tools))

    def test_restoration_requires_every_abi_library_license_and_provenance(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            generated = root / 'androidApp/build/generated'
            for abi in native.ABIS:
                path = generated / 'rustJni' / abi / 'libviptv_core.so'
                path.parent.mkdir(parents=True)
                elf_class, machine = native.ABIS[abi]
                path.write_bytes(b'\x7fELF' + bytes([elf_class, 1]) + bytes(12) + machine.to_bytes(2, 'little') + bytes(80))
            notices = generated / 'subtitleLicenses/licenses'
            notices.mkdir(parents=True)
            for name in ['webrtc-vad.LICENSE', 'webrtc-vad-source.json', 'webrtc-vad-48k.patch']:
                (notices / name).write_text('notice')
            native.seal(root, 'engine', list(native.ABIS), 'known-key')
            self.assertTrue(native.validate(root, 'engine', list(native.ABIS), 'known-key'))
            self.assertFalse(native.validate(root, 'engine', list(native.ABIS), 'changed-toolchain'))
            library = generated / 'rustJni/arm64-v8a/libviptv_core.so'
            original = library.read_bytes()
            library.write_bytes(original[:-1])
            self.assertFalse(native.validate(root, 'engine', list(native.ABIS), 'known-key'))
            library.write_bytes(original)
            (notices / 'webrtc-vad.LICENSE').unlink()
            self.assertFalse(native.validate(root, 'engine', list(native.ABIS), 'known-key'))


if __name__ == '__main__':
    unittest.main()
