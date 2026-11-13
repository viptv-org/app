import argparse
import base64
import binascii
import hashlib
import os
import re
import shutil
import subprocess
import sys
from pathlib import Path

EXPECTED_CERT_SHA256 = 'FBD4A7625A283F84CA956DCE0F1E6EB8F6601AB305D087EBA57D083015662DB5'
CREDENTIALS = (
    'ANDROID_RELEASE_KEYSTORE_BASE64',
    'ANDROID_RELEASE_STORE_PASSWORD',
    'ANDROID_RELEASE_KEY_ALIAS',
    'ANDROID_RELEASE_KEY_PASSWORD',
)


def prepare(destination: Path):
    missing = [name for name in CREDENTIALS if not os.environ.get(name)]
    if missing:
        raise ValueError(f'Missing release signing credentials: {", ".join(missing)}')
    encoded = ''.join(os.environ['ANDROID_RELEASE_KEYSTORE_BASE64'].split())
    data = base64.b64decode(encoded, validate=True)
    descriptor = os.open(destination, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    try:
        with os.fdopen(descriptor, 'wb') as output:
            output.write(data)
        certificate = subprocess.run([
            'keytool', '-exportcert', '-keystore', str(destination),
            '-alias', os.environ['ANDROID_RELEASE_KEY_ALIAS'],
            '-storepass:env', 'ANDROID_RELEASE_STORE_PASSWORD',
        ], check=True, capture_output=True, stdin=subprocess.DEVNULL).stdout
        if hashlib.sha256(certificate).hexdigest().upper() != EXPECTED_CERT_SHA256:
            raise ValueError('Release signing certificate does not match the pinned release identity')
        subprocess.run([
            'keytool', '-certreq', '-keystore', str(destination),
            '-alias', os.environ['ANDROID_RELEASE_KEY_ALIAS'],
            '-storepass:env', 'ANDROID_RELEASE_STORE_PASSWORD',
            '-keypass:env', 'ANDROID_RELEASE_KEY_PASSWORD',
        ], check=True, capture_output=True, stdin=subprocess.DEVNULL)
    except BaseException:
        destination.unlink(missing_ok=True)
        raise


def sdk_tool(name: str) -> str:
    found = shutil.which(name)
    if found:
        return found
    for sdk in (os.environ.get('ANDROID_HOME'), os.environ.get('ANDROID_SDK_ROOT'), Path.home() / 'Android/Sdk'):
        if sdk:
            candidates = sorted((Path(sdk) / 'build-tools').glob(f'*/{name}'))
            if candidates:
                return str(candidates[-1])
    raise ValueError(f'{name} not found: set ANDROID_HOME or add Android build-tools to PATH')


def verify_apk(apk: Path):
    verification = subprocess.run([
        sdk_tool('apksigner'), 'verify', '--verbose', '--print-certs', str(apk),
    ], check=True, capture_output=True, text=True).stdout
    certificates = re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)$', verification, re.M)
    if [value.upper() for value in certificates] != [EXPECTED_CERT_SHA256]:
        raise ValueError('APK signing certificate does not match the pinned release identity')
    root = Path(__file__).resolve().parents[1]
    def git(*arguments):
        return subprocess.check_output(['git', *arguments], cwd=root, text=True).strip()
    if git('rev-parse', '--is-shallow-repository') != 'false':
        raise ValueError('APK version verification requires full git history')
    count = int(git('rev-list', '--count', 'HEAD'))
    expected = ('org.viptv.experiment', str(1000 + count), f'0.1.{count}+{git("rev-parse", "--short=7", "HEAD")}')
    badging = subprocess.run([sdk_tool('aapt2'), 'dump', 'badging', str(apk)], check=True, capture_output=True, text=True).stdout
    identity = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']*)'", badging, re.M)
    if not identity or identity.groups() != expected:
        raise ValueError(f'APK identity does not match checkout: expected {expected}')
    return expected


def main():
    parser = argparse.ArgumentParser(description='Prepare the stable Android release key or verify a release APK')
    commands = parser.add_subparsers(dest='command', required=True)
    commands.add_parser('prepare').add_argument('keystore', type=Path)
    commands.add_parser('verify').add_argument('apk', type=Path)
    arguments = parser.parse_args()
    try:
        if arguments.command == 'prepare':
            prepare(arguments.keystore)
            print(f'Verified release certificate SHA256 {EXPECTED_CERT_SHA256}')
        else:
            package, code, name = verify_apk(arguments.apk)
            print(f'Verified {package} versionCode={code} versionName={name} certificateSHA256={EXPECTED_CERT_SHA256}')
    except (ValueError, binascii.Error, OSError, subprocess.CalledProcessError) as error:
        if isinstance(error, subprocess.CalledProcessError):
            sys.exit('Android signing tool failed; check credentials, keystore and APK integrity')
        sys.exit(str(error))


if __name__ == '__main__':
    main()
