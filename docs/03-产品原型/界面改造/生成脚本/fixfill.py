import json,subprocess,time
# op 偶尔返回空输出（连接抖动），读画布失败时重试
for i in range(4):
    out=subprocess.run(['op','get','--depth','30'],capture_output=True,text=True,encoding='utf-8').stdout
    try:
        d=json.loads(out); break
    except ValueError:
        if i==3: raise
        time.sleep(3)
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
