"""Sign a macOS native library exactly as Compose packaging does, so hashes taken here survive packaging."""
import platform
import subprocess

def sign_like_packaging(path):
    subprocess.run(['codesign', '--remove-signature', str(path)], check=True)
    if platform.machine().lower() in ('arm64', 'aarch64'):
        subprocess.run(['codesign', '--sign', '-', '--options', 'runtime', '--force', str(path)], check=True)
