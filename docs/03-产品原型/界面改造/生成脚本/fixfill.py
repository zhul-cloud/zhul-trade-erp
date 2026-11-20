import json,subprocess
d=json.loads(subprocess.run(['op','get','--depth','30'],capture_output=True,text=True,encoding='utf-8').stdout)
todo=[]
def walk(n):
    nm=n.get('name') or ''
    if n.get('type') in ('text','icon_font') and nm.startswith('fill='):
        want=nm[5:]; f=n.get('fill') or []
        if not f or f[0].get('color','').upper()!=want.upper(): todo.append((n['id'],want))
    ch=n.get('children')
    if isinstance(ch,list):
        for c in ch: walk(c)
for n in d['nodes']: walk(n)
for nid,c in todo:
    subprocess.run(['op','update_node',f'node_id={nid}','data='+json.dumps({'fill':[{'type':'solid','color':c}]})],capture_output=True)
print('fixed',len(todo))
