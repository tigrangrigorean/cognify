// Live integration of the BFF handler and Spring/PostgreSQL, without a browser.
// Creates a disposable account. Usage: node tests/auth-proxy-live.mjs http://localhost:8080
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {handleAuth} from '../lib/auth-proxy.mjs';
const backend=process.argv[2]||'http://localhost:8080';
const origin='https://cognify.test';let cookie='';
async function auth(action,body={}) {
 const request=new Request(origin+'/api/auth/'+action,{method:'POST',headers:{Origin:origin,'Content-Type':'application/json','X-Cognify-CSRF':'1',Cookie:cookie},body:JSON.stringify(body)});
 const response=await handleAuth(request,action,{origin,backend,secure:true});
 const header=response.headers.get('set-cookie');if(header)cookie=header.split(';')[0];
 const text=await response.text();return {status:response.status,body:text?JSON.parse(text):null,header};
}
const email=randomUUID()+'@example.com',password=randomUUID();
let result=await auth('register',{email,password,name:'BFF integration'});
assert.equal(result.status,200);assert.equal(result.body.refreshToken,undefined);assert.match(result.header,/HttpOnly/);assert.match(result.header,/Secure/);
const oldCookie=cookie;
result=await auth('refresh');assert.equal(result.status,200);assert.notEqual(cookie,oldCookie);assert.equal(result.body.refreshToken,undefined);
const token=result.body.accessToken;
assert.equal((await fetch(backend+'/api/me',{headers:{Authorization:'Bearer '+token}})).status,200);
result=await auth('logout');assert.equal(result.status,200);assert.match(result.header,/Max-Age=0/);
assert.equal((await fetch(backend+'/api/me',{headers:{Authorization:'Bearer '+token}})).status,401);
assert.equal((await auth('refresh')).status,401);
assert.equal((await auth('forgot-password',{email})).status,503);
console.log('PASS: live BFF registration, hidden refresh token, cookie rotation, immediate logout, SMTP unavailability');
