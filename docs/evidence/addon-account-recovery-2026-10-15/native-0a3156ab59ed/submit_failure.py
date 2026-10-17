import sys,json,re,time
from pathlib import Path
sys.path.insert(0,'/tmp/viptv-phone-preferences-jvx0yqu1');import qa,db
out=Path(__file__).resolve().parent;url=json.loads((out/'server.json').read_text())['url']
tree=qa.state();parents={child:node for node in tree.iter()for child in node}
n=next(n for n in tree.iter('node')if n.get('text')=='Install')
while n in parents and n.get('clickable')!='true':n=parents[n]
assert n.get('enabled')=='true';b=list(map(int,re.findall(r'\d+',n.get('bounds'))));x=(b[0]+b[2])//2;y=(b[1]+b[3])//2
qa.run(['shell',f'input tap {x} {y}; input tap {x} {y}; input tap {x} {y}'],capture_output=True)
time.sleep(2.2)
labels=qa.labels();events=json.loads((out/'server-events.json').read_text());assert events['manifest_attempts']==1,events
assert any(n['text']==url for n in labels),labels
assert any('could not be loaded' in(n['text']or'') for n in labels),labels
assert any(n['text']=='Install'and n['enabled']=='true'for n in labels)
assert any(n['desc']=='Close add-on form'and n['enabled']=='true'for n in labels)
assert not json.loads(db.query('select * from engine_connections;').strip()or'[]')
(out/'failure.json').write_text(json.dumps({'url':url,'rapid_taps':3,'manifest_attempts':1,'labels':labels,'server':events},indent=2))
(out/'failure.xml').write_bytes(qa.run(['exec-out','cat','/sdcard/viptv-phone-qa.xml'],capture_output=True).stdout)
(out/'failure.png').write_bytes(qa.run(['exec-out','screencap','-p'],capture_output=True).stdout)
print('Failure retains same URL/error; three rapid taps produced exactly one manifest request')
