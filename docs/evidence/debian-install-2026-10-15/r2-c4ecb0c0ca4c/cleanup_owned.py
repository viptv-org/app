from pathlib import Path
import json,os,subprocess
p=Path('/tmp/viptv-debian-install-2026-10-15');r=json.loads((p/'runner.json').read_text());env=os.environ|r['environment'];records=[]
# Save authoritative final inspect before stopping only this task's resources.
name='viptv-debian-r2-fresh-20261015'
x=subprocess.run(r['prefix']+['inspect',name],env=env,text=True,capture_output=True)
(p/'fresh-inspect.json').write_text(x.stdout)
for unit in ['viptv-debian-r2-probes-20261015.service','viptv-debian-r2-qualification-20261015.service']:
 x=subprocess.run(['systemctl','--user','stop',unit],text=True,capture_output=True);records.append({'action':'stop-own-user-unit','unit':unit,'exit_code':x.returncode,'stderr':x.stderr})
ids=[(p/'container-id').read_text().strip(),(p/'container-r2-id').read_text().strip(),name]
for cid in ids:
 x=subprocess.run(r['prefix']+['rm','--force',cid],env=env,text=True,capture_output=True);records.append({'action':'remove-own-container','id':cid,'exit_code':x.returncode,'stdout':x.stdout,'stderr':x.stderr})
x=subprocess.run(r['prefix']+['ps','-a','--format','json'],env=env,text=True,capture_output=True)
records.append({'action':'own-store-container-inventory','exit_code':x.returncode,'containers':json.loads(x.stdout) if x.returncode==0 else x.stdout})
for unit in ['viptv-debian-r2-probes-20261015.service','viptv-debian-r2-qualification-20261015.service']:
 x=subprocess.run(['systemctl','--user','is-active',unit],text=True,capture_output=True);records.append({'action':'own-unit-active-state','unit':unit,'state':x.stdout.strip(),'exit_code':x.returncode})
(p/'cleanup.json').write_text(json.dumps({'records':records,'host_package_installation':False,'host_manual_services_touched':False,'user_default_containers_touched':False,'host_display_or_wallet_accessed':False,'isolated_cached_image_retained':True},indent=2)+'\n')
print((p/'cleanup.json').read_text())
