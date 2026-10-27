#!/usr/bin/env python3
"""Captures account responses with a throwaway account's cookies and writes SCRUBBED fixtures next to this script.

    VELA_TEST_COOKIES=/path/to/cookies.txt python3 capture_fixtures.py

Keeps every key (so the structure is the real one) and numbers/booleans, replaces every string value:
  accountName -> "Test Person", email -> "test.person@example.com", channelId/browseId UC.. -> "UCscrubbed<n>",
  title/text -> "Channel <n>" / "Text <n>", urls -> "https://example.invalid/<n>.png", tokens/params -> "scrubbed", all else -> "x".
Then FAILS (exit 1, nothing written) if any cookie value, the account's e-mail, name or channel id, or visitor data appears.
Never prints a cookie value or response text.
"""
import hashlib, json, os, re, sys, time, urllib.request

path = os.environ.get('VELA_TEST_COOKIES') or os.path.expanduser('~/.vela-test/cookies.txt')
jar = {}
for l in open(path):
    if (l.startswith('#') and not l.startswith('#HttpOnly_')) or not l.strip(): continue
    p = l.rstrip('\n').split('\t')
    if len(p) >= 7: jar[p[5]] = p[6]
sapisid = jar.get('SAPISID') or jar.get('__Secure-3PAPISID')
cookie = '; '.join(f'{k}={v}' for k, v in jar.items())
def post(endpoint, body):
    ts = int(time.time()); o = 'https://www.youtube.com'
    h = {'Content-Type': 'application/json', 'Cookie': cookie, 'Authorization': f'SAPISIDHASH {ts}_{hashlib.sha1(f"{ts} {sapisid} {o}".encode()).hexdigest()}',
         'X-Goog-AuthUser': '0', 'Origin': o, 'X-Origin': o, 'Referer': o + '/', 'User-Agent': 'Mozilla/5.0 (X11; Linux x86_64; rv:140.0) Gecko/20100101 Firefox/140.0'}
    ctx = {'client': {'clientName': 'WEB', 'clientVersion': '2.20250101.00.00', 'hl': 'en', 'gl': 'US'}}
    req = urllib.request.Request(f'{o}/youtubei/v1/{endpoint}?prettyPrint=false', json.dumps({'context': ctx, **body}).encode(), h)
    return json.loads(urllib.request.urlopen(req, timeout=30).read())

counter = [0]
def n(): counter[0] += 1; return counter[0]
def scrub(e, key='', parent=''):
    if isinstance(e, dict): return {k: scrub(v, k, key) for k, v in e.items()}
    if isinstance(e, list): return [scrub(v, key, parent) for v in e]
    if isinstance(e, str):
        k = key.lower()
        if k == 'channelid' or (k == 'browseid' and e.startswith('UC')): return f'UCscrubbed{n():012d}'
        if k == 'email': return 'test.person@example.com'
        if k == 'simpletext' and parent.lower() == 'email': return 'test.person@example.com'
        if k == 'simpletext' and parent.lower() == 'accountname': return 'Test Person'
        if k in ('url', 'iconurl') or e.startswith(('http://', 'https://', '//')): return f'https://example.invalid/{n()}.png'
        if k in ('text',): return f'Text {n()}'
        if k in ('token', 'params', 'trackingparams', 'clicktrackingparams', 'continuation', 'visitordata', 'channelcreationtoken', 'key'): return 'scrubbed'
        if k == 'simpletext': return f'Label {n()}'
        return 'x'
    return e

def scrub_named(j, name):  # account name / title get readable values so parsers have something to find
    def walk(e, parent=''):
        if isinstance(e, dict):
            for k, v in e.items():
                if k == 'accountName' and isinstance(v, dict): v['simpleText'] = 'Test Person'
                if k == 'title' and parent == 'gridChannelRenderer' and isinstance(v, dict): v.pop('runs', None); v['simpleText'] = f'Channel {n()}'
                walk(v, k)
        elif isinstance(e, list):
            for v in e: walk(v, parent)
    walk(j); return j

here = os.path.dirname(os.path.abspath(__file__))
raw = {'account_menu.json': post('account/account_menu', {}), 'subscriptions.json': post('browse', {'browseId': 'FEsubscriptions'}),
       'history.json': post('browse', {'browseId': 'FEhistory'})}
secrets = set(v for v in jar.values() if len(v) >= 8)
text_all = json.dumps(raw)
for pattern in (r'activeAccountHeaderRenderer.*?email.*?"simpleText": ?"([^"]+)"',):
    m = re.search(pattern, text_all)
    if m: secrets.add(m.group(1))
for m in re.finditer(r'"visitorData": ?"([^"]+)"', text_all): secrets.add(m.group(1))
for m in re.finditer(r'"accountName": ?\{"simpleText": ?"([^"]+)"', text_all): secrets.add(m.group(1))
for m in re.finditer(r'"channelId": ?"(UC[^"]+)"', text_all): secrets.add(m.group(1))
out = {}
for name, j in raw.items():
    s = scrub_named(scrub(j), name); counter[0] = 0
    out[name] = json.dumps(s, separators=(',', ':'), sort_keys=True)
# grid channels were renamed by scrub_named after scrub turned titles into "x"; fine either way. Now the safety check:
for name, text in out.items():
    for secret in secrets:
        if secret and secret in text:
            print(f'FAIL: a secret-looking value survived scrubbing in {name}; nothing written', file=sys.stderr); sys.exit(1)
    if re.search(r'(SAPISIDHASH|__Secure-[13]PSID|"SID"|@gmail\.com|@googlemail\.com)', text):
        print(f'FAIL: secret-looking text in {name}; nothing written', file=sys.stderr); sys.exit(1)
for name, text in out.items(): open(os.path.join(here, name), 'w').write(text)
print('wrote', ', '.join(f'{k} ({len(v)} bytes)' for k, v in out.items()))
