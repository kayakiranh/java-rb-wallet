"""Real HTTP + Oracle Free integration tests. Run against a fresh, disposable Compose test environment."""
import concurrent.futures
import json
import os
import time
import urllib.error
import urllib.request
import uuid
import subprocess

BASE = os.getenv('WALLET_URL', 'http://localhost:5080')
FAKE = os.getenv('FAKE_URL', 'http://localhost:5081')
KEY = os.environ['FAKE_API_KEY']

def call(method, path, body=None, token=None, base=BASE, headers=None):
    h = {'Content-Type': 'application/json', **(headers or {})}
    if token:
        h['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request(base + path, data=json.dumps(body).encode() if body is not None else None, headers=h, method=method)
    try:
        with urllib.request.urlopen(req, timeout=20) as response:
            raw = response.read()
            return response.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as e:
        raw = e.read()
        return e.code, json.loads(raw) if raw else None

def ok(method, path, body=None, token=None, expected=200):
    status, data = call(method, path, body, token)
    assert status == expected, (path, status, data)
    return data

def new_key():
    return str(uuid.uuid4())

def wallet(token):
    return ok('GET', '/api/wallet', token=token)

def user(label):
    return ok('POST', '/api/auth/register', {'name': label, 'email': label + new_key() + '@example.test', 'password': 'DemoPassword123!'})['accessToken']

for _ in range(90):
    try:
        if call('GET', '/health')[0] == 200 and call('GET', '/health', base=FAKE)[0] == 200:
            break
    except (OSError, urllib.error.URLError):
        pass
    time.sleep(2)
else:
    raise AssertionError('APIs did not become ready')

assert call('GET', '/api/wallet')[0] == 401
assert call('GET', '/api/notification/messages', base=FAKE)[0] == 401
alice, bob = user('Alice'), user('Bob')
bob_number = wallet(bob)['number']
top = {'requestKey': new_key(), 'amount': 1000, 'bankAccount': 'DEMO-ACCOUNT'}
assert ok('POST', '/api/wallet/top-ups', top, alice)['status'] == 'Completed'
ok('POST', '/api/wallet/top-ups', top, alice)
assert wallet(alice)['balance'] == 1000
ok('POST', '/api/wallet/top-ups', dict(top, amount=2000), alice, expected=409)
transfer = {'requestKey': new_key(), 'amount': 200, 'recipientWalletNumber': bob_number}
ok('POST', '/api/transfers', transfer, alice)
ok('POST', '/api/transfers', transfer, alice)
bill = {'requestKey': new_key(), 'subscriberNumber': '10001'}
ok('POST', '/api/bills/payments', bill, alice)
ok('POST', '/api/bills/payments', bill, alice)
assert wallet(alice)['balance'] == 650
assert wallet(bob)['balance'] == 200
ok('POST', '/api/transfers', dict(transfer, requestKey=new_key(), amount=651), alice, expected=400)

# A completed provider payment times out; reserved money cannot be spent.
pending = ok('POST', '/api/bills/payments', {'requestKey': new_key(), 'subscriberNumber': 'TIMEOUT'}, alice, expected=202)
assert pending['status'] == 'Pending'
assert wallet(alice)['reserved'] == 150
assert wallet(alice)['available'] == 500
ok('POST', '/api/transfers', dict(transfer, requestKey=new_key(), amount=501), alice, expected=400)
ok('POST', '/api/transactions/' + pending['id'] + '/resume', {}, bob, expected=404)
resolved = ok('POST', '/api/transactions/' + pending['id'] + '/resume', {}, alice)
assert resolved['status'] == 'Completed'
ok('POST', '/api/transactions/' + pending['id'] + '/resume', {}, alice)
assert wallet(alice)['balance'] == 500
assert wallet(alice)['reserved'] == 0

# Exact provider rejection releases the reservation.
rejected = ok('POST', '/api/bills/payments', {'requestKey': new_key(), 'subscriberNumber': 'REJECT'}, alice)
assert rejected['status'] == 'Rejected'
assert wallet(alice)['balance'] == 500 and wallet(alice)['reserved'] == 0

# Concurrent distinct transfers compete for insufficient funds. Retry conflicts with original key.
requests = [dict(transfer, requestKey=new_key(), amount=400) for _ in range(2)]
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
    results = list(pool.map(lambda r: call('POST', '/api/transfers', r, alice), requests))
for i, (status, data) in enumerate(results):
    if status == 409:
        results[i] = call('POST', '/api/transfers', requests[i], alice)
assert sum(status == 200 and data['status'] == 'Completed' for status, data in results) == 1, results
assert wallet(alice)['balance'] == 100
assert wallet(bob)['balance'] == 600

# Concurrent same-key credits must not be applied twice.
credit = {'requestKey': new_key(), 'amount': 50, 'bankAccount': 'DEMO-ACCOUNT'}
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
    results = list(pool.map(lambda _: call('POST', '/api/wallet/top-ups', credit, alice), range(4)))
assert all(status in (200, 409) for status, _ in results), results
ok('POST', '/api/wallet/top-ups', credit, alice)
assert wallet(alice)['balance'] == 150

# Fraud refusal leaves both balances untouched.
ok('POST', '/api/wallet/top-ups', dict(credit, requestKey=new_key(), amount=20000), alice)
fraud = ok('POST', '/api/transfers', dict(transfer, requestKey=new_key(), amount=10001), alice)
assert fraud['status'] == 'Rejected'
assert wallet(alice)['balance'] == 20150
assert wallet(bob)['balance'] == 600
def eventually(check, seconds=60):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        result = check()
        if result:
            return result
        time.sleep(1)
    raise AssertionError('Condition did not become true')

def messages():
    status, data = call('GET', '/api/notification/messages', base=FAKE, headers={'X-Api-Key': KEY})
    assert status == 200
    return data

eventually(lambda: len(messages()) >= 8)
all_messages = messages()
assert len({(m['operationId'], m['userId']) for m in all_messages}) == len(all_messages)
assert any(t['direction'] == 'Incoming' for t in ok('GET', '/api/transactions', token=bob))
# Wait for old messages, then exercise a transient failure on one new notification.
eventually(lambda: all(n['deliveredAt'] for n in ok('GET', '/api/notifications', token=alice)))
eventually(lambda: all(n['deliveredAt'] for n in ok('GET', '/api/notifications', token=bob)))
assert call('PUT', '/api/scenarios/notification-failures', {'count': 1}, base=FAKE, headers={'X-Api-Key': KEY})[0] == 204
new_credit = ok('POST', '/api/wallet/top-ups', dict(credit, requestKey=new_key(), amount=10), alice)
assert wallet(alice)['balance'] == 20160

def notification_retried():
    entries = ok('GET', '/api/notifications', token=alice)
    return any(n['transactionId'] == new_credit['id'] and n['attempts'] >= 2 and n['deliveredAt'] for n in entries)
eventually(notification_retried)

# Fake and wallet restarts must preserve a provider success and pending DB operation.
auto_body = {'requestKey': new_key(), 'amount': 20, 'bankAccount': 'TIMEOUT'}
auto_pending = ok('POST', '/api/wallet/top-ups', auto_body, alice, expected=202)
assert auto_pending['status'] == 'Pending'
if os.getenv('TEST_RESTART') == '1':
    subprocess.run(['docker', 'compose', 'restart', 'fake', 'wallet'], check=True)
    def ready():
        try:
            return call('GET', '/health/ready')[0] == 200 and call('GET', '/health', base=FAKE)[0] == 200
        except (OSError, urllib.error.URLError):
            return False
    eventually(ready)
eventually(lambda: ok('GET', '/api/transactions/by-request/' + auto_body['requestKey'], token=alice)['status'] == 'Completed')
assert wallet(alice)['balance'] == 20180
assert ok('GET', '/api/bills/10001', token=alice)['paid']
ok('POST', '/api/wallet/top-ups', auto_body, alice)
assert wallet(alice)['balance'] == 20180
ok('GET', '/api/transactions/by-request/' + auto_body['requestKey'], token=bob, expected=404)
print('PASS: automatic recovery, outbox retry, incoming direction and cross-user isolation.')
if os.getenv('TEST_RESTART') == '1':
    print('PASS: durable state and recovery after fake/wallet container restart.')
print('PASS: registration, authorization, 650 TRY journey, replay/conflict, timeout recovery, reservation, rejection, concurrent funds, concurrent idempotency, fraud and notifications.')
