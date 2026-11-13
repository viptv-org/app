"""Fingerprint, build and verify relocatable Android native packaging inputs."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import struct
import subprocess
import sys
import time

ABIS = {'armeabi-v7a': (1, 40), 'arm64-v8a': (2, 183), 'x86_64': (2, 62)}
OUTPUTS = {'engine': ['rustJni', 'subtitleLicenses'], 'torrent': ['torrentRuntime']}


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def fingerprint(root, component, abis, toolchains):
    paths = [root / 'tools/android_native.py', root / 'tools/android-toolchains.json']
    if component == 'engine':
        paths += [root / 'tools/build_engine.py', root / 'tools/macos_signing.py']
        paths += list((root / '.cargo').rglob('*')) if (root / '.cargo').exists() else []
        paths += [p for p in (root / 'native/core').rglob('*')
                  if p.is_file() and not set(p.relative_to(root / 'native/core').parts) & {'.git', 'target', '__pycache__'}]
    else:
        paths += [root / name for name in ['TORRENT_RUNTIME_REF', 'tools/build_torrent_runtime.py',
                  'tools/playback_gateway.py', 'androidApp/src/main/java/org/playbackgateway/runtime/NativeRuntime.java']]
    files = {str(p.relative_to(root)): digest(p) for p in sorted(paths) if p.is_file()}
    prefixes = ('CARGO_', 'RUST', 'GO', 'CGO_', 'CC', 'CXX', 'CFLAGS', 'CPPFLAGS',
                'AR', 'LD', 'LDFLAGS', 'TARGET_', 'HOST_', 'BINDGEN_', 'CRATE_CC_',
                'RANLIB', 'PKG_CONFIG', 'RING_', 'OPENSSL_', 'LIBCLANG_', 'CLANG_', 'CMAKE_')
    locations = {'CARGO_HOME', 'CARGO_TARGET_DIR', 'RUSTUP_HOME', 'GOPATH', 'GOCACHE', 'GOMODCACHE'}
    environment = {key: value for key, value in os.environ.items()
                   if key not in locations and key.startswith(prefixes)}
    payload = {'schema': 1, 'component': component, 'abis': abis, 'files': files,
               'toolchains': toolchains, 'environment': environment}
    return hashlib.sha256(json.dumps(payload, sort_keys=True).encode()).hexdigest()


def native_environment(component):
    env = os.environ.copy()
    if component == 'engine' and env.get('VIPTV_CARGO_NDK_DIR'):
        env['PATH'] = env['VIPTV_CARGO_NDK_DIR'] + os.pathsep + env['PATH']
    if component == 'torrent' and not env.get('GO'):
        executable = shutil.which('go')
        if not executable:
            raise SystemExit('Go is missing')
        version = subprocess.check_output([executable, 'env', 'GOVERSION'], text=True, env=env).strip()
        if [int(part) for part in version.removeprefix('go').split('.')[:2]] < [1, 25]:
            pins = json.loads((Path(__file__).parent / 'android-toolchains.json').read_text())
            goroot = subprocess.check_output([executable, 'env', 'GOROOT'], text=True,
                                             env=dict(env, GOTOOLCHAIN='go' + pins['go'])).strip()
            executable = str(Path(goroot) / 'bin/go')
        env['GO'] = executable
    return env


def toolchains(root, component):
    env = native_environment(component)
    pins = json.loads((root / 'tools/android-toolchains.json').read_text())
    sdk = os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME')
    if not sdk and (root / 'local.properties').exists():
        sdk = next((line.split('=', 1)[1] for line in (root / 'local.properties').read_text().splitlines()
                    if line.startswith('sdk.dir=')), None)
    ndk = Path(os.environ.get('ANDROID_NDK_HOME') or str(Path(sdk or '') / 'ndk' / pins['ndk']))
    actual = {'pins': pins, 'ndk': (ndk / 'source.properties').read_text()}
    if component == 'engine':
        cargo_home = Path(env.get('CARGO_HOME', str(Path.home() / '.cargo')))
        actual['cargo-config'] = {name: digest(cargo_home / name) for name in ['config', 'config.toml']
                                  if (cargo_home / name).is_file()}
        actual['rust'] = subprocess.check_output([env.get('RUSTC', 'rustc'), '-vV'], text=True, env=env).strip()
        actual['cargo'] = subprocess.check_output(['cargo', '--version'], text=True, env=env).strip()
        actual['cargo-ndk'] = subprocess.check_output(['cargo', 'ndk', '--version'], text=True, env=env).strip()
    else:
        actual['go'] = subprocess.check_output([env['GO'], 'version'], text=True, env=env).strip()
    return actual


def output_root(root):
    return root / 'androidApp/build/generated'


def manifest_path(root, component):
    return output_root(root) / OUTPUTS[component][0] / 'native-cache.json'


def inventory(root, component, abis):
    base = output_root(root)
    paths = [p for directory in OUTPUTS[component] for p in (base / directory).rglob('*')
             if p.is_file() and p.name != 'native-cache.json']
    required = []
    for abi in abis:
        libraries = ['rustJni/' + abi + '/libviptv_core.so'] if component == 'engine' else [
            'torrentRuntime/jniLibs/' + abi + '/' + library
            for library in ['libtorrent_runtime.so', 'libtorrent_runtime_jni.so']]
        for name in libraries:
            path = base / name
            header = path.read_bytes()[:64]
            elf_class, machine = ABIS[abi]
            if len(header) < 64 or header[:4] != b'\x7fELF' or header[4:6] != bytes([elf_class, 1]) or struct.unpack('<H', header[18:20])[0] != machine:
                raise ValueError('Invalid Android ELF: ' + name)
            required.append(name)
    if component == 'engine':
        required += ['subtitleLicenses/licenses/' + name for name in
                     ['webrtc-vad.LICENSE', 'webrtc-vad-source.json', 'webrtc-vad-48k.patch']]
    else:
        required += ['torrentRuntime/assets/torrent-runtime/' + name for name in
                     ['LICENSE', 'PROVENANCE.md', 'go.mod', 'go.sum', 'NDK-TOOLCHAIN-NOTICE.txt']]
        if not any('/licenses/' in str(p) for p in paths):
            raise ValueError('Torrent dependency licenses are missing')
    for name in required:
        if not (base / name).is_file() or (base / name).stat().st_size == 0:
            raise ValueError('Native packaging input is missing: ' + name)
    return {str(p.relative_to(base)): digest(p) for p in sorted(paths)}


def seal(root, component, abis, key):
    files = inventory(root, component, abis)
    manifest_path(root, component).write_text(json.dumps({
        'schema': 1, 'component': component, 'abis': abis, 'fingerprint': key, 'files': files,
    }, sort_keys=True, indent=2) + '\n')


def validate(root, component, abis, key):
    try:
        manifest = json.loads(manifest_path(root, component).read_text())
        return manifest == {'schema': 1, 'component': component, 'abis': abis,
                            'fingerprint': key, 'files': inventory(root, component, abis)}
    except (OSError, ValueError, KeyError, TypeError):
        return False


def build(root, component, abis, key):
    if validate(root, component, abis, key):
        print(component + ': validated native cache hit', flush=True)
        return
    print(component + ': native cache miss or invalid output; rebuilding', flush=True)
    for directory in OUTPUTS[component]:
        shutil.rmtree(output_root(root) / directory, ignore_errors=True)
    script = 'build_engine.py' if component == 'engine' else 'build_torrent_runtime.py'
    command = [sys.executable, str(root / 'tools' / script)]
    if component == 'engine':
        command += ['android']
    command += ['--abis', ','.join(abis)]
    subprocess.run(command, cwd=root, env=native_environment(component), check=True)
    seal(root, component, abis, key)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['fingerprint', 'build', 'validate', 'seal'])
    parser.add_argument('component', choices=OUTPUTS)
    parser.add_argument('--abis', default=','.join(ABIS))
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    abis = [abi for abi in ABIS if abi in args.abis.split(',')]
    if not abis or set(args.abis.split(',')) != set(abis):
        parser.error('unsupported or empty ABI list')
    key = fingerprint(root, args.component, abis, toolchains(root, args.component))
    if args.action == 'fingerprint':
        print(key)
        return
    if args.action == 'validate':
        if not validate(root, args.component, abis, key):
            raise SystemExit(args.component + ': invalid native cache')
    elif args.action == 'seal':
        seal(root, args.component, abis, key)
    else:
        started = time.monotonic()
        build(root, args.component, abis, key)
        print(f'{args.component}: {time.monotonic() - started:.1f}s', flush=True)


if __name__ == '__main__':
    main()
