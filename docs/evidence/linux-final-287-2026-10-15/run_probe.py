from pathlib import Path
import json,os,select,shutil,subprocess,sys,time

root=Path('/tmp/viptv-desktop-vault-2026-10-15')
image=Path(os.environ.get('VIPTV_VAULT_QA_IMAGE','/mnt/ALPH/code/viptv-org/app/composeApp/build/compose/binaries/main/app/VIPTV-Experiment'))
owned=root/os.environ.get('VIPTV_VAULT_QA_RUN','owned-final-candidate')
if len(sys.argv)==1:
    owned.mkdir(exist_ok=False)
    for name in ['home','data','config','cache','state','runtime','control','tmp']:(owned/name).mkdir(mode=0o700)
    (owned/'empty-xauthority').touch(mode=0o600)
    launcher=owned/'probe-app/bin/VIPTV-Experiment';launcher.parent.mkdir(parents=True);shutil.copy2(image/'bin/VIPTV-Experiment',launcher)
    lib=owned/'probe-app/lib';(lib/'app').mkdir(parents=True);(lib/'runtime').symlink_to(image/'lib/runtime',target_is_directory=True)
    shutil.copy2(image/'lib/libapplauncher.so',lib/'libapplauncher.so')
    config=['[Application]','app.mainclass=DesktopVaultHarness','app.classpath='+str(root/'classes')]
    config+=['app.classpath='+str(jar) for jar in (image/'lib/app').glob('*.jar')]
    config+=['[JavaOptions]','java-options=-Duser.home='+str(owned/'home'),'java-options=-Djava.io.tmpdir='+str(owned/'tmp')]
    if os.environ.get('VIPTV_VAULT_QA_NATIVE_OVERRIDE'):
        config+=['java-options=-Duniffi.component.viptv_core.libraryOverride='+os.environ['VIPTV_VAULT_QA_NATIVE_OVERRIDE']]
    (lib/'app/VIPTV-Experiment.cfg').write_text('\n'.join(config)+'\n')
    env=os.environ.copy()
    for key in ['DISPLAY','WAYLAND_DISPLAY','DBUS_SESSION_BUS_ADDRESS','DBUS_STARTER_ADDRESS','DBUS_STARTER_BUS_TYPE','GNOME_KEYRING_CONTROL','SSH_AUTH_SOCK']:env.pop(key,None)
    env.update(HOME=str(owned/'home'),XDG_DATA_HOME=str(owned/'data'),XDG_CONFIG_HOME=str(owned/'config'),XDG_CACHE_HOME=str(owned/'cache'),XDG_STATE_HOME=str(owned/'state'),XDG_RUNTIME_DIR=str(owned/'runtime'),XAUTHORITY=str(owned/'empty-xauthority'),GNOME_KEYRING_CONTROL=str(owned/'control'))
    subprocess.run(['dbus-run-session','--',sys.executable,__file__,'private'],env=env,check=True,timeout=45)
else:
    results={};daemon=None;xvfb=None
    def dbus(method,*args):
        return subprocess.check_output(['gdbus','call','--session','--dest','org.freedesktop.secrets','--object-path','/org/freedesktop/secrets','--method','org.freedesktop.Secret.Service.'+method,*args],text=True,timeout=5).strip()
    def run(phase,timeout=8,env=None):
        started=time.monotonic()
        try:
            result=subprocess.run([str(owned/'probe-app/bin/VIPTV-Experiment'),phase],capture_output=True,text=True,timeout=timeout,env=env)
            results[phase]={'exit_code':result.returncode,'stdout':result.stdout.splitlines(),'stderr':result.stderr.splitlines(),'seconds':round(time.monotonic()-started,3)}
        except subprocess.TimeoutExpired:
            results[phase]={'bounded_parent_timeout':True,'seconds':round(time.monotonic()-started,3)}
        assert not results[phase].get('bounded_parent_timeout'),phase+' exceeded owned timeout'
        assert results[phase]['exit_code']==0,results[phase]
        print(json.dumps({phase:results[phase]}),flush=True)
    def actual_main(phase):
        environment=os.environ.copy()
        for key in list(environment):
            if key.startswith('VIPTV_'):environment.pop(key)
        environment['JAVA_TOOL_OPTIONS']='-Xmx512m -Duser.home='+str(owned/'home')+' -Djava.io.tmpdir='+str(owned/'tmp')
        started=time.monotonic();log=(owned/(phase+'.log')).open('w');app=None
        try:
            app=subprocess.Popen([str(image/'bin/VIPTV-Experiment')],env=environment,stdout=log,stderr=subprocess.STDOUT)
            time.sleep(4)
            assert app.poll() is None,'Actual Main exited early'
            windows=subprocess.check_output(['xdotool','search','--onlyvisible','--pid',str(app.pid),'--name','^VIPTV Experiment$'],env=environment,text=True,timeout=5).splitlines()
            assert len(windows)==1,windows
            screenshot=owned/(phase+'.png')
            subprocess.run(['import','-display',environment['DISPLAY'],'-window',windows[0],str(screenshot)],env=environment,check=True,timeout=5)
            results[phase]={'actual_packaged_main':True,'launcher':str(image/'bin/VIPTV-Experiment'),'screenshot':str(screenshot),'seconds_until_capture':round(time.monotonic()-started,3),'owned_display_only':True}
        finally:
            if app is not None and app.poll() is None:
                app.terminate()
                try:app.wait(timeout=5)
                except subprocess.TimeoutExpired:app.kill();app.wait()
            log.close()
    try:
        r,w=os.pipe();xlog=(root/'private-xvfb.log').open('w')
        xvfb=subprocess.Popen(['Xvfb','-displayfd',str(w),'-screen','0','1280x800x24','-nolisten','tcp','-ac'],pass_fds=(w,),stdout=xlog,stderr=subprocess.STDOUT);os.close(w)
        assert select.select([r],[],[],5)[0];number=os.read(r,64).decode().strip();os.close(r);assert number.isdigit();os.environ['DISPLAY']=':'+number
        subprocess.run(['gdbus','call','--session','--dest','org.freedesktop.DBus','--object-path','/org/freedesktop/DBus','--method','org.freedesktop.DBus.UpdateActivationEnvironment',repr({'DISPLAY':os.environ['DISPLAY']})],check=True,stdout=subprocess.DEVNULL)
        log=(root/'keyring.log').open('w')
        daemon=subprocess.Popen(['gnome-keyring-daemon','--foreground','--unlock','--components=secrets','--control-directory',str(owned/'control')],stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT)
        daemon.stdin.write(b'owned-isolated-disposable-password');daemon.stdin.close()
        for _ in range(40):
            owner=subprocess.check_output(['gdbus','call','--session','--dest','org.freedesktop.DBus','--object-path','/org/freedesktop/DBus','--method','org.freedesktop.DBus.NameHasOwner','org.freedesktop.secrets'],text=True,timeout=5)
            if 'true' not in owner:time.sleep(.05);continue
            try:
                alias=dbus('ReadAlias','default')
                if "'/')" not in alias:break
            except subprocess.CalledProcessError:pass
            time.sleep(.05)
        results['default_collection']=alias
        run('write-local');run('read-local');run('legacy-remote')
        def seed(key,value,*attributes):
            subprocess.run(['secret-tool','store','--label=VIPTV Owned Fixture','service','org.viptv.experiment','username',key,*attributes],input=value,text=True,check=True,timeout=5)
        seed('qa-legacy','owned-legacy')
        seed('qa-priority','owned-fallback');seed('qa-priority','owned-exact','target','default')
        seed('qa-ambiguous','owned-a','target','default','qa-identity','a');seed('qa-ambiguous','owned-b','target','default','qa-identity','b')
        run('credential-matching')
        run('write-local')
        actual_main('actual-main-unlocked-local-restore')
        results['lock_result']=dbus('Lock',"[objectpath '/org/freedesktop/secrets/collection/login']")
        run('locked-access')
        run('cancel-startup')
        actual_main('actual-main-locked-storage-warning')
        results['prompter_owned_after_locked_operations']=subprocess.check_output(['gdbus','call','--session','--dest','org.freedesktop.DBus','--object-path','/org/freedesktop/DBus','--method','org.freedesktop.DBus.NameHasOwner','org.gnome.keyring.SystemPrompter'],text=True,timeout=5).strip()
        missing=os.environ.copy();missing['DBUS_SESSION_BUS_ADDRESS']='unix:path='+str(owned/'no-bus')
        run('read',env=missing)
    finally:
        if daemon is not None:
            daemon.terminate()
            try:daemon.wait(timeout=5)
            except subprocess.TimeoutExpired:daemon.kill();daemon.wait()
        if xvfb is not None:xvfb.terminate();xvfb.wait(timeout=5)
        (owned/'proof.json').write_text(json.dumps(results,indent=2)+'\n')
