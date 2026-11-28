import json,sys
d=json.load(open(sys.argv[1],encoding='utf-8'))
bad=[];tot=0;near=0
def walk(n):
    global tot,near
    nm=n.get('name') or ''
    f=n.get('fill')
    if isinstance(f,list) and f and isinstance(f[0],dict) and f[0].get('color','').upper()=='#17191D': near+=1
    if nm.startswith('fill='):
        tot+=1
        if not f or f[0].get('color','').upper()!=nm[5:].upper(): bad.append((n.get('id'),nm,f and f[0].get('color')))
    for c in n.get('children') or []: walk(c)
roots=d.get('children') or d.get('nodes') or []
if not roots and 'pages' in d:
    for p in d['pages']: roots+=p.get('children') or []
for r in roots: walk(r)
print('tagged',tot,'mismatch',len(bad),'near-black',near, bad[:5])
