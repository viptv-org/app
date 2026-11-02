"""Bundle a native libmpv runtime; hash every byte before it enters JVM resources.

Use VIPTV_MPV_RUNTIME_DIR for an audited, preassembled runtime (manifest.json plus
flat native files). Linux/macOS can assemble from the installed development
library; Windows requires a flat runtime including its dependent DLLs. Builds
record exact hashes and host ABI; qualification applies only to that host.
"""
from pathlib import Path
import argparse, hashlib, json, os, platform, re, shutil, subprocess, tempfile

from macos_signing import sign_like_packaging

ROOT = Path(__file__).resolve().parents[1]

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def build():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--runtime', type=Path, default=os.getenv('VIPTV_MPV_RUNTIME_DIR'))
    args = parser.parse_args()
    system = platform.system()
    name = {'Linux': 'linux', 'Darwin': 'darwin', 'Windows': 'win32'}[system]
    arch = 'aarch64' if platform.machine().lower() in ('arm64', 'aarch64') else 'x86-64'
    target = ROOT / 'composeApp/build/mpvResources/mpv-runtime' / (name + '-' + arch)
    with tempfile.TemporaryDirectory(prefix='viptv-mpv-build-') as temporary:
        stage = Path(temporary)
        if args.runtime:
            source = Path(args.runtime).resolve()
            manifest = json.loads((source / 'manifest.json').read_text())
            assert manifest['platform'] == name + '-' + arch
            assert manifest['abi_major'] == 2
            assert manifest['library'] in manifest['files']
            for filename, digest in manifest['files'].items():
                assert Path(filename).name == filename and filename not in ('.', '..')
                file = source / filename
                assert sha(file) == digest, f'Native mpv checksum mismatch: {filename}'
                shutil.copy2(file, stage / filename)
        elif system == 'Linux':
            import ctypes.util
            soname = ctypes.util.find_library('mpv')
            assert soname, 'Install libmpv development/runtime packages, or set VIPTV_MPV_RUNTIME_DIR'
            # Resolve using the loader cache; do not assume /usr/lib vs multiarch paths.
            cache = subprocess.check_output(['ldconfig', '-p'], text=True)
            match = re.search(r'\s' + re.escape(soname) + r'\s.*=>\s+(\S+)', cache)
            library = Path(match.group(1)) if match else Path('/usr/lib') / soname
            assert library.is_file()
            listing = subprocess.check_output(['ldd', str(library)], text=True)
            assert 'not found' not in listing
            # The host supplies libc and its graphics/driver dispatch ABI. Never ship a GPU driver.
            host = re.compile(r'^(ld-linux|lib(c|m|pthread|dl|rt|resolv|util|nss_[^.]+)\.so|lib(GL|EGL|GLX|GLES|OpenGL|GLdispatch|vulkan|drm|gbm|wayland)[^/]*\.so)')
            files = {library.name: library}
            for line in listing.splitlines():
                match = re.search(r'(?:=>\s+)?(/\S+)\s+\(', line)
                if match:
                    file = Path(match.group(1))
                    if not host.match(file.name): files[file.name] = file
            for filename, file in files.items():
                shutil.copy2(file, stage / filename)
                output = stage / filename
                for needed in subprocess.check_output(['patchelf', '--print-needed', str(output)], text=True).splitlines():
                    if needed.startswith('/') and Path(needed).name in files:
                        subprocess.run(['patchelf', '--replace-needed', needed, Path(needed).name, str(output)], check=True)
                subprocess.run(['patchelf', '--set-rpath', '$ORIGIN', str(output)], check=True)
            manifest = {'library': library.name, 'abi_major': 2, 'platform': name + '-' + arch,
                        'origin': 'installed libmpv with private dependency closure; host libc and graphics drivers',
                        'upstream': 'https://github.com/mpv-player/mpv',
                        'source_versions': subprocess.check_output(['mpv', '--version'], text=True).splitlines(),
                        'files': {file.name: sha(file) for file in sorted(stage.iterdir())}}
        elif system == 'Darwin':
            prefix = Path(subprocess.check_output(['brew', '--prefix'], text=True).strip())
            library = prefix / 'lib/libmpv.2.dylib'
            assert library.is_file(), 'Install mpv using Homebrew or set VIPTV_MPV_RUNTIME_DIR'
            ffmpeg = (prefix / 'opt/ffmpeg').resolve()
            ffmpeg_full = (prefix / 'opt/ffmpeg-full').resolve()
            assert ffmpeg_full.is_dir(), 'Install ffmpeg-full using Homebrew; it is the FFmpeg build with zimg (zscale)'
            assert ffmpeg_full.name == ffmpeg.name, f'ffmpeg {ffmpeg.name} and ffmpeg-full {ffmpeg_full.name} must be the same release'
            files, links, pending = {}, {}, [library]
            while pending:
                file = pending.pop()
                if file.name in files:
                    assert files[file.name].resolve() == file.resolve(), 'Conflicting native basenames'
                    continue
                files[file.name] = file
                dependencies = [line.strip().split(' (', 1)[0] for line in subprocess.check_output(['otool', '-L', str(file)], text=True).splitlines()[1:]]
                links[file.name] = []
                for dependency in dependencies:
                    if dependency.startswith(('/System/', '/usr/lib/')): continue
                    dep = Path(dependency.replace('@loader_path', str(file.parent)))
                    if dependency.startswith('@rpath/'):
                        dep = prefix / 'lib' / Path(dependency).name
                    if dep.parent.resolve() == ffmpeg / 'lib':
                        dep = ffmpeg_full / 'lib' / dep.name
                    assert dep.is_file(), f'Unresolved mpv dependency: {dependency}'
                    if dep.resolve() == file.resolve(): continue
                    links[file.name].append((dependency, dep.name))
                    pending.append(dep)
            for filename, file in files.items():
                output = stage / filename
                shutil.copy2(file, output); output.chmod(0o755)
                subprocess.run(['install_name_tool', '-id', '@loader_path/' + filename, str(output)], check=True)
                for old, new in links[filename]:
                    subprocess.run(['install_name_tool', '-change', old, '@loader_path/' + new, str(output)], check=True)
                sign_like_packaging(output)
            manifest = {'library': library.name, 'abi_major': 2, 'platform': name + '-' + arch,
                        'origin': 'Homebrew mpv with ffmpeg-full FFmpeg libraries and private dependency closure; rewritten loader paths and ad-hoc signatures',
                        'source_versions': subprocess.check_output(['mpv', '--version'], text=True).splitlines() + ['ffmpeg-full ' + ffmpeg_full.name],
                        'upstream': 'https://github.com/mpv-player/mpv',
                        'files': {file.name: sha(file) for file in sorted(stage.iterdir())}}
        elif system == 'Windows':
            prefix = Path(os.getenv('VIPTV_MPV_PREFIX', 'C:/msys64/ucrt64'))
            binary = prefix / 'bin'
            library = binary / 'libmpv-2.dll'
            assert library.is_file(), 'Install MSYS2 UCRT64 mpv or set VIPTV_MPV_RUNTIME_DIR'
            objdump = binary / 'objdump.exe'
            available = {p.name.lower(): p for p in binary.glob('*.dll')}
            files, pending = {}, [library]
            while pending:
                file = pending.pop()
                if file.name.lower() in files: continue
                files[file.name.lower()] = file
                listing = subprocess.check_output([str(objdump), '-p', str(file)], text=True)
                for dependency in re.findall(r'DLL Name:\s*(\S+)', listing):
                    key = dependency.lower()
                    if key in available: pending.append(available[key])
                    elif not key.startswith(('api-ms-', 'ext-ms-')):
                        assert (Path(os.environ['WINDIR']) / 'System32' / dependency).is_file(), f'Unresolved DLL: {dependency}'
            for file in files.values(): shutil.copy2(file, stage / file.name)
            manifest = {'library': library.name, 'abi_major': 2, 'platform': name + '-' + arch,
                        'origin': 'MSYS2 UCRT64 mpv with private DLL dependency closure',
                        'source_versions': subprocess.check_output([str(binary / 'mpv.exe'), '--version'], text=True).splitlines(),
                        'upstream': 'https://github.com/mpv-player/mpv',
                        'files': {file.name: sha(file) for file in sorted(stage.iterdir())}}
        else:
            raise SystemExit('Unsupported desktop platform')
        if not args.runtime:
            for license in (ROOT / 'native/mpv').glob('LICENSE*'): shutil.copy2(license, stage / license.name)
            shutil.copy2(ROOT / 'native/mpv/NOTICE.txt', stage / 'NOTICE.txt')
            manifest['files'] = {file.name: sha(file) for file in sorted(stage.iterdir())}
        (stage / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
        if target.exists(): shutil.rmtree(target)
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copytree(stage, target)
        print(f'Bundled {manifest["library"]}: {len(manifest["files"])} checksummed files for {manifest["platform"]}')

if __name__ == '__main__': build()
