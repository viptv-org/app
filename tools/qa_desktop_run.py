#!/usr/bin/env python3
"""Run an explicit owned-file desktop probe in its own Xvfb and silent audio sink."""
import argparse,hashlib,json,os,select,subprocess,time
from pathlib import Path
from PIL import Image

parser=argparse.ArgumentParser();parser.add_argument('--launcher',type=Path,required=True);parser.add_argument('--output',type=Path,required=True);parser.add_argument('--subtitle-format',choices=['vtt','srt','ass'],default='vtt');args=parser.parse_args()
assert args.launcher.is_file();args.output.mkdir(parents=True,exist_ok=False);out=args.output.resolve()
for name in ['tmp','home','cache','data']: (out/name).mkdir()
log=out/'run.log';result=out/'player.json';media=out/'owned.mp4';caption=out/('owned.'+args.subtitle_format)
font=subprocess.check_output(['fc-match','-f','%{file}','sans'],text=True).strip()
ff=['ffmpeg','-nostdin','-hide_banner','-loglevel','error','-f','lavfi','-i',f"color=c=0x202020:s=960x540:r=30:d=20,drawtext=fontfile={font}:text='OWNED DESKTOP FRAME %{{n}}':x=40:y=40:fontsize=28:fontcolor=white",'-f','lavfi','-i','sine=frequency=440:sample_rate=48000:duration=20','-f','lavfi','-i','sine=frequency=880:sample_rate=48000:duration=20','-map','0:v','-map','1:a','-map','2:a','-c:v','libx264','-preset','ultrafast','-pix_fmt','yuv420p','-threads','2','-c:a','aac','-metadata:s:a:0','language=eng','-metadata:s:a:1','language=spa','-disposition:a:0','default','-disposition:a:1','0','-shortest',str(media)]
subprocess.run(ff,check=True,stdout=subprocess.DEVNULL,stderr=(out/'ffmpeg.log').open('w'),timeout=40)
text='DESKTOP STYLE CHECK'
if args.subtitle_format=='vtt':caption.write_text('WEBVTT\n\n00:00:00.000 --> 00:00:20.000\n'+text+'\n')
elif args.subtitle_format=='srt':caption.write_text('1\n00:00:00,000 --> 00:00:20,000\n'+text+'\n')
else:caption.write_text('[Script Info]\nScriptType: v4.00+\nPlayResX: 960\nPlayResY: 540\n[V4+ Styles]\nFormat: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\nStyle: Default,Noto Sans,30,&H00FFFFFF,&H00FFFFFF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,0,0,2,10,10,30,1\n[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\nDialogue: 0,0:00:00.00,0:00:20.00,Default,,0,0,0,,'+text+'\n')
owned=[];module=None;player=None;xvfb=None;displayfd=None;baseline=subprocess.check_output(['pactl','get-default-sink'],text=True).strip();sink='viptv_desktop_owned_'+str(os.getpid());screens={};window_moved=False;loaded_jvm=None;start=time.monotonic();exit_code=None
try:
 # Add/remove only this owned sink; never change default sink, volume or existing streams.
 module=subprocess.check_output(['pactl','load-module','module-null-sink','sink_name='+sink,'sink_properties=device.description=VIPTV_Isolated_QA'],text=True).strip();assert module.isdigit()
 assert subprocess.check_output(['pactl','get-default-sink'],text=True).strip()==baseline,'Owned sink changed default; aborting before playback'
 alsa=out/'alsa.conf';alsa.write_text('pcm.!default { type pulse }\nctl.!default { type pulse }\n')
 r,w=os.pipe();xlog=(out/'xvfb.log').open('w');xvfb=subprocess.Popen(['Xvfb','-displayfd',str(w),'-screen','0','1280x720x24','-nolisten','tcp','-ac'],pass_fds=(w,),stdout=xlog,stderr=subprocess.STDOUT);os.close(w)
 assert select.select([r],[],[],10)[0],'Xvfb startup timeout';number=os.read(r,64).decode().strip();os.close(r);assert number.isdigit();display=':'+number
 env=os.environ.copy();env.update({'DISPLAY':display,'PULSE_SINK':sink,'ALSA_CONFIG_PATH':str(alsa),'XDG_DATA_HOME':str(out/'data'),'XDG_CACHE_HOME':str(out/'cache'),'VIPTV_DESKTOP_PLAYER_QA':'1','VIPTV_DESKTOP_QA_SCRIPTED':'1','VIPTV_DESKTOP_QA_URI':media.as_uri(),'VIPTV_DESKTOP_QA_SUBTITLE':str(caption),'VIPTV_DESKTOP_QA_RESULT':str(result),'VIPTV_DESKTOP_QA_START_MS':'2000','VIPTV_DESKTOP_QA_SCALE':'1.5','VIPTV_DESKTOP_QA_TEXT_COLOR':'#FFFFC107','VIPTV_DESKTOP_QA_BACKGROUND_COLOR':'#CC0000FF','JAVA_TOOL_OPTIONS':'-Djava.io.tmpdir='+str(out/'tmp')+' -Duser.home='+str(out/'home')})
 output=log.open('w');player=subprocess.Popen([str(args.launcher.resolve())],env=env,stdout=output,stderr=subprocess.STDOUT)
 while player.poll() is None and time.monotonic()-start<80:
  try:state=json.loads(result.read_text())
  except (FileNotFoundError,json.JSONDecodeError):state={}
  if loaded_jvm is None:
   try:
    for line in Path(f'/proc/{player.pid}/maps').read_text().splitlines():
     if '/libjvm.so' in line:loaded_jvm=line.split()[-1];break
   except FileNotFoundError:pass
  phase=state.get('phase')
  if phase and not window_moved:
   ids=subprocess.check_output(['xdotool','search','--onlyvisible','--name','^VIPTV Desktop Playback QA$'],env=env,text=True).splitlines()
   assert ids,'Owned QA window not visible'
   subprocess.run(['xdotool','windowmove','--sync',ids[0],'0','0'],env=env,check=True,timeout=5);window_moved=True
  if phase in ['playing','paused','caption-off','caption-on','caption-sized'] and phase not in screens:
   time.sleep(.18);path=out/(phase+'.png');subprocess.run(['import','-display',display,'-window','root',str(path)],check=True,timeout=5);screens[phase]=str(path)
  time.sleep(.03)
 if player.poll() is None:player.terminate();raise RuntimeError('Owned desktop probe exceeded80seconds')
 exit_code=player.wait(timeout=10);output.close();state=json.loads(result.read_text())
 assert exit_code==0 and state.get('passed') is True,(exit_code,state)
 for marker in ['pure virtual','fatal error','terminate called','segmentation fault']:
  assert marker not in log.read_text().lower(),marker
 # The generated movie contains only grey/white pixels; gold pixels can only be captions.
 metrics={}
 for phase in ['caption-off','caption-on','caption-sized']:
  image=Image.open(screens[phase]).convert('RGB');points=[(x,y) for y in range(image.height//2,image.height) for x in range(image.width) if (lambda c:c[0]>200 and 100<c[1]<230 and c[2]<80)(image.getpixel((x,y)))]
  metrics[phase]={'gold_pixels':len(points),'bounds':([min(x for x,y in points),min(y for x,y in points),max(x for x,y in points),max(y for x,y in points)] if points else None)}
 assert metrics['caption-off']['gold_pixels']<10,metrics
 assert metrics['caption-on']['gold_pixels']>150 and metrics['caption-sized']['gold_pixels']>100,metrics
 on=metrics['caption-on']['bounds'];sized=metrics['caption-sized']['bounds'];assert sized[2]-sized[0]<on[2]-on[0],metrics
 receipt={'passed':True,'launcher':str(args.launcher.resolve()),'fixture':'generated20s960x540H264/30fps +twoAAC tracks; no media network','subtitle_format':args.subtitle_format,'media_sha256':hashlib.sha256(media.read_bytes()).hexdigest(),'subtitle_sha256':hashlib.sha256(caption.read_bytes()).hexdigest(),'result':state,'screenshots':screens,'pixel_checks':metrics,'elapsed_seconds':round(time.monotonic()-start,2),'runtime_scope':'Linux bundled JVM under owned Xvfb/virtual silent audio; not physical display/audio/GPU/Windows/macOS acceptance','actual_desktop_ui_touched':False,'loaded_jvm':loaded_jvm,'default_audio_sink_preserved':True}
 (out/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n');print(json.dumps({'passed':True,'format':args.subtitle_format,'output':str(out),'pixel_checks':metrics},indent=2))
finally:
 if player is not None and player.poll() is None:player.terminate();player.wait(timeout=10)
 if xvfb is not None and xvfb.poll() is None:xvfb.terminate();xvfb.wait(timeout=10)
 if module is not None:subprocess.run(['pactl','unload-module',module],check=True)
 assert subprocess.check_output(['pactl','get-default-sink'],text=True).strip()==baseline,'Default sink changed during probe'
