#!/usr/bin/env python3
"""Read real metadata seeds; discover real enabled sources; cache labels without playback handles."""
import argparse, concurrent.futures, json, os, pathlib, re, threading, time, urllib.request, urllib.error

def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--origin',default='https://viptv.local.test:18445');parser.add_argument('--seed-dir',type=pathlib.Path,required=True);parser.add_argument('--session',type=pathlib.Path,required=True);parser.add_argument('--output',type=pathlib.Path,required=True);parser.add_argument('--budget',type=float,default=55);args=parser.parse_args()
 assert 10<=args.budget<60
 os.umask(0o077);args.output.mkdir(mode=0o700,exist_ok=False);raw=args.output/'raw';raw.mkdir(mode=0o700)
 session=json.loads(args.session.read_text());token=session['access_token'];private_strings=[v for k,v in session.items() if isinstance(v,str) and ('token' in k or 'secret' in k)]
 lock=threading.Lock();started=time.monotonic();deadline=started+args.budget
 def save(path,value):path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n');path.chmod(0o600)
 def clean(value):
  if isinstance(value,str):
   for secret in private_strings:value=value.replace(secret,'[credential removed]')
   value=re.sub(r'(?i)(?:https?|ftp|magnet|acestream)\s*:[^\s<>"\']+', '[URL removed]',value)
   value=re.sub(r'(?i)\b(?:bearer|access_token|refresh_token|api_key|apikey|password|authorization)\s*[:= ]\s*[^\s,;]+','[credential removed]',value)
   value=re.sub(r'(?i)\b[0-9a-f]{32,128}\b','[hash removed]',value)
   return value
  if isinstance(value,dict):return {k:clean(v) for k,v in value.items() if k in ['provider_name','size_bytes','quality','resolution','width','height','codec','video_codec','audio_codec','audio_languages','audio','language','languages','channels','hdr','dolby_vision','display_name','label','bit_depth','container','size','seeders','title','subtitle','badges']}
  if isinstance(value,list):return [clean(v) for v in value if isinstance(v,(str,int,float,bool,list,dict))]
  if isinstance(value,(int,float,bool)) or value is None:return value
  return None
 def api(path,body=None,method=None,cleanup=False):
  remaining=deadline-time.monotonic();timeout=min(12,remaining) if not cleanup else 2
  if timeout<=0:raise TimeoutError('shared batch deadline')
  request=urllib.request.Request(args.origin+path,data=json.dumps(body).encode() if body is not None else None,headers={'Content-Type':'application/json','Origin':args.origin,'Authorization':'Bearer '+token},method=method)
  tick=time.monotonic()
  try:
   with urllib.request.urlopen(request,timeout=max(.1,timeout)) as response:return response.status,json.load(response),round(time.monotonic()-tick,3)
  except urllib.error.HTTPError as error:
   try:data=json.load(error)
   except Exception:data={}
   return error.code,data,round(time.monotonic()-tick,3)
 movie=json.loads((args.seed_dir/'movie.json').read_text())['cards'];home=json.loads((args.seed_dir/'home.json').read_text());homecards=[card for row in home.get('rows',[]) for card in row.get('cards',[])]
 selected=[];seen=set()
 for card in [card for card in homecards if card.get('identity',{}).get('episode')]+movie:
  ident=card.get('identity',{});key=(ident.get('kind'),ident.get('id'))
  if key in seen or len(selected)>=12:continue
  seen.add(key);selected.append(card)
 seeds=[]
 for card in selected:
  ident=card['identity'];body={'type':ident['kind'],'id':ident['id'],'name':card['title'],'metadata_provider_id':card.get('provider_id','simkl')}
  if card.get('metadata',{}).get('year'):body['year']=card['metadata']['year']
  parent=card.get('parent_identity');episode=ident.get('episode')
  if parent:body['series_id']=parent['id']
  if episode:body.update(season=episode['season'],episode=episode['episode'])
  for external in (parent or ident).get('ids',[]):
   if external['namespace'] in ['imdb','tmdb']:body[external['namespace']+'_id']=external['value']
  if card.get('metadata_origin'):body.update(metadata_origin=card['metadata_origin'],card={k:card[k] for k in ['identity','provider_id','parent_identity','metadata_origin'] if k in card})
  seeds.append({'title':card['title'],'category':card.get('category'),'metadata_seed':'fresh real Simkl search/Home fetched in this validation session','request':body})
 save(args.output/'seeds.json',seeds)
 inventory={}
 try:
  status,data,seconds=api('/api/v2/addons');save(raw/'addon-inventory.json',{'status':status,'seconds':seconds,'body':data})
  for row in data.get('items',[]):inventory['addon:'+str(row['id'])]=clean(row.get('name',''))
 except Exception as error:inventory={'inventory_error':type(error).__name__}
 allowed=['name','title','description','filename','quality','codec','audio_languages','audio_language','audio_language_status','presentation','resolution','video_codec','audio_codec','container','source_name','type','size','size_bytes','seeders']
 def worker(index,seed):
  result={'title':seed['title'],'category':seed['category'],'metadata_seed':seed['metadata_seed'],'events':[],'polls':[]};jid=None;cursor=0;tick=time.monotonic()
  try:
   status,data,seconds=api('/api/v2/streams',seed['request']);save(raw/(str(index)+'-start.json'),{'status':status,'seconds':seconds,'body':data});result.update(start_status=status,start_seconds=seconds)
   if status!=200 or not isinstance(data.get('id'),str):result['start_error']=clean(data.get('error',data.get('error_code','request failed')));return result
   jid=data['id'];poll=0
   while time.monotonic()<deadline-2:
    status,data,seconds=api('/api/v2/streams/'+jid+'?after='+str(cursor)+'&wait_ms=3000');save(raw/(str(index)+'-poll-'+str(poll)+'.json'),{'status':status,'seconds':seconds,'body':data});poll+=1
    result['polls'].append({'status':status,'seconds':seconds,'new_events':len(data.get('events',[])),'done':data.get('done',False),'elapsed_seconds':round(time.monotonic()-started,3)})
    if status!=200:result['poll_error']=clean(data.get('error',data.get('error_code','poll failed')));break
    for event in data.get('events',[]):
     source=event.get('source','unknown');eventout={'seq':event.get('seq'),'provider':inventory.get(source,clean(source)),'source':clean(source),'elapsed_seconds':round(time.monotonic()-started,3),'streams':[]}
     for k in ['error','error_code']:
      if k in event:eventout[k]=clean(event[k])
     for stream in event.get('streams',[]):
      item={k:clean(stream[k]) for k in allowed if k in stream}
      hints=stream.get('behaviorHints')
      if isinstance(hints,dict) and isinstance(hints.get('filename'),str):item['behaviorHints']={'filename':clean(hints['filename'])}
      eventout['streams'].append(item)
     eventout['stream_count']=len(eventout['streams']);result['events'].append(eventout)
    cursor+=len(data.get('events',[]))
    if data.get('done'):result['done']=True;break
   if not result.get('done'):result['timed_out_or_interrupted']=True
  except Exception as error:result['client_error']=type(error).__name__
  finally:
   if jid:
    try:
     status,data,seconds=api('/api/v2/streams/'+jid,method='DELETE',cleanup=True);save(raw/(str(index)+'-cleanup.json'),{'status':status,'body':data});result['owned_job_cleanup_status']=status
    except Exception as error:result['cleanup_error']=type(error).__name__
   result['seconds']=round(time.monotonic()-tick,3)
  return result
 with concurrent.futures.ThreadPoolExecutor(max_workers=12,thread_name_prefix='real-source-batch') as pool:results=list(pool.map(lambda pair:worker(*pair),enumerate(seeds)))
 output={'provenance':'Fresh requests to real configured source providers via authenticated canonical backend; fresh real Simkl metadata seeds; no mock/synthetic source responses','batch_budget_seconds':args.budget,'elapsed_seconds':round(time.monotonic()-started,3),'playback_or_progress_mutations':False,'provider_names':inventory,'results':results}
 save(args.output/'sanitized.json',output)
 summary={'elapsed_seconds':output['elapsed_seconds'],'titles':len(results),'done':sum(bool(x.get('done')) for x in results),'streams':sum(e['stream_count'] for x in results for e in x['events']),'events':sum(len(x['events']) for x in results),'provider_counts':{},'errors':[],'owned_job_cleanup_statuses':[x.get('owned_job_cleanup_status') for x in results]}
 for item in results:
  for event in item['events']:
   key=event['provider'];summary['provider_counts'][key]=summary['provider_counts'].get(key,0)+event['stream_count']
   if event.get('error') or event.get('error_code'):summary['errors'].append({'title':item['title'],'provider':key,'error_code':event.get('error_code'),'error':event.get('error')})
  for key in ['start_error','poll_error','client_error','cleanup_error']:
   if item.get(key):summary['errors'].append({'title':item['title'],key:item[key]})
 save(args.output/'summary.json',summary);print(json.dumps(summary,ensure_ascii=False),flush=True)
if __name__=='__main__':main()
