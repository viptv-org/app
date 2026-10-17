from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from pathlib import Path
import json,time,threading,os,signal
root=Path(__file__).resolve().parent
lock=threading.Lock();events=[];attempts=0
class Handler(BaseHTTPRequestHandler):
 def do_GET(self):
  global attempts
  with lock:
   if self.path=='/manifest.json':
    attempts+=1;status=503 if attempts==1 else 200
    body={'error':'Synthetic one-shot manifest failure'} if status==503 else {'id':'org.viptv.synthetic.phone.addons','name':'Synthetic phone add-on','version':'1.0.0','types':['movie'],'resources':['catalog','stream'],'catalogs':[{'id':'synthetic-phone','type':'movie','name':'Synthetic catalog'}]}
   elif self.path.startswith('/catalog/'):status=200;body={'metas':[]}
   else:status=404;body={'error':'Synthetic fixture route not found'}
   events.append({'method':'GET','path':self.path,'status':status,'manifest_attempts':attempts,'time':time.time()})
   (root/'server-events.json').write_text(json.dumps({'manifest_attempts':attempts,'events':events},indent=2))
  if self.path=='/manifest.json':time.sleep(2)
  data=json.dumps(body).encode();self.send_response(status);self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(data)));self.end_headers();self.wfile.write(data)
 def log_message(self,*args):pass
server=ThreadingHTTPServer(('127.0.0.1',0),Handler);port=server.server_address[1]
(root/'server.json').write_text(json.dumps({'pid':os.getpid(),'port':port,'url':f'http://127.0.0.1:{port}/manifest.json','binding':'127.0.0.1 only','synthetic_provider':True,'credentials':False},indent=2));print(port,flush=True)
def stop(*args):raise SystemExit(0)
signal.signal(signal.SIGTERM,stop);signal.signal(signal.SIGINT,stop)
try:server.serve_forever()
finally:server.server_close()
