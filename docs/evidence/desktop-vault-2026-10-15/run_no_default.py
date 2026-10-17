from pathlib import Path
import json,os,shutil,subprocess,sys,time
root=Path('/tmp/viptv-desktop-vault-2026-10-15');owned=root/'owned-no-default'
if len(sys.argv)==1:
    owned.mkdir()
    for name in ['home','data','config','cache','runtime','control','tmp']:(owned/name).mkdir(mode=0o700)
    shutil.copytree(root/'owned-fixed-final/probe-app',owned/'probe-app',symlinks=True)
    config=owned/'probe-app/lib/app/VIPTV-Experiment.cfg';config.write_text(config.read_text().replace(str(root/'owned-fixed-final'),str(owned)))
    env=os.environ.copy()
    for key in ['DISPLAY','WAYLAND_DISPLAY','DBUS_SESSION_BUS_ADDRESS','DBUS_STARTER_ADDRESS','DBUS_STARTER_BUS_TYPE','GNOME_KEYRING_CONTROL','SSH_AUTH_SOCK']:env.pop(key,None)
    env.update(HOME=str(owned/'home'),XDG_DATA_HOME=str(owned/'data'),XDG_CONFIG_HOME=str(owned/'config'),XDG_CACHE_HOME=str(owned/'cache'),XDG_RUNTIME_DIR=str(owned/'runtime'),GNOME_KEYRING_CONTROL=str(owned/'control'))
    subprocess.run(['dbus-run-session','--',sys.executable,__file__,'private'],env=env,check=True,timeout=20)
else:
    log=(root/'no-default-daemon.log').open('w');daemon=None;result={}
    def alias():return subprocess.check_output(['gdbus','call','--session','--dest','org.freedesktop.secrets','--object-path','/org/freedesktop/secrets','--method','org.freedesktop.Secret.Service.ReadAlias','default'],text=True,timeout=3).strip()
    try:
        daemon=subprocess.Popen(['gnome-keyring-daemon','--foreground','--components=secrets','--control-directory',str(owned/'control')],stdin=subprocess.DEVNULL,stdout=log,stderr=subprocess.STDOUT)
        for _ in range(60):
            owner=subprocess.check_output(['gdbus','call','--session','--dest','org.freedesktop.DBus','--object-path','/org/freedesktop/DBus','--method','org.freedesktop.DBus.NameHasOwner','org.freedesktop.secrets'],text=True,timeout=3)
            if 'true' in owner:break
            time.sleep(.05)
        result['default_alias_before']=alias();assert result['default_alias_before']=="(objectpath '/',)"
        started=time.monotonic();p=subprocess.run([str(owned/'probe-app/bin/VIPTV-Experiment'),'locked-access'],capture_output=True,text=True,timeout=5)
        result.update(exit_code=p.returncode,stdout=p.stdout.splitlines(),stderr=p.stderr.splitlines(),seconds=round(time.monotonic()-started,3))
        assert p.returncode==0 and 'vault_access_failures:3' in p.stdout,result
        result['default_alias_after']=alias();assert result['default_alias_before']==result['default_alias_after']
        result['keyring_data_files_created']=[str(p.relative_to(owned/'data')) for p in (owned/'data').rglob('*') if p.is_file()]
        assert not result['keyring_data_files_created'],result
        print(json.dumps(result,indent=2))
    finally:
        if daemon is not None:
            daemon.terminate()
            try:daemon.wait(timeout=3)
            except subprocess.TimeoutExpired:daemon.kill();daemon.wait()
        (root/'no-default.json').write_text(json.dumps(result,indent=2)+'\n')
