import os,subprocess,json,socket,time
from pathlib import Path
root=Path('/tmp/viptv-api26-qa')
assert 'target=android-26' in (root/'avds/viptv-api26-isolated.ini').read_text()
assert 'AndroidVersion.ApiLevel=26' in Path('/home/vynxc/Android/Sdk/system-images/android-26/google_apis/x86_64/source.properties').read_text()
for port in [5592,5593,18592]:
 with socket.socket() as s:assert s.connect_ex(('127.0.0.1',port))!=0,f'Owned target port {port} occupied'
env=dict(os.environ,ANDROID_AVD_HOME=str(root/'avds'))
cmd=['/home/vynxc/Android/Sdk/emulator/emulator','-avd','viptv-api26-isolated','-port','5592','-memory','2048','-cores','2','-no-window','-no-audio','-no-boot-anim','-no-snapshot','-gpu','swiftshader']
with (root/'emulator-current.log').open('wb') as f:p=subprocess.Popen(cmd,env=env,stdout=f,stderr=subprocess.STDOUT,start_new_session=True)
(root/'emulator-process.json').write_text(json.dumps({'pid':p.pid,'serial':'emulator-5592','avd':'viptv-api26-isolated'},indent=2))
for _ in range(30):
 r=subprocess.run(['/home/vynxc/Android/Sdk/platform-tools/adb','-s','emulator-5592','shell','getprop','sys.boot_completed'],capture_output=True,text=True,timeout=5)
 if r.stdout.strip()=='1':break
 time.sleep(2)
else:raise AssertionError('Owned API26 boot timed out')
import qa;qa.guard()
mode=qa.run(['shell','dumpsys','uimode'],capture_output=True).stdout.decode();assert 'mCurUiMode=0x11' in mode
cmd=['python3','/mnt/ALPH/code/viptv-org/app/tools/qa_lifecycle_server.py','--root','/tmp/viptv-16k-qa/https','--media','/home/vynxc/.cache/viptv-qa/runtime-16k/media','--port','18592','--direct','--rolling']
with (root/'server-current.log').open('wb') as f:s=subprocess.Popen(cmd,stdout=f,stderr=subprocess.STDOUT,start_new_session=True)
(root/'server-process.json').write_text(json.dumps({'pid':s.pid,'port':18592},indent=2));print(json.dumps({'owned_emulator_pid':p.pid,'owned_fixture_pid':s.pid,'sdk':26,'ui_mode':'normal0x11'}),flush=True)
