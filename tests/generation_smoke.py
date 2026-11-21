"""Full HTTP generation flow. Requires a disposable PostgreSQL DB, dev + mock AI + TEST payments.
Usage: python3 tests/generation_smoke.py http://localhost:8082 cognify_generation_test
No real AI or bank calls. Never point this at a production database.
"""
import io, json, subprocess, sys, time, urllib.request, urllib.error, uuid, zipfile
base = sys.argv[1]
db = sys.argv[2]
def call(method, path, body=None, token=None, expected=200, binary=False):
    headers = {'Content-Type': 'application/json'}
    if token: headers['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request(base + '/api' + path, data=json.dumps(body).encode() if body is not None else None, headers=headers, method=method)
    try: result = urllib.request.urlopen(req, timeout=20)
    except urllib.error.HTTPError as e: result = e
    raw = result.read()
    assert result.status == expected, (path, result.status, raw[:500])
    return raw if binary else json.loads(raw) if raw else None
assert call('GET', '/public/payment-mode')['test']
email = uuid.uuid4().hex + '@example.com'
password = uuid.uuid4().hex
registered = call('POST', '/auth/register', {'email': email, 'password': password, 'name': 'Generation smoke'})
token = registered['accessToken']
assert call('GET', '/generation/config', token=token)['mock']
subprocess.run(['docker','exec','cognify-postgres','psql','-U','postgres','-d',db,'-v','ON_ERROR_STOP=1','-c',f"UPDATE accounts SET role='ADMIN' WHERE email='{email}'"],check=True,capture_output=True)
token = call('POST','/auth/login',{'email':email,'password':password})['accessToken']
rule = next(r for r in call('GET','/admin/pricing',token=token) if r['operation']=='DOCUMENT_GENERATION')
call('PUT','/admin/pricing/DOCUMENT_GENERATION',{'unit':'PAGE','unitCost':10,'active':True,'version':rule['version']},token)
pack = call('POST','/admin/coin-packages',{'name':'Generation smoke','coins':100,'amountMinor':10000,'currency':'AMD','active':True,'version':0},token)
call('POST','/payments',{'packageId':pack['id'],'requestKey':str(uuid.uuid4())},token)
quote = call('GET','/generation/quote?pages=3',token=token)
request = {'topic':'Հայաստանի տնտեսությունը','kind':'ESSAY','language':'hy','pages':3,'subject':'Տնտեսագիտություն','university':'','requirements':'','acceptedQuote':quote['coins'],'requestKey':str(uuid.uuid4())}
job = call('POST','/generation/jobs',request,token,202)
assert call('POST','/generation/jobs',request,token,202)['id']==job['id']
def until(status):
    deadline = time.monotonic()+45
    while time.monotonic()<deadline:
        current=call('GET','/generation/jobs/'+job['id'],token=token)
        assert current['status']!='FAILED',current
        if current['status']==status:return current
        time.sleep(.5)
    raise AssertionError('Generation timeout')
plan=until('AWAITING_PLAN')
assert call('GET','/wallet',token=token)['reserved']==30
call('PUT','/generation/jobs/'+job['id']+'/plan',{'headings':plan['plan'],'version':plan['version']},token)
finished=until('COMPLETED')
assert finished['chargedCoins']==10
assert call('GET','/wallet',token=token)=={'balance':90,'reserved':0,'available':90}
docpath='/documents/'+finished['documentId']
doc=call('GET',docpath,token=token)
parts=call('GET',docpath+'/sections',token=token)
call('PUT',docpath+'/sections/'+parts[0]['id'],{'title':'Ներածություն','content':'Խմբագրված հայերեն տեքստ','version':doc['version']},token)
assert len(call('GET',docpath+'/versions',token=token))==1
raw=call('GET',docpath+'/export.docx',token=token,binary=True)
with zipfile.ZipFile(io.BytesIO(raw)) as archive:
    xml=archive.read('word/document.xml').decode()
    assert 'Խմբագրված հայերեն տեքստ' in xml and 'ԹԵՍՏԱՅԻՆ ՓԱՍՏԱԹՈՒՂԹ' in xml
other=call('POST','/auth/register',{'email':uuid.uuid4().hex+'@example.com','password':password,'name':'Other'})['accessToken']
call('GET',docpath+'/export.docx',token=other,expected=404,binary=True)
print('PASS: HTTP register → TEST Coins → quote → plan → approval → asynchronous sections → edit/history → DOCX; idempotency and ownership; final balance 90, reserved 0')
