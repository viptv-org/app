from pathlib import Path
import hashlib,json,os,shutil,signal,subprocess,time
root=Path('/mnt/ALPH/code/viptv-org/app');p=Path('/tmp/viptv-debian-final313-2026-10-15');r=json.loads((p/'runner.json').read_text());env=os.environ|r['environment'];name='viptv-debian-final313-20261015';steps={};created=False
sha=lambda f:hashlib.sha256(f.read_bytes()).hexdigest()
def pod(args,timeout=600,check=True):
 x=subprocess.run(r['prefix']+args,env=env,text=True,capture_output=True,timeout=timeout)
 if check and x.returncode:raise RuntimeError((args,x.returncode,x.stdout,x.stderr))
 return x

def run(label,script,timeout=300):
 start=time.monotonic();x=pod(['exec',name,'sh','-c',script],timeout=timeout,check=False)
 (p/(label+'.log')).write_text(x.stdout+x.stderr);steps[label]={'exit_code':x.returncode,'seconds':round(time.monotonic()-start,3)};(p/'steps.json').write_text(json.dumps(steps,indent=2)+'\n');assert x.returncode==0,(label,x.stdout,x.stderr)
try:
 out=root/'build/linux-final313-r2-2026-10-15'
 command=['python3',str(root/'tools/package_linux.py'),'--image',str(p/'app-image'),'--java-home','/tmp/viptv-temurin21/jdk-21.0.12.1+1','--dpkg-home',str(root/'build/packaging-tools/dpkg-1.23.7-1'),'--output',str(out)]
 started=time.monotonic()
 with (p/'package.log').open('w') as log:x=subprocess.run(command,cwd=root,stdout=log,stderr=subprocess.STDOUT,timeout=180)
 assert x.returncode==0,'Packaging failed; see package.log'
 receipt=json.loads((out/'receipt.json').read_text());digest=receipt['sha256'];artifact=root/'artifacts/linux-desktop-2026-10-15'/('final313-r2-'+digest[:12]);shutil.copytree(out,artifact)
 for f in ['image-manifest.json','source-manifest.json','source-status.txt']:shutil.copy2(p/f,artifact/f)
 (p/'artifact-directory').write_text(str(artifact)+'\n');(p/'package-step.json').write_text(json.dumps({'exit_code':0,'seconds':round(time.monotonic()-started,3),'artifact':str(artifact),'sha256':digest},indent=2)+'\n')
 script='''mkdir /qualification
cat /etc/os-release > /qualification/os-release
if test -d /usr/share/desktop-directories; then echo present; else echo absent; fi > /qualification/menu-directory-before.txt
apt-get update > /qualification/apt-update.log 2>&1
echo $? > /qualification/apt-update.exit
DEBIAN_FRONTEND=noninteractive apt-get install -y /mnt/viptv.deb > /qualification/apt-install-deb.log 2>&1
echo $? > /qualification/apt-install-deb.exit
dpkg-query -W -f='${Package} ${Version} ${db:Status-Abbrev}\\n' viptv-experiment > /qualification/dpkg-status.txt 2>&1
dpkg --audit > /qualification/dpkg-audit.txt 2>&1
date -u +%Y-%m-%dT%H:%M:%SZ > /qualification/install-complete
sleep 1200
'''
 command=r['prefix']+['run','--detach','--name',name,'--network=pasta','--cgroups=disabled','--shm-size=256m','--security-opt=no-new-privileges','--mount','type=bind,src='+str(artifact/receipt['package'])+',dst=/mnt/viptv.deb,ro','docker.io/library/debian@sha256:918311b7b6c4c6f68b232ba516584925f6c78ad82b6fd534b98979df6438e483','sh','-c',script]
 (p/'container-command.json').write_text(json.dumps(command,indent=2)+'\n');x=subprocess.run(command,env=env,text=True,capture_output=True,timeout=30);assert x.returncode==0,x.stderr;created=True;(p/'container-id').write_text(x.stdout)
 for _ in range(120):
  if pod(['exec',name,'test','-f','/qualification/install-complete'],check=False).returncode==0:break
  time.sleep(5)
 else:raise RuntimeError('Install did not finish within600s')
 run('install-gate',"test $(cat /qualification/apt-install-deb.exit) = 0 && test ! -s /qualification/dpkg-audit.txt && grep -q 'viptv-experiment 0.1.0-2 ii' /qualification/dpkg-status.txt")
 run('installed-menu',"set -e; dpkg-query -L viptv-experiment; ls -ld /usr/share/desktop-directories; cat /usr/share/applications/viptv-experiment-VIPTV-Experiment.desktop; test -x /opt/viptv-experiment/bin/VIPTV-Experiment; cat /opt/viptv-experiment/lib/runtime/release")
 run('native-smoke',"set -e; mkdir -p /qualification/smoke/home /qualification/smoke/data /qualification/smoke/cache /qualification/smoke/config; env -u DISPLAY -u WAYLAND_DISPLAY -u DBUS_SESSION_BUS_ADDRESS HOME=/qualification/smoke/home XDG_DATA_HOME=/qualification/smoke/data XDG_CONFIG_HOME=/qualification/smoke/config XDG_CACHE_HOME=/qualification/smoke/cache VIPTV_ENGINE_SMOKE_TEST=1 VIPTV_ENGINE_SMOKE_RESULT=/qualification/smoke/result JAVA_TOOL_OPTIONS='-Xmx512m -Duser.home=/qualification/smoke/home' /opt/viptv-experiment/bin/VIPTV-Experiment; test $(cat /qualification/smoke/result) = verified-local-engine",60)
 run('qa-dependencies',"DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends python3 dbus-x11 libglib2.0-bin gnome-keyring libsecret-tools xvfb xdotool imagemagick",600)
 pod(['exec',name,'mkdir','-p','/qualification/vault']);pod(['cp','/tmp/viptv-desktop-vault-2026-10-15/classes',name+':/qualification/vault/classes']);pod(['cp',str(p/'run_installed_probe.py'),name+':/qualification/vault/run_probe.py'])
 run('vault-main-probe','python3 /qualification/vault/run_probe.py',90)
 run('installed-file-integrity','dpkg --verify viptv-experiment')
 assert not (p/'installed-file-integrity.log').read_text().strip(),'Installed package files changed'
 assert sha(artifact/receipt['package'])==digest,'Immutable package bytes changed'
 (p/'passed.json').write_text(json.dumps({'passed':True,'artifact':str(artifact),'sha256':digest,'steps':steps},indent=2)+'\n')
finally:
 cleanup=[]
 if created:
  x=pod(['inspect',name],check=False);(p/'inspect.json').write_text(x.stdout)
  x=pod(['cp',name+':/qualification',str(p/'result')],check=False);cleanup.append({'action':'preserve-result','exit_code':x.returncode,'stderr':x.stderr})
  x=pod(['rm','--force',name],check=False);cleanup.append({'action':'remove-exact-owned-container','name':name,'exit_code':x.returncode,'stderr':x.stderr})
  x=pod(['ps','-a','--format','json'],check=False);cleanup.append({'action':'isolated-container-inventory','exit_code':x.returncode,'output':x.stdout})
  pause=Path(r['environment']['XDG_RUNTIME_DIR'])/'libpod/tmp/pause.pid'
  if x.returncode==0 and json.loads(x.stdout)==[] and pause.exists():
   pid=int(pause.read_text().strip());proc=Path('/proc/'+str(pid))
   if proc.exists():
    cmd=(proc/'cmdline').read_bytes().replace(b'\0',b' ').decode().strip();cg=(proc/'cgroup').read_text();assert cmd=='catatonit -P' and 'podman-pause-' in cg;os.kill(pid,signal.SIGTERM)
    for _ in range(50):
     if not proc.exists():break
     time.sleep(.05)
    cleanup.append({'action':'terminate-exact-own-pause','pid':pid,'verified_cmdline':cmd,'exists_after':proc.exists()})
 (p/'cleanup.json').write_text(json.dumps(cleanup,indent=2)+'\n')
