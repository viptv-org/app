from pathlib import Path
import json,os,subprocess,time
p=Path('/tmp/viptv-debian-install-2026-10-15');r=json.loads((p/'runner.json').read_text());env=os.environ|r['environment'];name='viptv-debian-r2-fresh-20261015';steps={}
def call(args,timeout=600,check=True):
    result=subprocess.run(r['prefix']+args,env=env,text=True,capture_output=True,timeout=timeout)
    if check and result.returncode:raise RuntimeError((args,result.returncode,result.stdout,result.stderr))
    return result

def run(label,script,timeout=300):
    start=time.monotonic();result=call(['exec',name,'sh','-c',script],timeout=timeout,check=False)
    (p/('fresh-'+label+'.log')).write_text(result.stdout+result.stderr)
    steps[label]={'exit_code':result.returncode,'seconds':round(time.monotonic()-start,3)}
    (p/'fresh-qualification-steps.json').write_text(json.dumps(steps,indent=2)+'\n')
    assert result.returncode==0,(label,result.stdout,result.stderr)
try:
    for _ in range(120):
        result=call(['exec',name,'test','-f','/qualification/install-complete'],check=False)
        if result.returncode==0:break
        time.sleep(5)
    else:raise RuntimeError('Install not complete within600s')
    run('install-gate',"test $(cat /qualification/apt-install-deb.exit) = 0 && test ! -s /qualification/dpkg-audit.txt && grep -q 'viptv-experiment 0.1.0-2 ii' /qualification/dpkg-status.txt")
    run('installed-menu',"set -e; dpkg-query -L viptv-experiment; ls -ld /usr/share/desktop-directories; cat /usr/share/applications/viptv-experiment-VIPTV-Experiment.desktop; test -x /opt/viptv-experiment/bin/VIPTV-Experiment; cat /opt/viptv-experiment/lib/runtime/release")
    run('native-smoke',"set -e; mkdir -p /qualification/smoke/home /qualification/smoke/data /qualification/smoke/cache /qualification/smoke/config; env -u DISPLAY -u WAYLAND_DISPLAY -u DBUS_SESSION_BUS_ADDRESS HOME=/qualification/smoke/home XDG_DATA_HOME=/qualification/smoke/data XDG_CONFIG_HOME=/qualification/smoke/config XDG_CACHE_HOME=/qualification/smoke/cache VIPTV_ENGINE_SMOKE_TEST=1 VIPTV_ENGINE_SMOKE_RESULT=/qualification/smoke/result JAVA_TOOL_OPTIONS='-Xmx512m -Duser.home=/qualification/smoke/home' /opt/viptv-experiment/bin/VIPTV-Experiment; test $(cat /qualification/smoke/result) = verified-local-engine",60)
    run('qa-dependencies',"DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends python3 dbus-x11 libglib2.0-bin gnome-keyring libsecret-tools xvfb xdotool imagemagick",600)
    call(['exec',name,'mkdir','-p','/qualification/vault'])
    call(['cp','/tmp/viptv-desktop-vault-2026-10-15/classes',name+':/qualification/vault/classes'])
    call(['cp',str(p/'run_installed_probe.py'),name+':/qualification/vault/run_probe.py'])
    run('vault-main-probe','python3 /qualification/vault/run_probe.py',90)
finally:
    call(['cp',name+':/qualification',str(p/'fresh-result')],check=False)
