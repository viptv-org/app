import hashlib,json,re,time
from pathlib import Path
import qa
p=Path('/mnt/ALPH/code/viptv-org/app/artifacts/android-release-2026-10-15/local-entry-4cb59ab8ea45/viptv-android-release-local.apk');sha=hashlib.sha256(p.read_bytes()).hexdigest();assert sha=='4cb59ab8ea453d8e20dfc5b731fe8b430bc1818c7a95e55a9c8d59bbed4f3193'
out=qa.root/'normal-4cb';out.mkdir(exist_ok=True)
assert 'mCurUiMode=0x11' in qa.run(['shell','dumpsys','uimode'],capture_output=True).stdout.decode()
def wait(expected,file,forbid=()):
 until=time.monotonic()+35
 while time.monotonic()<until:
  tree=qa.state();v=[n.get('text') for n in tree.iter('node') if n.get('text')];assert not any(x in v for x in forbid),v
  if all(x in v for x in expected):(out/file).write_bytes((qa.root/'current.xml').read_bytes());return v
  time.sleep(.2)
 raise AssertionError(v)
install=qa.run(['install','-r',str(p)],capture_output=True,timeout=90).stdout.decode();clear=qa.run(['shell','pm','clear','org.viptv.experiment'],capture_output=True).stdout.decode();assert 'Success' in clear
launch=qa.run(['shell','am','start','-W','-n','org.viptv.experiment/.MainActivity'],capture_output=True).stdout.decode();(out/'launch.txt').write_text(launch)
wait(['Use without an account'],'first-entry-welcome.xml');qa.tap('Use without an account');wait(['Who’s watching?','Default'],'first-entry-profiles.xml',forbid=['Home']);qa.tap('Default');home=wait(['Home','My List','Search'],'home.xml');qa.screen('normal-4cb-home')
qa.run(['shell','am','force-stop','org.viptv.experiment'],capture_output=True);restart=qa.run(['shell','am','start','-W','-n','org.viptv.experiment/.MainActivity'],capture_output=True).stdout.decode();(out/'restart.txt').write_text(restart)
cold=wait(['Who’s watching?','Default'],'cold-profile.xml',forbid=['Home','Use without an account','Sign in to VIPTV']);qa.screen('normal-4cb-cold-profile');qa.tap('Default');wait(['Home','My List','Search'],'cold-selected-home.xml')
errors=qa.run(['logcat','-b','crash','-d','-v','brief','AndroidRuntime:E','*:S'],capture_output=True).stdout.decode();(out/'runtime-errors.log').write_text(errors);assert 'Process: org.viptv.experiment' not in errors
r={'passed':True,'apk_sha256':sha,'variant':'normal release','avd':qa.cfg['avd_name'],'serial':qa.serial,'sdk':26,'ui_mode':'normal 0x11','first_entry_mode_reset':'Owned fresh app data via pm clear; Success, then welcome observed before Use without account','install':install,'pm_clear':clear,'real_native_local_home':True,'cold_restore_profiles_before_home':True,'cold_restore_without_welcome':True,'unprotected_default_selected_explicitly':True,'home_labels':home,'cold_profile_labels':cold,'framework_first_launch_ms':int(re.search(r'ThisTime: (\d+)',launch).group(1)),'framework_cold_reopen_ms':int(re.search(r'ThisTime: (\d+)',restart).group(1)),'scope':'latest normal first local entry and cold Profiles gate only; unchanged debug/native player scenarios not repeated'}
(out/'result.json').write_text(json.dumps(r,indent=2));print(json.dumps(r),flush=True)
