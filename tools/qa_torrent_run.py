#!/usr/bin/env python3
"""Native torrent/Media3 qualification on explicitly owned read-only QA AVDs.

The .torrentqa package is disposable. Local preverified-cache fixtures are
functional checks only and can never count as cold performance measurements.
"""
import argparse
import base64
import hashlib
import importlib.util
import json
from pathlib import Path
import random
import subprocess
import time
import urllib.request
import uuid
import zipfile

import playback_gateway

PACKAGE = 'org.viptv.experiment.torrentqa'
ACTIVITY = PACKAGE+'/org.viptv.experiment.TorrentColdQaActivity'
OWNED = {'emulator-5574': 'viptv-native-qa', 'emulator-5576': 'viptv-torrent-tv-20261022'}


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--serial',choices=OWNED,required=True)
    p.add_argument('--apk',type=Path,required=True)
    p.add_argument('--output',type=Path,required=True)
    p.add_argument('--gateway',type=Path)
    p.add_argument('--adb',default='/home/vynxc/Android/Sdk/platform-tools/adb')
    p.add_argument('--local-fixture',type=Path)
    p.add_argument('--cases',nargs='+',default=['sintel','cosmos-laundromat'])
    p.add_argument('--modes',nargs='+',default=['cold'])
    p.add_argument('--trials',type=int,default=3)
    p.add_argument('--engines',nargs='+',choices=['candidate','stremio'],default=['candidate'])
    p.add_argument('--reference-apk',type=Path)
    p.add_argument('--timeout',type=int,default=60)
    args=p.parse_args()
    args.gateway=args.gateway or playback_gateway.repository()
    adb=[args.adb,'-s',args.serial]
    def run(*cmd,check=True,input=None):
        return subprocess.run(adb+list(cmd),input=input,stdout=subprocess.PIPE,stderr=subprocess.PIPE,check=check,timeout=90)
    observed=run('emu','avd','name').stdout.decode().splitlines()[0].strip()
    assert observed==OWNED[args.serial], 'Refusing a non-owned AVD'
    assert not args.output.exists(), 'Use fresh evidence output'
    assert not args.local_fixture or ('cold' not in args.modes and args.engines==['candidate'])
    args.output.mkdir(parents=True)
    identity={'avd':observed,'serial':args.serial,'apk_sha256':hashlib.sha256(args.apk.read_bytes()).hexdigest(),
              'seed':20261022,'trials':args.trials,'modes':args.modes,'engines':args.engines,
              'local_fixture_functional_only':bool(args.local_fixture),'libraries':{}}
    with zipfile.ZipFile(args.apk) as z:
        for name in z.namelist():
            if name.endswith(('libtorrent_runtime.so','libtorrent_runtime_jni.so')):
                identity['libraries'][name]=hashlib.sha256(z.read(name)).hexdigest()
    identity['device']={key:run('shell','getprop',key).stdout.decode().strip() for key in
                        ['ro.build.version.sdk','ro.product.cpu.abi','ro.build.fingerprint']}
    (args.output/'run.json').write_text(json.dumps(identity,indent=2))
    run('install','-r',str(args.apk))
    reference_port=None
    if 'stremio' in args.engines:
        assert args.reference_apk
        run('install','-r',str(args.reference_apk))
        reference_port=int(run('forward','tcp:0','tcp:11470').stdout)
        identity['reference_apk_sha256']=hashlib.sha256(args.reference_apk.read_bytes()).hexdigest()
        (args.output/'run.json').write_text(json.dumps(identity,indent=2))
    module=importlib.util.spec_from_file_location('benchmark',args.gateway/'torrent-runtime/scripts/benchmark-streaming.py')
    screen=importlib.util.module_from_spec(module);module.loader.exec_module(screen)
    cases=[]
    if args.local_fixture:
        local=json.loads(args.local_fixture.read_text());cases=[local]
    else:
        for name in args.cases:
            assert name in screen.FILMS
            raw=urllib.request.urlopen('https://webtorrent.io/torrents/'+name+'.torrent',timeout=30).read()
            case=screen.decode_torrent(raw)
            case['raw']=raw
            case['source']={'magnet':'magnet:?xt=urn:btih:'+case['hash'],'file_index':case['index'],'trackers':case['trackers']}
            cases.append(case)
    def write_file(name,data):
        run('shell','-T','run-as',PACKAGE,'tee','files/'+name+'.tmp',input=data)
        run('shell','run-as',PACKAGE,'mv','files/'+name+'.tmp','files/'+name)
    def read_file(name):
        result=run('shell','-T','run-as',PACKAGE,'cat','files/'+name,check=False)
        return result.stdout if result.returncode==0 else None
    def http(path,body=None):
        req=urllib.request.Request(f'http://127.0.0.1:{reference_port}'+path,
                                   data=json.dumps(body).encode() if body is not None else None,
                                   headers={'Content-Type':'application/json'})
        with urllib.request.urlopen(req,timeout=args.timeout) as reply:return json.load(reply)
    rows=[];rng=random.Random(20261022)
    try:
        for trial in range(args.trials):
            order=list(range(len(cases)));rng.shuffle(order)
            for ordinal in order:
                for mode in args.modes:
                    engines=args.engines.copy();rng.shuffle(engines)
                    for engine in engines:
                        directory=args.output/f'trial-{trial}-{ordinal}-{mode}-{engine}';directory.mkdir()
                        row=dict(trial=trial,case=ordinal,mode=mode,engine=engine,state='cold' if mode=='cold' else 'functional',passed=False)
                        assert run('emu','avd','name').stdout.decode().splitlines()[0].strip()==OWNED[args.serial]
                        run('shell','am','force-stop',PACKAGE)
                        run('shell','pm','clear',PACKAGE)
                        if engine=='stremio':run('shell','pm','clear','com.stremio.one')
                        run('shell','run-as',PACKAGE,'mkdir','-p','files')
                        case=cases[ordinal]
                        source=case['source']
                        if mode=='expire' and not args.local_fixture:
                            source={'metainfo':base64.b64encode(case['raw']).decode(),'file_index':case['index']}
                        if mode=='cancel':source={'magnet':'magnet:?xt=urn:btih:'+hashlib.sha1(uuid.uuid4().bytes).hexdigest()}
                        spec=dict(trial_id=uuid.uuid4().hex,mode=mode,source=source,timeout_ms=args.timeout*1000,reference=engine=='stremio')
                        if args.local_fixture and mode not in ('expire','cancel'):
                            spec['local_fixture']=case['local_fixture']
                            write_file('torrent-qa-fixture.mp4',Path(case['movie']).read_bytes())
                        write_file('torrent-qa-input.json',json.dumps(spec).encode())
                        start=time.monotonic();captured=False
                        try:
                            run('shell','am','start','-W','-n',ACTIVITY,'--ez','qa_tv',str(args.serial=='emulator-5576').lower())
                            if engine=='stremio':
                                while read_file('torrent-qa-reference-ready') is None:
                                    if time.monotonic()-start>args.timeout:raise TimeoutError('reference marker')
                                    time.sleep(.05)
                                activity='com.stremio.tv.MainActivity' if args.serial=='emulator-5576' else 'com.stremio.android.MainActivity'
                                run('shell','am','start','-W','-n','com.stremio.one/'+activity)
                                while True:
                                    try:settings=http('/settings');break
                                    except Exception:
                                        if time.monotonic()-start>args.timeout:raise TimeoutError('reference server')
                                        time.sleep(.1)
                                (directory/'reference-settings.json').write_text(json.dumps(settings,indent=2))
                                data=http('/'+case['hash']+'/create',{'peerSearch':{'min':40,'max':200,'sources':['dht:'+case['hash']]+['tracker:'+t for t in case['trackers']]}})
                                if not data.get('files'):raise RuntimeError('reference metadata unavailable')
                                run('shell','am','start','--activity-reorder-to-front','--activity-single-top','-n',ACTIVITY)
                                write_file('torrent-qa-reference-reply.json',json.dumps({'url':f"http://127.0.0.1:11470/{case['hash']}/{case['index']}"}).encode())
                            deadline=start+args.timeout+25
                            while time.monotonic()<deadline:
                                if not captured and read_file('torrent-qa-frame.json'):
                                    if trial==0:(directory/'first-frame.png').write_bytes(run('exec-out','screencap','-p').stdout)
                                    captured=True
                                result=read_file('torrent-qa-output.json')
                                if result:
                                    try: complete=json.loads(result)
                                    except json.JSONDecodeError:
                                        time.sleep(.05);continue
                                    assert complete.get('trial_id')==spec['trial_id'] and complete.get('mode')==mode
                                    row.update(complete);row['engine']=engine;break
                                time.sleep(.2)
                            else:raise TimeoutError('activity result')
                            pids=run('shell','pidof',PACKAGE+':torrent_runtime',check=False)
                            assert not pids.stderr.strip(), 'Worker PID check failed'
                            row['actual_worker_pid_absent']=not pids.stdout.strip()
                            if not row['actual_worker_pid_absent']:row['passed']=False
                            error=read_file('torrent-qa-error.txt')
                            if error:(directory/'error.txt').write_bytes(error)
                        except Exception as error:
                            row.update(passed=False,harness_error=type(error).__name__)
                        finally:
                            row['wall_seconds']=time.monotonic()-start
                            (directory/'result.json').write_text(json.dumps(row,indent=2))
                            run('shell','am','force-stop',PACKAGE)
                            if engine=='stremio':run('shell','am','force-stop','com.stremio.one')
                        rows.append(row)
                        (args.output/'results.json').write_text(json.dumps(rows,indent=2))
                        report=screen.summary(rows,args.trials)
                        if args.modes==['cold'] and set(args.engines)=={'candidate','stremio'}:report['cold_start_gate']=screen.cold_start_gate(rows)
                        report['measurement']='Android Media3 native renderer callback on owned emulator, not physical scanout'
                        (args.output/'summary.json').write_text(json.dumps(report,indent=2))
                        print(json.dumps({k:v for k,v in row.items() if k!='player_samples'}),flush=True)
    finally:
        if reference_port:run('forward','--remove','tcp:'+str(reference_port),check=False)


if __name__=='__main__':main()
