from pathlib import Path
import sys,json,re,time,subprocess
sys.path.insert(0,'/tmp/viptv-phone-preferences-jvx0yqu1');import qa,db
p=Path(__file__).resolve().parent;url=json.loads((p/'server.json').read_text())['url']
def capture(name):
 (p/(name+'.xml')).write_bytes(qa.run(['exec-out','cat','/sdcard/viptv-phone-qa.xml'],capture_output=True).stdout)
 (p/(name+'.png')).write_bytes(qa.run(['exec-out','screencap','-p'],capture_output=True).stdout)
def bounds(n):return list(map(int,re.findall(r'\d+',n.get('bounds'))))
initial=qa.state();add=bounds(next(n for n in initial.iter('node')if n.get('text')=='Add an add-on'));advanced=bounds(next(n for n in initial.iter('node')if n.get('text')=='Advanced configuration'));assert add[3]<advanced[1]
qa.tap('Add an add-on');fields=[n for n in qa.state().iter('node')if n.get('class')=='android.widget.EditText'];assert len(fields)==1
b=bounds(fields[0]);qa.run(['shell','input','tap',str((b[0]+b[2])//2),str((b[1]+b[3])//2)],capture_output=True);qa.enter(url);qa.run(['shell','input','keyevent','4'],capture_output=True)
r=subprocess.run(['python3',str(p/'submit_failure.py')],capture_output=True,text=True,check=True);print(r.stdout,flush=True)
qa.tap('Install')
for _ in range(8):
 labels=qa.labels()
 if any(n['text']=='Synthetic phone add-on'for n in labels)and not any(n['desc']=='Close add-on form'for n in labels):break
 time.sleep(.5)
else:raise AssertionError(labels)
assert not any('could not be loaded'in(n['text']or'')for n in labels)
connections=json.loads(db.query('select * from engine_connections;').strip()or'[]');assert len(connections)==1
(p/'installed.json').write_text(json.dumps({'connections':connections,'labels':labels,'server':json.loads((p/'server-events.json').read_text())},indent=2));capture('installed')
qa.tap('Add an add-on');tree=qa.state();fields=[n for n in tree.iter('node')if n.get('class')=='android.widget.EditText'];assert len(fields)==1
labels=qa.labels();parents={c:n for n in tree.iter()for c in n};install=next(n for n in tree.iter('node')if n.get('text')=='Install')
while install in parents and install.get('clickable')!='true':install=parents[install]
assert install.get('enabled')=='false';assert fields[0].get('text')=='';assert not any(n['text']==url for n in labels)
(p/'reopened.json').write_text(json.dumps({'field_text':fields[0].get('text'),'install_enabled':False,'previous_url_absent':True,'labels':labels},indent=2));capture('reopened-blank');print('Success closes sheet; reopening shows blank URL and disabled Install',flush=True)
qa.tap('Close add-on form');qa.tap('Remove');labels=qa.labels();assert any(n['text']=='Remove Synthetic phone add-on?'for n in labels)
qa.tap('Remove');labels=qa.labels();assert not any(n['text']=='Synthetic phone add-on'for n in labels);assert any(n['text']=='No add-ons installed.'for n in labels);capture('removed')
after={key:json.loads(db.query(sql).strip()or'[]')for key,sql in [('preferences','select * from engine_profile_preferences;'),('connections','select * from engine_connections;'),('profiles','select * from engine_profiles;')]};before=json.loads((p/'baseline.json').read_text());assert before['preferences']==after['preferences'];assert before['connections']==after['connections']==[]
before_profile=json.loads(before['profiles'][0]['value']);after_profile=json.loads(after['profiles'][0]['value']);revision={'before':before_profile['scope'].pop('revision'),'after':after_profile['scope'].pop('revision')};assert revision['after']==revision['before']+2;assert before_profile==after_profile;assert after_profile['assignments']==[]
(p/'after-removal.json').write_text(json.dumps(after,indent=2));events=json.loads((p/'server-events.json').read_text());assert events['manifest_attempts']==2;assert [e['status']for e in events['events']if e['path']=='/manifest.json']==[503,200]
(p/'flow-result.json').write_text(json.dumps({'status':'passed','synthetic_provider':True,'url':url,'add_text_bounds':add,'advanced_text_bounds':advanced,'rapid_first_taps':3,'first_submit_manifest_requests':1,'failure_retains_exact_url_and_error':True,'success_sheet_closes':True,'success_error_cleared':True,'success_reopen_url_blank':True,'reopen_install_disabled':True,'own_record_removed':True,'original_preferences_unchanged':True,'original_connections_unchanged':True,'profile_descriptor_excluding_revision_unchanged':True,'expected_profile_revision':revision,'server':events},indent=2));print('Own record removed; prefs/connections restored; expected profile revision '+str(revision),flush=True)
