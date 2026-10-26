"""Verify a built or extracted package's launcher, JVM, checksummed mpv renderer and Rust bytes."""
from pathlib import Path
import argparse,hashlib,json,os,platform,subprocess,tempfile,time,zipfile

root=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser();parser.add_argument('--image',type=Path);parser.add_argument('--receipt',type=Path);parser.add_argument('--allow-candidate',action='store_true');args=parser.parse_args()
image=(args.image or root/'composeApp/build/compose/binaries/main/app').resolve()
launchers=[p for p in image.rglob('*') if p.is_file() and p.name in ('VIPTV-Experiment','VIPTV-Experiment.exe') and p.parent.name in ('bin','MacOS','VIPTV-Experiment')]
if len(launchers)!=1:raise SystemExit(f'Expected one packaged launcher, found{len(launchers)}')
launcher=launchers[0];arch='aarch64' if platform.machine().lower() in ['aarch64','arm64'] else 'x86-64';system=platform.system();prefix,library={'Linux':('linux','libviptv_core.so'),'Darwin':('darwin','libviptv_core.dylib'),'Windows':('win32','viptv_core.dll')}[system];resource=prefix+'-'+arch+'/'+library
sha=lambda b:hashlib.sha256(b).hexdigest();expected=root/'composeApp/build/nativeResources'/resource;expected_hash=sha(expected.read_bytes());native=[];jar_hashes={}
mpv_prefix='mpv-runtime/'+prefix+'-'+arch+'/'
mpv_expected=json.loads((root/'composeApp/build/mpvResources'/mpv_prefix/'manifest.json').read_text());mpv_seen=[]
torrent_prefix='torrent-runtime/'+prefix+'-'+arch+'/'
torrent_name='torrent-worker.exe' if system=='Windows' else 'torrent-worker'
torrent_input=root/'composeApp/build/torrentResources'/torrent_prefix
torrent_expected=json.loads((torrent_input/'worker.json').read_text());torrent_seen=[]
assert not torrent_expected['qualification_only'] or args.allow_candidate,'Qualification-only worker cannot pass release package verification'
assert args.allow_candidate or torrent_expected['revision']==(root/'TORRENT_RUNTIME_REF').read_text().strip(),'Torrent worker revision differs from pin'
for p in image.rglob('*.jar'):
 h=sha(p.read_bytes());jar_hashes[p.name]=h
 assert 'kiteplayer' not in p.name.lower() and 'kiteffmpeg' not in p.name.lower(),'Retired player dependency in package'
 with zipfile.ZipFile(p) as z:
  assert not any(n.startswith('io/github/yuroyami/kiteplayer/') for n in z.namelist()),'Retired player classes in package'
  if mpv_prefix+'manifest.json' in z.namelist():
   manifest=json.loads(z.read(mpv_prefix+'manifest.json'));assert manifest==mpv_expected
   assert manifest['library'] in manifest['files'] and manifest['abi_major']==2
   for name,digest in manifest['files'].items():assert sha(z.read(mpv_prefix+name))==digest,'Packaged mpv native bytes differ from manifest'
   mpv_seen.append(manifest)
  if resource in z.namelist():native.append({'jar':str(p.relative_to(image)),'jar_sha256':h,'sha256':sha(z.read(resource))})
  if torrent_prefix+'worker.json' in z.namelist():
   manifest=json.loads(z.read(torrent_prefix+'worker.json'))
   assert manifest==torrent_expected and sha(z.read(torrent_prefix+torrent_name))==manifest['sha256'],'Packaged torrent worker differs from build input'
   torrent_seen.append(manifest)
assert len(native)==1 and native[0]['sha256']==expected_hash,'Packaged Rust resource differs from current platform packaging input'
assert len(mpv_seen)==1,'Expected exactly one checksummed mpv runtime'
assert len(torrent_seen)==1,'Expected exactly one packaged torrent worker'
releases=[p for p in image.rglob('release') if 'runtime' in p.parts];assert len(releases)==1
runtime={line.split('=',1)[0]:line.split('=',1)[1].strip('"') for line in releases[0].read_text().splitlines() if '=' in line};assert runtime.get('JAVA_VERSION')
with tempfile.TemporaryDirectory(prefix='viptv-packaged-smoke-') as temporary:
 result=Path(temporary)/'result.txt';env=os.environ.copy();env.update({'VIPTV_ENGINE_SMOKE_TEST':'1','VIPTV_ENGINE_SMOKE_RESULT':str(result)})
 start=time.monotonic();process=subprocess.run([str(launcher)],env=env,capture_output=True,text=True,timeout=60)
 if process.returncode or any(m in process.stderr.lower() for m in ['pure virtual','fatal error','terminate called']):print(process.stdout);print(process.stderr);raise SystemExit('Packaged launcher/native shutdown failed')
 assert result.is_file() and result.read_text().strip()=='verified-local-engine','Packaged SQLite/profile diagnostic did not complete'
 torrent_result=Path(temporary)/'torrent-result.txt'
 torrent_env=os.environ.copy();torrent_env.update(VIPTV_TORRENT_SMOKE_TEST=str(torrent_result))
 torrent_process=subprocess.run([str(launcher)],env=torrent_env,capture_output=True,text=True,timeout=30)
 assert torrent_process.returncode==0 and torrent_result.is_file() and torrent_result.read_text().strip()=='verified-torrent-worker','Packaged torrent control/cache lifecycle failed'
 receipt={'image':str(image),'launcher_sha256':sha(launcher.read_bytes()),'bundled_java_version':runtime['JAVA_VERSION'],'bundled_java_modules':runtime.get('MODULES'),'native_resource':native[0],'current_native_input_sha256':expected_hash,'mpv_runtime':mpv_seen[0],'launcher_jvm_sqlite_smoke_passed':True,'smoke_seconds':round(time.monotonic()-start,3),'physical_device_or_user_vault_access':False,'scope':'Executed current host platform only; does not qualify unavailable Windows/macOS runners or package-manager installation.'}
 mpv_result=Path(temporary)/'mpv-result.txt'
 mpv_process=subprocess.run([str(launcher)],env={**os.environ,'VIPTV_MPV_SMOKE_TEST':str(mpv_result)},capture_output=True,text=True,timeout=45)
 assert mpv_process.returncode==0 and mpv_result.is_file() and mpv_result.read_text()=='verified-mpv-runtime','Packaged mpv load/render/shutdown failed'
 receipt['mpv_runtime_lifecycle_passed']=True
 receipt['torrent_worker']=torrent_seen[0];receipt['torrent_worker_lifecycle_passed']=True
 if args.receipt:args.receipt.parent.mkdir(parents=True,exist_ok=True);args.receipt.write_text(json.dumps(receipt,indent=2)+'\n')
print('Packaged launcher, bundled JVM, checksummed mpv renderer and exact Rust/SQLite engine verified')
