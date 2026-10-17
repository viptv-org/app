"""Normal-release minimum-OS smoke; hard guards reside in qa.py."""
import sys,json,re,hashlib,sqlite3,time,argparse
from pathlib import Path
import qa
p=argparse.ArgumentParser();p.add_argument('--apk',type=Path,required=True);p.add_argument('--sha256',required=True);a=p.parse_args()
assert hashlib.sha256(a.apk.read_bytes()).hexdigest()==a.sha256
out=qa.root/'normal-current';out.mkdir(exist_ok=True)
def labels():return [n.get('text') for n in qa.state().iter('node') if n.get('text')]
def wait_labels(required):
 until=time.monotonic()+30
 while time.monotonic()<until:
  v=labels()
  if all(x in v for x in required):return v
  time.sleep(.2)
 raise AssertionError(v)
qa.run(['install','-r',str(a.apk)],capture_output=True,timeout=90)
qa.run(['shell','pm','clear','org.viptv.experiment'],capture_output=True)
launch=qa.run(['shell','am','start','-W','-n','org.viptv.experiment/.MainActivity'],capture_output=True).stdout.decode();(out/'launch.txt').write_text(launch)
wait_labels(['Use without an account']);qa.tap('Use without an account');wait_labels(['Default']);qa.tap('Default');wait_labels(['Home','My List','Search'])
qa.screen('normal-current-home');qa.tap('Settings');qa.tap('Playback');wait_labels(['Playback preferences']);qa.tap('Best match')
qa.run(['shell','input','swipe','730','490','730','200','400'],capture_output=True);qa.tap('1080p');qa.tap('Large')
qa.run(['shell','input','swipe','730','490','730','180','400'],capture_output=True);qa.tap('Save preferences');time.sleep(.5)
qa.run(['shell','am','force-stop','org.viptv.experiment'],capture_output=True)
db=qa.run(['exec-out','su','0','cat','/data/data/org.viptv.experiment/files/local.sqlite'],capture_output=True).stdout
private=qa.root/'normal-current-local.sqlite';private.write_bytes(db);private.chmod(0o600)
c=sqlite3.connect(private);row=c.execute('select * from engine_profile_preferences').fetchone();c.close();v=json.loads(row[-1]);assert v['source_selection']=='best' and v['max_quality_height']==1080 and v['subtitle_font_scale']==1.25
restart=qa.run(['shell','am','start','-W','-n','org.viptv.experiment/.MainActivity'],capture_output=True).stdout.decode();(out/'restart.txt').write_text(restart)
until=time.monotonic()+30
while time.monotonic()<until:
 restored=labels()
 if 'Who’s watching?' in restored:break
 assert 'Home' not in restored, 'Cold local restore bypassed profile gate'
 time.sleep(.2)
else:raise AssertionError(restored)
assert 'Use without an account' not in restored and 'Sign in to VIPTV' not in restored
(out/'cold-restore.xml').write_bytes((qa.root/'current.xml').read_bytes());qa.screen('normal-current-cold-restore')
if 'Who’s watching?' in restored:qa.tap('Default')
wait_labels(['Home','My List','Search']);qa.tap('Settings');qa.tap('Playback');qa.run(['shell','input','swipe','730','490','730','200','400'],capture_output=True);qa.state();qa.screen('normal-current-preferences-reopened');(out/'preferences-reopened.xml').write_bytes((qa.root/'current.xml').read_bytes())
result={'passed':True,'sdk':26,'apk_sha256':a.sha256,'variant':'normal release','native_profile_preferences_revision':row[-2],'persisted_preferences':v,'cold_restore_skipped_signin':True,'cold_restore_profile_gate_before_home':True,'cold_restore_labels':restored,'native_local_home':True,'first_framework_launch_ms':int(re.search(r'ThisTime: (\d+)',launch).group(1)),'reopen_framework_launch_ms':int(re.search(r'ThisTime: (\d+)',restart).group(1))}
(out/'result.json').write_text(json.dumps(result,indent=2));print(json.dumps(result),flush=True)
