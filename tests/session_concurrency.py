"""PostgreSQL HTTP concurrency regression test. Creates one disposable test account.
Run with a dedicated backend instance: python3 tests/session_concurrency.py http://localhost:8082
"""
import concurrent.futures
import json
import sys
import threading
import urllib.request
import urllib.error
import uuid
base=sys.argv[1] if len(sys.argv)>1 else 'http://localhost:8080'
def call(path, body=None, token=None, method='POST'):
 headers={'Content-Type':'application/json'}
 if token:headers['Authorization']='Bearer '+token
 req=urllib.request.Request(base+'/api'+path,data=json.dumps(body).encode() if body is not None else None,headers=headers,method=method)
 try:res=urllib.request.urlopen(req,timeout=20)
 except urllib.error.HTTPError as e:res=e
 raw=res.read()
 return res.status,json.loads(raw) if raw else None
email=uuid.uuid4().hex+'@example.com'
password=uuid.uuid4().hex
status,auth=call('/auth/register',{'email':email,'password':password,'name':'Concurrent test'})
assert status==200,status
barrier=threading.Barrier(2)
def rotate():
 barrier.wait()
 return call('/auth/refresh',{'refreshToken':auth['refreshToken']})
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:
 results=list(executor.map(lambda _:rotate(),range(2)))
assert sorted(s for s,_ in results)==[200,401],results
winner=next(body for status,body in results if status==200)
assert call('/me',token=winner['accessToken'],method='GET')[0]==401,'Replay must revoke even the winning session JWT'
assert call('/auth/refresh',{'refreshToken':winner['refreshToken']})[0]==401
status,fresh=call('/auth/login',{'email':email,'password':password})
assert status==200
assert call('/me',token=fresh['accessToken'],method='GET')[0]==200
assert call('/auth/logout',{'refreshToken':fresh['refreshToken']})[0]==200
assert call('/me',token=fresh['accessToken'],method='GET')[0]==401
print('PASS: concurrent refresh single winner, committed replay revocation, fresh login and immediate logout')
