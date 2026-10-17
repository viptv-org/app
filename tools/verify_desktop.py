"""Verify a built or extracted package's launcher, JVM, pinned renderer and Rust bytes."""
from pathlib import Path
import argparse,hashlib,json,os,platform,subprocess,tempfile,time,zipfile

root=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser();parser.add_argument('--image',type=Path);parser.add_argument('--receipt',type=Path);args=parser.parse_args()
image=(args.image or root/'composeApp/build/compose/binaries/main/app').resolve()
launchers=[p for p in image.rglob('*') if p.is_file() and p.name in ('VIPTV-Experiment','VIPTV-Experiment.exe') and p.parent.name in ('bin','MacOS','VIPTV-Experiment')]
if len(launchers)!=1:raise SystemExit(f'Expected one packaged launcher, found{len(launchers)}')
launcher=launchers[0];arch='aarch64' if platform.machine().lower() in ['aarch64','arm64'] else 'x86-64';system=platform.system();prefix,library={'Linux':('linux','libviptv_core.so'),'Darwin':('darwin','libviptv_core.dylib'),'Windows':('win32','viptv_core.dll')}[system];resource=prefix+'-'+arch+'/'+library
sha=lambda b:hashlib.sha256(b).hexdigest();expected=root/'composeApp/build/nativeResources'/resource;expected_hash=sha(expected.read_bytes());native=[];jar_hashes={}
pins=json.loads((root/'native/kiteplayer/ARTIFACTS.json').read_text())['sha256'];required={Path(p).name:h for p,h in pins.items() if '-jvm/' in p and p.endswith('.jar')};seen={}
for p in image.rglob('*.jar'):
 h=sha(p.read_bytes());jar_hashes[p.name]=h
 for name,wanted in required.items():
  if p.name.startswith(name[:-4]) and h==wanted:seen[name]=h
 with zipfile.ZipFile(p) as z:
  if resource in z.namelist():native.append({'jar':str(p.relative_to(image)),'jar_sha256':h,'sha256':sha(z.read(resource))})
assert len(native)==1 and native[0]['sha256']==expected_hash,'Packaged Rust resource differs from current platform packaging input'
assert len(seen)==len(required),f'Pinned renderer JVM jars missing/mismatched:{set(required)-set(seen)}'
releases=[p for p in image.rglob('release') if 'runtime' in p.parts];assert len(releases)==1
runtime={line.split('=',1)[0]:line.split('=',1)[1].strip('"') for line in releases[0].read_text().splitlines() if '=' in line};assert runtime.get('JAVA_VERSION')
with tempfile.TemporaryDirectory(prefix='viptv-packaged-smoke-') as temporary:
 result=Path(temporary)/'result.txt';env=os.environ.copy();env.update({'VIPTV_ENGINE_SMOKE_TEST':'1','VIPTV_ENGINE_SMOKE_RESULT':str(result)})
 start=time.monotonic();process=subprocess.run([str(launcher)],env=env,capture_output=True,text=True,timeout=60)
 if process.returncode or any(m in process.stderr.lower() for m in ['pure virtual','fatal error','terminate called']):print(process.stdout);print(process.stderr);raise SystemExit('Packaged launcher/native shutdown failed')
 assert result.is_file() and result.read_text().strip()=='verified-local-engine','Packaged SQLite/profile diagnostic did not complete'
 receipt={'image':str(image),'launcher_sha256':sha(launcher.read_bytes()),'bundled_java_version':runtime['JAVA_VERSION'],'bundled_java_modules':runtime.get('MODULES'),'native_resource':native[0],'current_native_input_sha256':expected_hash,'pinned_renderer_jvm_jars':seen,'launcher_jvm_sqlite_smoke_passed':True,'smoke_seconds':round(time.monotonic()-start,3),'physical_device_or_user_vault_access':False,'scope':'Executed current host platform only; does not qualify unavailable Windows/macOS runners or package-manager installation.'}
 if args.receipt:args.receipt.parent.mkdir(parents=True,exist_ok=True);args.receipt.write_text(json.dumps(receipt,indent=2)+'\n')
print('Packaged launcher, bundled JVM, pinned renderer and exact Rust/SQLite engine verified')
