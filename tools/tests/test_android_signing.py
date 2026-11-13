import base64
import hashlib
import importlib.util
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location('android_signing', Path(__file__).parents[1] / 'android_signing.py')
signing = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(signing)


class AndroidSigningTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.fixture = tempfile.TemporaryDirectory()
        cls.keystore = Path(cls.fixture.name) / 'fixture.jks'
        cls.credentials = {
            'ANDROID_RELEASE_KEYSTORE_BASE64': '',
            'ANDROID_RELEASE_STORE_PASSWORD': 'fixture-password',
            'ANDROID_RELEASE_KEY_ALIAS': 'fixture',
            'ANDROID_RELEASE_KEY_PASSWORD': 'fixture-password',
        }
        with patch.dict(os.environ, cls.credentials):
            subprocess.run([
                'keytool', '-genkeypair', '-keystore', str(cls.keystore), '-storetype', 'JKS',
                '-alias', 'fixture', '-storepass:env', 'ANDROID_RELEASE_STORE_PASSWORD',
                '-keypass:env', 'ANDROID_RELEASE_KEY_PASSWORD', '-keyalg', 'RSA',
                '-dname', 'CN=Signing Test', '-validity', '1',
            ], check=True, capture_output=True)
            certificate = subprocess.run([
                'keytool', '-exportcert', '-keystore', str(cls.keystore), '-alias', 'fixture',
                '-storepass:env', 'ANDROID_RELEASE_STORE_PASSWORD',
            ], check=True, capture_output=True).stdout
        cls.fingerprint = hashlib.sha256(certificate).hexdigest().upper()
        cls.credentials['ANDROID_RELEASE_KEYSTORE_BASE64'] = base64.b64encode(cls.keystore.read_bytes()).decode()

    @classmethod
    def tearDownClass(cls):
        cls.fixture.cleanup()

    def test_wrong_certificate_fails_and_removes_temporary_keystore(self):
        with tempfile.TemporaryDirectory() as directory, patch.dict(os.environ, self.credentials):
            destination = Path(directory) / 'release.keystore'
            with self.assertRaisesRegex(ValueError, 'certificate'):
                signing.prepare(destination)
            self.assertFalse(destination.exists())

    def test_verified_key_is_private_and_wrong_key_password_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory, patch.dict(os.environ, self.credentials), patch.object(signing, 'EXPECTED_CERT_SHA256', self.fingerprint):
            destination = Path(directory) / 'release.keystore'
            signing.prepare(destination)
            self.assertEqual(destination.read_bytes(), self.keystore.read_bytes())
            self.assertEqual(destination.stat().st_mode & 0o777, 0o600)
            destination.unlink()
            with patch.dict(os.environ, {'ANDROID_RELEASE_KEY_PASSWORD': 'wrong-password'}):
                with self.assertRaises(subprocess.CalledProcessError):
                    signing.prepare(destination)
            self.assertFalse(destination.exists())

    def test_existing_destination_is_preserved(self):
        with tempfile.TemporaryDirectory() as directory, patch.dict(os.environ, self.credentials):
            destination = Path(directory) / 'release.keystore'
            destination.write_bytes(b'keep-existing-key')
            with self.assertRaises(FileExistsError):
                signing.prepare(destination)
            self.assertEqual(destination.read_bytes(), b'keep-existing-key')

    def make_apk(self, package='org.viptv.experiment', version_code=None, version_name=None):
        root = Path(__file__).resolve().parents[2]
        count = subprocess.check_output(['git', 'rev-list', '--count', 'HEAD'], cwd=root, text=True).strip()
        commit = subprocess.check_output(['git', 'rev-parse', '--short=7', 'HEAD'], cwd=root, text=True).strip()
        manifest = Path(self.fixture.name) / 'AndroidManifest.xml'
        manifest.write_text(
            f'<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="{package}" '
            f'android:versionCode="{version_code or 1000 + int(count)}" '
            f'android:versionName="{version_name or f"0.1.{count}+{commit}"}">'
            '<uses-sdk android:minSdkVersion="26" android:targetSdkVersion="36"/>'
            '<application android:hasCode="false"/></manifest>'
        )
        apk = Path(self.fixture.name) / 'fixture.apk'
        sdk = Path(os.environ.get('ANDROID_HOME', Path.home() / 'Android/Sdk'))
        android_jar = sorted((sdk / 'platforms').glob('*/android.jar'))[-1]
        subprocess.run([signing.sdk_tool('aapt2'), 'link', '-I', str(android_jar), '-o', str(apk), '--manifest', str(manifest)], check=True, capture_output=True)
        with patch.dict(os.environ, self.credentials):
            subprocess.run([
                signing.sdk_tool('apksigner'), 'sign', '--ks', str(self.keystore),
                '--ks-key-alias', 'fixture', '--ks-pass', 'env:ANDROID_RELEASE_STORE_PASSWORD',
                '--key-pass', 'env:ANDROID_RELEASE_KEY_PASSWORD', str(apk),
            ], check=True, capture_output=True)
        return apk

    def test_apk_with_another_signer_is_rejected(self):
        apk = self.make_apk()
        with self.assertRaisesRegex(ValueError, 'certificate'):
            signing.verify_apk(apk)

    def test_verified_apk_requires_the_expected_package_and_git_version(self):
        with patch.object(signing, 'EXPECTED_CERT_SHA256', self.fingerprint):
            signing.verify_apk(self.make_apk())
            for arguments in ({'package': 'org.example.other'}, {'version_code': 1001}, {'version_name': '0.1.1+wrong'}):
                with self.subTest(arguments=arguments), self.assertRaisesRegex(ValueError, 'identity'):
                    signing.verify_apk(self.make_apk(**arguments))

    def test_tampered_apk_is_rejected(self):
        apk = self.make_apk()
        with open(apk, 'r+b') as output:
            output.write(b'broken')
        with patch.object(signing, 'EXPECTED_CERT_SHA256', self.fingerprint):
            with self.assertRaises(subprocess.CalledProcessError):
                signing.verify_apk(apk)

    def test_missing_credentials_fail_without_creating_keystore(self):
        with tempfile.TemporaryDirectory() as directory, patch.dict(os.environ, {}, clear=True):
            destination = Path(directory) / 'release.keystore'
            with self.assertRaisesRegex(ValueError, 'Missing release signing credentials'):
                signing.prepare(destination)
            self.assertFalse(destination.exists())


if __name__ == '__main__':
    unittest.main()
