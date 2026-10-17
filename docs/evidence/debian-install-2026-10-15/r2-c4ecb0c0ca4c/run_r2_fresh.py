from pathlib import Path
import json,os,subprocess
p=Path('/tmp/viptv-debian-install-2026-10-15');r=json.loads((p/'runner.json').read_text());env=os.environ|r['environment']
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
sleep 2000
'''
command=r['prefix']+['run','--name','viptv-debian-r2-fresh-20261015','--network=pasta','--cgroups=disabled','--shm-size=256m','--security-opt=no-new-privileges','--mount','type=bind,src=/mnt/ALPH/code/viptv-org/app/build/linux-debian-r2-2026-10-15/viptv-experiment_0.1.0-2_amd64.deb,dst=/mnt/viptv.deb,ro','docker.io/library/debian@sha256:918311b7b6c4c6f68b232ba516584925f6c78ad82b6fd534b98979df6438e483','sh','-c',script]
(p/'fresh-command.json').write_text(json.dumps(command,indent=2)+'\n')
raise SystemExit(subprocess.run(command,env=env).returncode)
