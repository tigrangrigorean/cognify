"""Run against a local Cognify backend with PostgreSQL. Creates two test accounts.
Usage: python3 tests/api_smoke.py [http://localhost:8080]
"""
import json
import sys
import uuid
import urllib.request
import urllib.error

base = sys.argv[1] if len(sys.argv) > 1 else 'http://localhost:8080'

def call(method, path, body=None, token=None, expected=200):
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    request = urllib.request.Request(base + '/api' + path, data=json.dumps(body).encode() if body is not None else None, headers=headers, method=method)
    try:
        response = urllib.request.urlopen(request, timeout=10)
    except urllib.error.HTTPError as e:
        response = e
    assert response.status == expected, (method, path, response.status, response.read().decode())
    raw = response.read()
    return json.loads(raw) if raw else None

suffix = uuid.uuid4().hex
email = 'smoke-' + suffix + '@example.com'
password = uuid.uuid4().hex
first = call('POST', '/auth/register', {'email': email, 'password': password, 'name': 'Անի'})
second = call('POST', '/auth/register', {'email': 'other-' + suffix + '@example.com', 'password': password, 'name': 'Արամ'})
a, b = first['accessToken'], second['accessToken']
call('POST', '/auth/login', {'email': email, 'password': password})
assert call('GET', '/me', token=a)['balance'] == 0
call('GET', '/documents', expected=401)
body = {'title': 'Փորձնական աշխատանք', 'kind': 'ESSAY', 'content': 'Առաջին տարբերակ', 'version': 0}
doc = call('POST', '/documents', body, a)
assert call('GET', '/documents', token=b) == []
call('PUT', '/documents/' + doc['id'], body, b, 404)
body['content'] = 'Խմբագրված բովանդակություն'
updated = call('PUT', '/documents/' + doc['id'], body, a)
assert updated['version'] > doc['version']
call('PUT', '/documents/' + doc['id'], body, a, 409)
assert call('GET', '/documents', token=a)[0]['content'] == body['content']
call('PUT', '/admin/content/headline', {'value': 'Denied'}, a, 403)
assert call('GET', '/public/content')['headline']
rotated = call('POST', '/auth/refresh', {'refreshToken': first['refreshToken']})
call('POST', '/auth/refresh', {'refreshToken': first['refreshToken']}, expected=401)
call('POST', '/auth/logout', {'refreshToken': rotated['refreshToken']})
call('POST', '/auth/refresh', {'refreshToken': rotated['refreshToken']}, expected=401)
call('GET', '/me', token=a, expected=401)
a = call('POST', '/auth/login', {'email': email, 'password': password})['accessToken']
call('DELETE', '/documents/' + doc['id'], token=a)
assert call('GET', '/documents', token=a) == []
print('PASS: registration, login, zero wallet, CRUD, ownership, stale version, ADMIN denial, CMS, refresh rotation and logout')
