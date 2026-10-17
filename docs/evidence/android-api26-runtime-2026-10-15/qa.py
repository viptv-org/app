from pathlib import Path
import json,subprocess,sys,time,re,xml.etree.ElementTree as ET,shlex
root=Path(__file__).resolve().parent;cfg={'serial':'emulator-5592','avd_name':'viptv-api26-isolated'};adb='/home/vynxc/Android/Sdk/platform-tools/adb';serial=cfg['serial']
def guard():
 r=subprocess.run([adb,'-s',serial,'emu','avd','name'],capture_output=True,text=True,timeout=8)
 assert r.returncode==0 and r.stdout.splitlines()[0].strip()==cfg['avd_name'],'Owned serial/name guard failed'
 assert subprocess.check_output([adb,'-s',serial,'shell','getprop','ro.build.version.sdk']).decode().strip()=='26'
def run(args,**kwargs):
 guard();return subprocess.run([adb,'-s',serial,*args],check=True,timeout=kwargs.pop('timeout',40),**kwargs)
def state():
 run(['shell','uiautomator','dump','--compressed','/sdcard/viptv-phone-qa.xml'],capture_output=True)
 data=run(['exec-out','cat','/sdcard/viptv-phone-qa.xml'],capture_output=True).stdout
 (root/'current.xml').write_bytes(data);return ET.fromstring(data)
def labels():return [{'text':n.get('text'),'desc':n.get('content-desc'),'click':n.get('clickable'),'enabled':n.get('enabled'),'checked':n.get('checked'),'bounds':n.get('bounds')}for n in state().iter('node') if n.get('text') or n.get('content-desc')]
def tap(label,contains=False,index=0):
 nodes=[n for n in state().iter('node') if any((label in n.get(k,'') if contains else label==n.get(k,'')) for k in ('text','content-desc')) and n.get('enabled')=='true']
 clickable=[n for n in nodes if n.get('clickable')=='true'];nodes=clickable or nodes
 assert len(nodes)>index,'Missing UI control: '+label
 values=list(map(int,re.findall(r'\d+',nodes[index].get('bounds'))));x=(values[0]+values[2])//2;y=(values[1]+values[3])//2
 assert x>=0 and y>=0;run(['shell','input','tap',str(x),str(y)],capture_output=True);time.sleep(.35)
def swipe(up=True):run(['shell','input','swipe','540','1800' if up else '500','540','500' if up else '1800','400'],capture_output=True);time.sleep(.4)
def enter(value):
 run(['shell','input','keyevent','123'],capture_output=True);run(['shell','input','keyevent',*(['67']*40)],capture_output=True)
 run(['shell','input text '+shlex.quote(value)],capture_output=True);time.sleep(.3)
def screen(name):
 assert re.fullmatch(r'[a-z0-9-]+',name);d=root/'captures';d.mkdir(exist_ok=True)
 (d/(name+'.png')).write_bytes(run(['exec-out','screencap','-p'],capture_output=True).stdout)
if __name__=='__main__':
 mode=sys.argv[1]
 if mode=='state':print(json.dumps(labels(),indent=2))
 elif mode=='tap':tap(sys.argv[2],contains='--contains'in sys.argv,index=int(sys.argv[3])if len(sys.argv)>3 and sys.argv[3].isdigit() else 0)
 elif mode=='swipe':swipe()
 elif mode=='text':enter(sys.argv[2])
 elif mode=='screen':screen(sys.argv[2]);print('Captured owned phone')
 elif mode=='adb':
  assert sys.argv[2] not in ('kill-server','start-server','devices');r=run(sys.argv[2:],capture_output=True,timeout=90);sys.stdout.buffer.write(r.stdout)
