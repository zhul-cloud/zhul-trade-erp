"""用法：在本目录运行 python build.py。生成全部原型：清空画布 → 跑生成脚本 → 删除临时节点 → 还原颜色 → 导出 PNG → 保存 → 校验 → 停止。
任何一步失败都停下，不删旧图；导出全部成功后才替换目录里的图。"""
import json, os, subprocess, sys, time, shutil

HERE = os.path.dirname(os.path.abspath(__file__))
D = os.path.dirname(HERE).replace(os.sep, '/')
OP = D + '/界面改造样板.op'
TMP = os.path.join(HERE, 'export-tmp')
SCRIPTS = ['销售订单列表.js', '销售订单详情.js', '登记到账.js', '工作台.js', '主题色对比.js', '外壳.js', '公共组件.js']
NAMES = {'A1 ': 'A1-销售订单列表-浅色-1440', 'A2 ': 'A2-销售订单列表-深色-1440', 'A3 ': 'A3-销售订单列表-浅色-1366',
         'A1d': 'A1d-表格右滚-浅色', 'A2d': 'A2d-表格右滚-深色', 'A1e': 'A1e-表格滚到最右-浅色',
         'A1b': 'A1b-关键词帮助悬停-浅色', 'A2b': 'A2b-关键词帮助悬停-深色', 'A1c': 'A1c-快捷筛选与操作悬停-浅色', 'A2c': 'A2c-快捷筛选与操作悬停-深色',
         'B1 ': 'B1-销售订单详情-浅色-1440', 'B2 ': 'B2-销售订单详情-深色-1440', 'B1c': 'B1c-批量操作与更多菜单-浅色', 'B2c': 'B2c-批量操作与更多菜单-深色',
         'B1d': 'B1d-成本明细滚到最右-浅色', 'B1e': 'B1e-状态组合-浅色',
         'C1 ': 'C1-登记到账-浅色', 'C2 ': 'C2-登记到账-深色', 'C1b': 'C1b-登记到账状态-浅色',
         'D1 ': 'D1-工作台-业务员-浅色', 'D2 ': 'D2-工作台-业务员-深色', 'D1b': 'D1b-工作台状态-浅色', 'D1c': 'D1c-涨跌颜色-浅色',
         'E1 ': 'E1-主题色对比-浅色', 'E2 ': 'E2-主题色对比-深色',
         'F1 ': 'F1-外壳-浅色', 'F2 ': 'F2-外壳-深色', 'F1b': 'F1b-收起侧栏与主题设置-浅色', 'F2b': 'F2b-收起侧栏与主题设置-深色', 'F1d': 'F1d-外壳状态-浅色',
         'G1 ': 'G1-公共组件-浅色', 'G2 ': 'G2-公共组件-深色',
         'H1 ': 'H1-工作台-老板-浅色', 'H2 ': 'H2-工作台-老板-深色', 'H3 ': 'H3-工作台-业务员-浅色',
         'H1b': 'H1b-风险提醒两种状态-浅色', 'H4 ': 'H4-工作台-业务员-深色'}


def op(*args, retries=4):
    for i in range(retries):
        r = subprocess.run(['op', *args], capture_output=True, text=True, encoding='utf-8', cwd=HERE)
        if r.returncode == 0:
            return r.stdout
        time.sleep(2)
    sys.exit(f'op {args[0]} 失败：{r.stderr.strip()[:300]}')


def roots():
    return json.loads(op('get', '--depth', '0'))['nodes']


def main():
    for attempt in range(2):
        if json.loads(op('status')).get('running'):
            break
        if attempt:
            subprocess.run(['op', 'stop'], capture_output=True, cwd=HERE)
            time.sleep(3)
        subprocess.Popen(['op', 'start', '--headless', '--file', OP], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, cwd=HERE)
        for _ in range(30):
            time.sleep(2)
            if json.loads(op('status')).get('running'):
                break
    if not json.loads(op('status')).get('running'):
        sys.exit('OpenPencil 没有启动')
    for n in roots():
        op('delete', n['id'])
    for s in SCRIPTS:
        op('design', '@' + s)
    for n in roots():
        if n.get('name') == 'TRASH':
            op('delete', n['id'])
    # OpenPencil 偶尔在还原后又把低对比度颜色改深，反复还原到没有需要修的节点为止
    for _ in range(5):
        out = subprocess.run([sys.executable, 'fixfill.py'], check=True, cwd=HERE, capture_output=True, text=True, encoding='utf-8').stdout
        print(out.strip())
        if out.strip() == 'fixed 0':
            break
    nodes = roots()
    plan = []
    for n in nodes:
        key = n['name'][:3]
        if key not in NAMES:
            sys.exit(f'未知画板：{n["name"]}')
        plan.append((n['id'], NAMES[key]))
    if len(plan) != len(NAMES):
        sys.exit(f'画板数量不对：{len(plan)} / {len(NAMES)}')
    shutil.rmtree(TMP, ignore_errors=True)
    os.makedirs(TMP)
    for nid, f in plan:
        out = os.path.join(TMP, f + '.png')
        op('export', '--item', nid, '--output', out)
        if not os.path.getsize(out):
            sys.exit('导出为空：' + f)
    for f in os.listdir(D):
        if f.endswith('.png') and f[:1] in 'ABCDEFGH':
            os.remove(os.path.join(D, f))
    for f in os.listdir(TMP):
        shutil.move(os.path.join(TMP, f), os.path.join(D, f))
    shutil.rmtree(TMP)
    op('save', OP)
    op('stop')
    subprocess.run([sys.executable, 'checkop.py', OP], check=True, cwd=HERE)
    print('done', len(plan), 'frames')


main()
