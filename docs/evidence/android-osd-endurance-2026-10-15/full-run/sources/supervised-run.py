import os, subprocess, json, socket, time, re, signal, hashlib
from pathlib import Path
from datetime import datetime, timezone

root=Path('/tmp/viptv-osd-endurance-qa/supervised-20261015T2017Z')
root.mkdir(exist_ok=False)
app=Path('/mnt/ALPH/code/viptv-org/app')
apk=app/'artifacts/android-osd-endurance-2026-10-15/5abd366c036b/viptv-osd-endurance-debug.apk'
assert hashlib.sha256(apk.read_bytes()).hexdigest()=='5abd366c036b58260e4ce3dd2d026a0c717e45950341747593e59f1be88f353a'
avds=Path('/tmp/viptv-16k-qa/avds');avd=Path('/home/vynxc/.cache/viptv-qa/android-16k/device.avd')
assert f'path={avd}' in (avds/'viptv-16k-isolated.ini').read_text()
assert 'system-images/android-36/google-tv-ps16k/x86_64/' in (avd/'config.ini').read_text()
for port in [5586,5587,18586]:
 with socket.socket() as s:assert s.connect_ex(('127.0.0.1',port))!=0
adb=['/home/vynxc/Android/Sdk/platform-tools/adb','-s','emulator-5586']
children=[];handles=[];result=1
def stamp():return datetime.now(timezone.utc).isoformat()
def save(name,data):
 p=root/name;t=p.with_suffix('.tmp');t.write_text(json.dumps(data,indent=2));t.replace(p)
def start(name,cmd,env=None):
 f=(root/(name+'.log')).open('wb');handles.append(f)
 p=subprocess.Popen(cmd,env=env,stdout=f,stderr=subprocess.STDOUT);children.append((name,p))
 save(name+'-process.json',{'pid':p.pid,'started_utc':stamp()});return p
def guard():
 assert subprocess.check_output(adb+['emu','avd','name']).decode().splitlines()[0]=='viptv-16k-isolated'
def run(*args):
 guard();return subprocess.check_output(adb+list(args),stderr=subprocess.STDOUT).decode()
def interrupted(signum,frame):raise RuntimeError('Supervisor interrupted by signal '+str(signum))
signal.signal(signal.SIGTERM,interrupted)
save('supervisor-start.json',{'pid':os.getpid(),'started_utc':stamp(),'unit':'viptv-osd-endurance-20261015T2017Z.service','interrupted_run':'../long-run','interrupted_run_counts_as_endurance':False})
try:
 emu=start('emulator',['/home/vynxc/Android/Sdk/emulator/emulator','-avd','viptv-16k-isolated','-port','5586','-memory','2048','-cores','2','-no-window','-no-audio','-no-boot-anim','-no-snapshot','-gpu','swiftshader'],dict(os.environ,ANDROID_AVD_HOME=str(avds)))
 for _ in range(60):
  assert emu.poll() is None,'Owned emulator exited during boot'
  try:
   if subprocess.check_output(adb+['shell','getprop','sys.boot_completed'],stderr=subprocess.DEVNULL,timeout=5).decode().strip()=='1':break
  except subprocess.SubprocessError:pass
  time.sleep(2)
 else:raise AssertionError('Owned guest boot timed out')
 assert run('shell','getprop','ro.build.version.sdk').strip()=='36'
 assert run('shell','getconf','PAGE_SIZE').strip()=='16384'
 mode=run('shell','dumpsys','uimode');assert int(re.search(r'mCurUiMode=(0x[0-9a-fA-F]+)',mode).group(1),16)&15==4
 for k,v in [('bionic.linker.16kb.app_compat.enabled','false'),('pm.16kb.app_compat.disabled','true')]:
  if run('shell','getprop',k).strip()!=v:run('shell','setprop',k,v)
  assert run('shell','getprop',k).strip()==v
 save('guest.json',{'sdk':36,'page_size':16384,'uimode':mode,'linker_compat':'false','package_compat_disabled':'true','fingerprint':run('shell','getprop','ro.build.fingerprint').strip()})
 fixture=start('fixture',['python3',str(app/'tools/qa_lifecycle_server.py'),'--root','/tmp/viptv-16k-qa/https','--media','/home/vynxc/.cache/viptv-qa/runtime-40min-2026-10-15/media','--port','18586','--direct','--rolling'])
 for _ in range(50):
  assert fixture.poll() is None
  with socket.socket() as s:
   if s.connect_ex(('127.0.0.1',18586))==0:break
  time.sleep(.1)
 driver=start('observer',['python3',str(app/'tools/qa_endurance_run.py'),'--apk',str(apk),'--root',str(root/'run'),'--ca','/tmp/viptv-16k-qa/https/fixture.crt','--origin','https://127.0.0.1:18586','--sustain-ms','2460000'])
 while driver.poll() is None:
  save('supervisor-live.json',{'utc':stamp(),'supervisor_pid':os.getpid(),'children':{name:{'pid':p.pid,'status':p.poll()} for name,p in children},'clock_monotonic':time.monotonic()})
  assert emu.poll() is None,'Owned emulator exited'
  assert fixture.poll() is None,'Owned fixture exited'
  time.sleep(10)
 result=driver.returncode
 save('supervisor-result.json',{'observer_exit':result,'finished_utc':stamp()})
except BaseException as e:
 save('supervisor-error.json',{'error':str(e),'utc':stamp()})
 raise
finally:
 signal.signal(signal.SIGTERM,signal.SIG_IGN)
 try:
  guard();run('reverse','--remove','tcp:18586');run('emu','kill')
 except Exception:pass
 for name,p in reversed(children):
  if p.poll() is None:p.terminate()
 for name,p in reversed(children):
  try:p.wait(timeout=15)
  except subprocess.TimeoutExpired:p.kill();p.wait(timeout=5)
 save('cleanup.json',{'utc':stamp(),'children':{name:{'pid':p.pid,'returncode':p.poll(),'proc_present':Path('/proc',str(p.pid)).exists()} for name,p in children}})
 for f in handles:f.close()
raise SystemExit(result)
