"""按角色导出可访问的页面：口径同 EffectivePermissionResolver + MenuServiceImpl.visibleIds。
用法：python3 snapshot.py <库名> <输出文件>
- reachable：能访问的页面路径（type=2）与按钮权限码（type=3）
- visible：侧边栏能看到的页面（自身与全部上级都没有隐藏）
平台账号的角色只看 role_resource；租户角色取 role_resource 与所在租户套餐的交集；租户管理员只看套餐。"""
import json
import subprocess
import sys

db, out = sys.argv[1], sys.argv[2]


def q(sql):
    r = subprocess.run(['mysql', '-uroot', '-proot', db, '-N', '-B', '-e', sql], capture_output=True, text=True, check=True)
    return [line.split('\t') for line in r.stdout.strip().split('\n') if line]


res = {int(i): dict(pid=int(p), type=int(t), path=pa if pa != 'NULL' else '', perm=pe if pe != 'NULL' else '',
                    hidden=h == '1', name=n)
       for i, p, t, pa, pe, h, n in q("select id, pid, type, ifnull(path,''), ifnull(permission,''), ifnull(is_hidden,0), name from resource")}
packages = {int(i): set(json.loads(m or '[]')) for i, m in q("select id, menu_ids from tenant_package")}
tenant_pkg = {int(t): int(p) for t, p in q("select id, ifnull(package_id,0) from tenant")}
grants = {}
for code, rid in q("select role_code, resource_id from role_resource"):
    grants.setdefault(code, set()).add(int(rid))
roles = q("select code, tenant_id, name from role")


def describe(ids):
    reach, visible = set(), set()
    for i in ids:
        r = res.get(i)
        if not r:
            continue
        if r['type'] == 3 and r['perm']:
            reach.add(r['perm'])
        if r['type'] == 2 and r['path']:
            reach.add(r['path'])
            cur, ok = i, True
            while cur and cur in res:
                if res[cur]['hidden']:
                    ok = False
                    break
                cur = res[cur]['pid']
            if ok:
                visible.add(r['path'])
    return {'reachable': sorted(reach), 'visible': sorted(visible)}


snap = {}
for code, tenant, name in roles:
    tenant = int(tenant)
    ids = set(grants.get(code, set()))
    if tenant > 0:
        ids &= packages.get(tenant_pkg.get(tenant, 0), set())
    snap[f'role:{tenant}:{code}:{name}'] = describe(ids)
for pid, ids in packages.items():
    snap[f'package:{pid}'] = describe(ids)
json.dump(snap, open(out, 'w'), ensure_ascii=False, indent=1, sort_keys=True)
print(db, len(snap), 'entries')
