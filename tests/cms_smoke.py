"""Live CMS regression test on a disposable PostgreSQL database.
Usage: python3 tests/cms_smoke.py [http://localhost:8080] [cognify_phase2_fresh]
Uses cognify-postgres Docker container; creates a disposable ADMIN account.
Restores edited public content in finally, retains audit entries and test account.
"""
import concurrent.futures,json,subprocess,sys,threading,urllib.request,urllib.error,uuid
base=sys.argv[1] if len(sys.argv)>1 else 'http://localhost:8080'
database=sys.argv[2] if len(sys.argv)>2 else 'cognify_phase2_fresh'
def call(method,path,body=None,token=None):
 headers={'Content-Type':'application/json'}
 if token:headers['Authorization']='Bearer '+token
 req=urllib.request.Request(base+'/api'+path,data=json.dumps(body).encode() if body is not None else None,headers=headers,method=method)
 try:res=urllib.request.urlopen(req,timeout=20)
 except urllib.error.HTTPError as e:res=e
 raw=res.read();return res.status,json.loads(raw) if raw else None
email='cms-'+uuid.uuid4().hex+'@example.com';password=uuid.uuid4().hex
status,user=call('POST','/auth/register',{'email':email,'password':password,'name':'CMS test'});assert status==200
assert call('GET','/admin/blocks',token=user['accessToken'])[0]==403
subprocess.run(['docker','exec','cognify-postgres','psql','-U','postgres','-d',database,'-v','ON_ERROR_STOP=1','-c',f"UPDATE accounts SET role='ADMIN' WHERE email='{email}'"],check=True,capture_output=True)
status,auth=call('POST','/auth/login',{'email':email,'password':password});assert status==200
token=auth['accessToken'];original=next(b for b in call('GET','/admin/blocks',token=token)[1] if b['key']=='faq')
try:
 hidden={**original,'visible':False};assert call('PUT','/admin/blocks/faq',hidden,token)[0]==200
 assert all(b['key']!='faq' for b in call('GET','/public/blocks')[1])
 current=next(b for b in call('GET','/admin/blocks',token=token)[1] if b['key']=='faq')
 barrier=threading.Barrier(2)
 def save(n):
  barrier.wait();return call('PUT','/admin/blocks/faq',{**current,'title':'Concurrent '+str(n),'visible':True},token)
 with concurrent.futures.ThreadPoolExecutor(max_workers=2) as ex:results=list(ex.map(save,range(2)))
 assert sorted(r[0] for r in results)==[200,409],results
 winner=next(r[1] for r in results if r[0]==200)
 public=next(b for b in call('GET','/public/blocks')[1] if b['key']=='faq');assert public['title']==winner['title']
 log=call('GET','/admin/audit',token=token)[1]
 actor=json.loads(subprocess.check_output(['docker','exec','cognify-postgres','psql','-U','postgres','-d',database,'-Atc',f"SELECT to_json(id) FROM accounts WHERE email='{email}'"],text=True))
 assert len([a for a in log if a['target']=='faq' and a['actorId']==actor])==2
 assert call('PUT','/admin/content/logoUrl',{'value':'javascript:alert(1)'},token)[0]==400
 print('PASS: PostgreSQL CMS visibility, one concurrent winner, matching audit entries, public rendering data, USER denial, unsafe URL rejection')
finally:
 current=next(b for b in call('GET','/admin/blocks',token=token)[1] if b['key']=='faq')
 assert call('PUT','/admin/blocks/faq',{**original,'version':current['version']},token)[0]==200
