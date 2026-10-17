import sys,time,json,re,ssl,urllib.request,subprocess,threading
from pathlib import Path
sys.path.insert(0,'/tmp/viptv-api26-qa');import qa
root=qa.root/'hls';root.mkdir(exist_ok=True);origin='https://127.0.0.1:18592'
ctx=ssl.create_default_context(cafile='/mnt/ALPH/code/viptv-org/.local-https/certs/ca.crt')
with urllib.request.urlopen(urllib.request.Request(origin+'/qa/reset-media',data=b'{}',method='POST'),context=ctx) as r:assert r.status==200
qa.guard();stream=subprocess.Popen([qa.adb,'-s',qa.serial,'logcat','-b','main','-T','1','-v','brief','VIPTV_PLAYBACK_QA:I','AndroidRuntime:E','*:S'],stdout=subprocess.PIPE,bufsize=0)
lines=[];samples=[];lock=threading.Lock();stages=[]
def read():
 for raw in stream.stdout:
  line=raw.decode(errors='replace')
  with lock:
   lines.append(line);m=re.search(r'position_ms=(\d+) duration_ms=',line)
   if m:samples.append((time.monotonic(),int(m.group(1))))
thread=threading.Thread(target=read,daemon=True);thread.start()
def wait(predicate,seconds=30):
 until=time.monotonic()+seconds
 while time.monotonic()<until:
  assert not any('failed=true' in x or 'FATAL EXCEPTION' in x for x in lines),'Player failed'
  if predicate():return
  time.sleep(.1)
 raise AssertionError('Timed out: '+str(stages))
def return_task():
 qa.run(['shell','input','keyevent','KEYCODE_APP_SWITCH'],capture_output=True)
 tree=qa.state()
 cards=[n for n in tree.iter('node') if n.get('clickable')=='true' and n.get('class')=='android.widget.FrameLayout' and any(c.get('text')=='VIPTV Experiment' for c in n.iter('node'))]
 assert len(cards)==1,'Expected owned app card in Recents'
 v=list(map(int,re.findall(r'\d+',cards[0].get('bounds'))));qa.run(['shell','input','tap',str((v[0]+v[2])//2),str((v[1]+v[3])//2)],capture_output=True)
 wait(lambda:any('mCurrentFocus=' in line and 'NativePlaybackQaActivity' in line for line in qa.run(['shell','dumpsys','window','windows'],capture_output=True).stdout.decode().splitlines()))
def mark(name):
 with lock:s={'stage':name,'position_ms':samples[-1][1] if samples else None,'sample_count':len(samples)}
 stages.append(s);print(json.dumps(s),flush=True);return s
launch=['shell','am','start','-S','-n','org.viptv.experiment/.NativePlaybackQaActivity','--es','qa_media_uri',origin+'/media/index.m3u8','--el','qa_position_ms','8000','--es','qa_title','API26-H264-AAC']
started=time.monotonic();result={'passed':False}
try:
 qa.run(launch,capture_output=True)
 wait(lambda:any('presentation=NativeRendererPresented' in x for x in lines))
 wait(lambda:samples and samples[-1][1]>=10500)
 first=mark('playing');assert 'ffmpeg_audio_available=true' in ''.join(lines)
 qa.run(['shell','input','keyevent','KEYCODE_MEDIA_PLAY_PAUSE'],capture_output=True);time.sleep(.6);p=mark('user_paused')
 time.sleep(2);q=mark('paused_stable');assert abs(q['position_ms']-p['position_ms'])<=250
 qa.run(['shell','input','keyevent','KEYCODE_DPAD_LEFT'],capture_output=True)
 qa.run(['shell','input','keyevent','KEYCODE_DPAD_CENTER'],capture_output=True)
 wait(lambda:samples[-1][1]<p['position_ms']-700)
 seek=mark('paused_seek');time.sleep(1);assert abs(samples[-1][1]-seek['position_ms'])<=250
 qa.state();qa.screen('api26-hls-paused-seek');(root/'paused-seek.xml').write_bytes((qa.root/'current.xml').read_bytes())
 qa.run(['shell','input','keyevent','KEYCODE_HOME'],capture_output=True);time.sleep(2)
 before=len(samples)
 return_task()
 wait(lambda:len(samples)>before);time.sleep(1);ret=mark('paused_home_return');assert abs(ret['position_ms']-seek['position_ms'])<=250
 qa.run(['shell','input','keyevent','KEYCODE_MEDIA_PLAY_PAUSE'],capture_output=True)
 wait(lambda:samples[-1][1]>=ret['position_ms']+1500);playing=mark('playing_resumed')
 qa.run(['shell','input','keyevent','KEYCODE_HOME'],capture_output=True);time.sleep(.4);bg=mark('playing_background_stopped');time.sleep(2)
 before=len(samples);return_task()
 wait(lambda:len(samples)>before);returned=samples[before][1];assert abs(returned-bg['position_ms'])<=500
 wait(lambda:samples[-1][1]>=returned+1500);mark('playing_home_return')
 body=''.join(lines);assert len(re.findall('opening_position_ms=',body))==1,'Player reopened across lifecycle'
 assert 'audio_decoder=ffmpeg' in body and '-aac' in body
 result={'passed':True,'sdk':26,'avd':qa.cfg['avd_name'],'immutable_debug_sha256':'7699e92dc23a037933402abebfcf30bcba40b378ea4d37c212efddd3424cf414','scope':'real NativePlaybackQaActivity and TvPlayerScreen on normal-mode API26 OS; not MainActivity/controller TV lifecycle','stages':stages,'paused_seek_delta_ms':seek['position_ms']-p['position_ms'],'first_return_position_ms':returned,'opening_count':1,'wall_ms':round((time.monotonic()-started)*1000),'ffmpeg_aac':True,'h264_rendered':True,'first_frame':True,'user_paused_home_return':True,'playing_home_return':True}
finally:
 stream.terminate();stream.wait(timeout=5);thread.join(timeout=2)
 (root/'activity.log').write_text(''.join(lines));(root/'samples.json').write_text(json.dumps(samples));(root/'result.json').write_text(json.dumps(result,indent=2));qa.run(['shell','am','force-stop','org.viptv.experiment'],capture_output=True)
print(json.dumps(result),flush=True)
