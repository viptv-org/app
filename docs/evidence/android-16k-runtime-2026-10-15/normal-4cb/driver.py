import subprocess,hashlib,json,time,re,xml.etree.ElementTree as E
from pathlib import Path
adb=['/home/vynxc/Android/Sdk/platform-tools/adb','-s','emulator-5586'];out=Path('/tmp/viptv-16k-qa/normal-4cb');out.mkdir(exist_ok=True)
def run(*x):
 assert subprocess.check_output(adb+['emu','avd','name']).decode().splitlines()[0]=='viptv-16k-isolated'
 assert subprocess.check_output(adb+['shell','getprop','ro.build.version.sdk']).decode().strip()=='36'
 assert subprocess.check_output(adb+['shell','getconf','PAGE_SIZE']).decode().strip()=='16384'
 return subprocess.check_output(adb+list(x),stderr=subprocess.STDOUT).decode()
for prop,value in [('bionic.linker.16kb.app_compat.enabled','false'),('pm.16kb.app_compat.disabled','true')]:
 if run('shell','getprop',prop).strip()!=value:run('shell','setprop',prop,value)
 assert run('shell','getprop',prop).strip()==value
p=Path('/mnt/ALPH/code/viptv-org/app/artifacts/android-release-2026-10-15/local-entry-4cb59ab8ea45/viptv-android-release-local.apk');sha=hashlib.sha256(p.read_bytes()).hexdigest();assert sha=='4cb59ab8ea453d8e20dfc5b731fe8b430bc1818c7a95e55a9c8d59bbed4f3193'
install=run('install','-r',str(p));run('shell','pm','clear','org.viptv.experiment');run('logcat','-c')
launch=run('shell','am','start','-W','-n','org.viptv.experiment/.MainActivity');(out/'launch.txt').write_text(launch)
def wait_labels(expected,file,forbid=()):
 until=time.monotonic()+35
 while time.monotonic()<until:
  run('shell','uiautomator','dump','/sdcard/normal-16k-6b.xml');s=run('shell','cat','/sdcard/normal-16k-6b.xml');tree=E.fromstring(s);labels=[n.get('text') for n in tree.iter('node') if n.get('text')]
  assert not any(e in labels for e in forbid),labels
  if all(e in labels for e in expected):(out/file).write_text(s);return tree,labels
  time.sleep(.2)
 raise AssertionError(labels)
def default_focused(tree):return any(n.get('focused')=='true' and any(c.get('text')=='Default' for c in n.iter('node')) for n in tree.iter('node'))
wait_labels(['Use without an account','Sign in'],'welcome.xml');run('shell','input','keyevent','KEYCODE_DPAD_RIGHT');run('shell','input','keyevent','KEYCODE_DPAD_CENTER')
tree,_=wait_labels(['Default','Who’s watching?'],'profile.xml');assert default_focused(tree);run('shell','input','keyevent','KEYCODE_DPAD_CENTER')
_,labels=wait_labels(['Home','Movies','Series','Anime','Calendar','My List'],'home.xml');(out/'home.png').write_bytes(subprocess.check_output(adb+['exec-out','screencap','-p']))
run('shell','am','force-stop','org.viptv.experiment');restart=run('shell','am','start','-W','-n','org.viptv.experiment/.MainActivity');(out/'restart.txt').write_text(restart)
tree,cold=wait_labels(['Default','Who’s watching?'],'cold-profile.xml',forbid=['Home','Use without an account','Sign in']);assert default_focused(tree);(out/'cold-profile.png').write_bytes(subprocess.check_output(adb+['exec-out','screencap','-p']))
run('shell','input','keyevent','KEYCODE_DPAD_CENTER');wait_labels(['Home','Movies','Series','Anime','Calendar','My List'],'cold-selected-home.xml')
errors=run('logcat','-d','-v','brief','AndroidRuntime:E','linker:E','*:S');(out/'runtime-errors.log').write_text(errors);assert 'Process: org.viptv.experiment' not in errors and 'UnsatisfiedLinkError' not in errors
r={'passed':True,'apk_sha256':sha,'variant':'normal release','avd':'viptv-16k-isolated','serial':'emulator-5586','sdk':36,'page_size':16384,'linker_compat':run('shell','getprop','bionic.linker.16kb.app_compat.enabled').strip(),'package_compat_disabled':run('shell','getprop','pm.16kb.app_compat.disabled').strip(),'fingerprint':run('shell','getprop','ro.build.fingerprint').strip(),'install':install,'launch':launch,'restart':restart,'profile_focus':'Default on fresh and cold profile screens','home_labels':labels,'cold_profile_labels':cold,'first_entry_mode_reset':'Owned fresh data via pm clear; welcome observed before Use without account','real_native_local_home':True,'cold_restore_profiles_before_home':True,'cold_restore_without_welcome':True,'unprotected_default_selected_explicitly':True,'scope':'latest normal first local entry and cold Profiles gate only; no repeat of unchanged debug extended playback'}
(out/'result.json').write_text(json.dumps(r,indent=2));print(json.dumps(r),flush=True)
