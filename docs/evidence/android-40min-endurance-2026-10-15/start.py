import os,subprocess,json,socket,time,re
from pathlib import Path
root=Path('/tmp/viptv-40min-qa');avds=Path('/tmp/viptv-16k-qa/avds');avd=Path('/home/vynxc/.cache/viptv-qa/android-16k/device.avd')
assert f'path={avd}' in (avds/'viptv-16k-isolated.ini').read_text();assert 'system-images/android-36/google-tv-ps16k/x86_64/' in (avd/'config.ini').read_text()
for port in [5586,5587,18586]:
 with socket.socket() as s:assert s.connect_ex(('127.0.0.1',port))!=0
cmd=['/home/vynxc/Android/Sdk/emulator/emulator','-avd','viptv-16k-isolated','-port','5586','-memory','2048','-cores','2','-no-window','-no-audio','-no-boot-anim','-no-snapshot','-gpu','swiftshader']
with (root/'emulator.log').open('wb') as f:p=subprocess.Popen(cmd,env=dict(os.environ,ANDROID_AVD_HOME=str(avds)),stdout=f,stderr=subprocess.STDOUT,start_new_session=True)
(root/'emulator-process.json').write_text(json.dumps({'pid':p.pid,'avd':'viptv-16k-isolated','serial':'emulator-5586'},indent=2))
adb=['/home/vynxc/Android/Sdk/platform-tools/adb','-s','emulator-5586']
for _ in range(30):
 r=subprocess.run(adb+['shell','getprop','sys.boot_completed'],capture_output=True,text=True,timeout=5)
 if r.stdout.strip()=='1':break
 time.sleep(2)
else:raise AssertionError('Owned guest boot timed out')
def run(*x):
 assert subprocess.check_output(adb+['emu','avd','name']).decode().splitlines()[0]=='viptv-16k-isolated'
 assert subprocess.check_output(adb+['shell','getprop','ro.build.version.sdk']).decode().strip()=='36'
 assert subprocess.check_output(adb+['shell','getconf','PAGE_SIZE']).decode().strip()=='16384'
 return subprocess.check_output(adb+list(x),stderr=subprocess.STDOUT).decode()
mode=run('shell','dumpsys','uimode');assert int(re.search(r'mCurUiMode=(0x[0-9a-fA-F]+)',mode).group(1),16)&15==4
for k,v in [('bionic.linker.16kb.app_compat.enabled','false'),('pm.16kb.app_compat.disabled','true')]:
 if run('shell','getprop',k).strip()!=v:run('shell','setprop',k,v)
 assert run('shell','getprop',k).strip()==v
cmd=['python3','/mnt/ALPH/code/viptv-org/app/tools/qa_lifecycle_server.py','--root','/tmp/viptv-16k-qa/https','--media','/home/vynxc/.cache/viptv-qa/runtime-40min-2026-10-15/media','--port','18586','--direct','--rolling']
with (root/'server.log').open('wb') as f:s=subprocess.Popen(cmd,stdout=f,stderr=subprocess.STDOUT,start_new_session=True)
(root/'server-process.json').write_text(json.dumps({'pid':s.pid,'port':18586},indent=2));(root/'guest.json').write_text(json.dumps({'sdk':36,'page_size':16384,'uimode':mode,'linker_compat':'false','package_compat_disabled':'true','fingerprint':run('shell','getprop','ro.build.fingerprint').strip()},indent=2));print(json.dumps({'guest_pid':p.pid,'fixture_pid':s.pid,'serial':'emulator-5586','port':18586}),flush=True)
