"""HTTP Coins test: requires dev + TEST_PAYMENTS_ENABLED=true and a disposable DB.
python3 tests/coins_smoke.py http://localhost:8081 cognify_coins_test
Creates test accounts, one active package and one pricing rule. No real payment.
"""
import concurrent.futures,json,subprocess,sys,urllib.request,urllib.error,uuid
base=sys.argv[1] if len(sys.argv)>1 else 'http://localhost:8081'
db=sys.argv[2] if len(sys.argv)>2 else 'cognify_coins_test'
def call(method,path,body=None,token=None,expected=200):
 headers={'Content-Type':'application/json'}
 if token:headers['Authorization']='Bearer '+token
 req=urllib.request.Request(base+'/api'+path,data=json.dumps(body).encode() if body is not None else None,headers=headers,method=method)
 try:r=urllib.request.urlopen(req,timeout=20)
 except urllib.error.HTTPError as e:r=e
 raw=r.read();assert r.status==expected,(path,r.status,raw.decode())
 return json.loads(raw) if raw else None
assert call('GET','/public/payment-mode')['test'] is True,'Run only on explicitly enabled test payments'
def register():
 email=uuid.uuid4().hex+'@example.com';password=uuid.uuid4().hex
 return email,password,call('POST','/auth/register',{'email':email,'password':password,'name':'Coins HTTP test'})['accessToken']
email,password,_=register()
subprocess.run(['docker','exec','cognify-postgres','psql','-U','postgres','-d',db,'-v','ON_ERROR_STOP=1','-c',f"UPDATE accounts SET role='ADMIN' WHERE email='{email}'"],check=True,capture_output=True)
admin=call('POST','/auth/login',{'email':email,'password':password})['accessToken']
user_email,_,user=register();_,_,other=register()
pack=call('POST','/admin/coin-packages',{'name':'HTTP test package','coins':200,'amountMinor':100000,'currency':'AMD','active':True,'version':0},admin)
request={'packageId':pack['id'],'requestKey':str(uuid.uuid4())}
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
 results=list(pool.map(lambda _:call('POST','/payments',request,user),range(2)))
assert results[0]['id']==results[1]['id']
assert results[0]['test'] is True
assert call('GET','/wallet',token=user)=={'balance':200,'reserved':0,'available':200}
assert len(call('GET','/payments',token=user))==1
assert len(call('GET','/wallet/transactions',token=user))==1
assert call('GET','/wallet/transactions',token=other)==[]
target=call('GET','/admin/wallets/lookup?email='+user_email,token=admin)['id']
adjust={'amount':-50,'reason':'HTTP regression test','requestKey':str(uuid.uuid4())}
call('POST','/admin/wallets/'+target+'/adjustments',adjust,user,403)
call('POST','/admin/wallets/'+target+'/adjustments',{**adjust,'reason':''},admin,400)
a=call('POST','/admin/wallets/'+target+'/adjustments',adjust,admin)
b=call('POST','/admin/wallets/'+target+'/adjustments',adjust,admin)
assert a['id']==b['id']
call('POST','/admin/wallets/'+target+'/adjustments',{**adjust,'amount':-40},admin,409)
assert call('GET','/wallet',token=user)['available']==150
assert call('GET','/me',token=user)['balance']==150
op='HTTP_'+uuid.uuid4().hex.upper()
call('PUT','/admin/pricing/'+op,{'unit':'PAGE','unitCost':10,'active':True,'version':0},admin)
assert call('GET','/pricing/quote?operation='+op+'&units=3',token=user)['coins']==30
assert any(x['action']=='COINS_ADJUSTMENT' and x['target']==target for x in call('GET','/admin/audit',token=admin))
call('POST','/payments/webhook',{'paymentId':results[0]['id'],'status':'success'},user,404)
print('PASS: real HTTP test payment idempotency, wallet isolation, administrator-only adjustments, reasons, replay conflicts, quotes and audit')
