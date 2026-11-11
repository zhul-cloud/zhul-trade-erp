import * as M from './model';

const MENUS = [
  '/dashboard',
  '/sales/orders',
  '/sales/pi',
  '/quotation',
  '/purchase/orders',
];
const menuOf = (url: string) => {
  const path = url.split(/[?#]/)[0];
  return MENUS.filter((m) => path === m || path.startsWith(`${m}/`)).sort(
    (a, b) => b.length - a.length,
  )[0];
};

const start = () => {
  let s = M.ensureHome(M.emptyState(), '/dashboard');
  s = M.navigated(s, '/dashboard', 'push', menuOf);
  return s;
};
const urls = (t?: M.Tab) => t?.stack.map((e) => e.url);

describe('菜单标签页', () => {
  it('首页固定在最左，点菜单新建标签，再点切回且页面不变', () => {
    let s = start();
    expect(s.tabs.map((t) => t.menuPath)).toEqual(['/dashboard']);
    expect(s.tabs[0].pinned).toBe(true);

    let r = M.openMenu(s, '/sales/orders');
    expect(r.url).toBe('/sales/orders');
    s = M.navigated(r.state, r.url, 'push', menuOf);
    s = M.navigated(s, '/sales/orders/3', 'push', menuOf);
    expect(urls(M.activeTab(s))).toEqual(['/sales/orders', '/sales/orders/3']);

    r = M.openMenu(s, '/quotation');
    s = M.navigated(r.state, r.url, 'push', menuOf);
    r = M.openMenu(s, '/sales/orders');
    // 已有标签：跳到它当前的页面（订单详情），不新开
    expect(r.url).toBe('/sales/orders/3');
    expect(
      r.state.tabs.filter((t) => t.menuPath === '/sales/orders'),
    ).toHaveLength(1);
  });

  it('页面内跳转入栈，返回出栈，可以多层返回', () => {
    let s = M.openMenu(start(), '/sales/orders').state;
    s = M.navigated(s, '/sales/orders/3', 'push', menuOf);
    s = M.navigated(s, '/sales/pi/7', 'push', menuOf);
    s = M.navigated(s, '/quotation/9', 'push', menuOf);
    // 跳到别的菜单下的页面仍在当前标签
    expect(s.active).toBe('/sales/orders');
    let r = M.back(s);
    expect(r.url).toBe('/sales/pi/7');
    s = M.navigated(r.state, r.url as string, 'push', menuOf);
    r = M.back(s);
    expect(r.url).toBe('/sales/orders/3');
    expect(urls(M.activeTab(r.state))).toEqual([
      '/sales/orders',
      '/sales/orders/3',
    ]);
  });

  it('返回后上一页的条目 key 不变（缓存的页面原样显示）', () => {
    let s = M.openMenu(start(), '/purchase/orders').state;
    s = M.navigated(s, '/purchase/orders?status=2&page=3', 'replace', menuOf);
    const listKey = M.top(M.activeTab(s) as M.Tab).key;
    s = M.navigated(s, '/sales/orders/3', 'push', menuOf);
    const r = M.back(s);
    const top = M.top(M.activeTab(r.state) as M.Tab);
    expect(top.key).toBe(listKey);
    expect(top.url).toBe('/purchase/orders?status=2&page=3');
  });

  it('浏览器后退：是上一条就出栈，是别的标签的当前页就切过去', () => {
    let s = M.openMenu(start(), '/sales/orders').state;
    s = M.navigated(s, '/sales/orders/3', 'push', menuOf);
    s = M.navigated(s, '/sales/orders', 'pop', menuOf);
    expect(urls(M.activeTab(s))).toEqual(['/sales/orders']);
    s = M.navigated(s, '/dashboard', 'pop', menuOf);
    expect(s.active).toBe('/dashboard');
    expect(urls(s.tabs.find((t) => t.menuPath === '/sales/orders'))).toEqual([
      '/sales/orders',
    ]);
  });

  it('首次打开详情地址：按菜单归属建标签，没有上一页', () => {
    let s = M.ensureHome(M.emptyState(), '/dashboard');
    s = M.navigated(s, '/sales/orders/3', 'push', menuOf);
    expect(s.active).toBe('/sales/orders');
    expect(urls(M.activeTab(s))).toEqual(['/sales/orders/3']);
  });

  it('超过 10 个标签关掉最久没用的，跳过固定和有未保存修改的', () => {
    let s = start();
    for (let i = 1; i <= 9; i += 1) s = M.openMenu(s, `/m${i}`).state;
    expect(s.tabs).toHaveLength(10);
    s = M.setDirty(
      s,
      M.top(s.tabs.find((t) => t.menuPath === '/m1') as M.Tab).key,
      true,
    );
    s = M.openMenu(s, '/m10').state;
    const paths = s.tabs.map((t) => t.menuPath);
    expect(paths).toHaveLength(10);
    expect(paths).toContain('/dashboard');
    expect(paths).toContain('/m1');
    expect(paths).not.toContain('/m2');
    expect(paths).toContain('/m10');
  });

  it('关闭当前标签切到右边一个；固定标签不能关；关闭其他、关闭右侧', () => {
    let s = start();
    for (const m of ['/sales/orders', '/sales/pi', '/quotation'])
      s = M.openMenu(s, m).state;
    s = M.switchTo(s, '/sales/pi').state;
    let r = M.close(s, '/sales/pi');
    expect(r.state.active).toBe('/quotation');
    expect(r.url).toBe('/quotation');
    expect(M.close(s, '/dashboard').state).toBe(s);

    r = M.closeOthers(s, '/sales/orders');
    expect(r.state.tabs.map((t) => t.menuPath)).toEqual([
      '/dashboard',
      '/sales/orders',
    ]);

    r = M.closeRight(s, '/sales/orders');
    expect(r.state.tabs.map((t) => t.menuPath)).toEqual([
      '/dashboard',
      '/sales/orders',
    ]);
    expect(r.state.active).toBe('/sales/orders');
  });

  it('登记标题、刷新换 key、恢复时按权限过滤', () => {
    let s = M.openMenu(start(), '/sales/orders').state;
    s = M.navigated(s, '/sales/orders/3', 'push', menuOf);
    const key = M.top(M.activeTab(s) as M.Tab).key;
    s = M.setTitle(s, key, '销售订单 SO1', 'SO1');
    expect(M.top(M.activeTab(s) as M.Tab)).toMatchObject({
      title: '销售订单 SO1',
      tabLabel: 'SO1',
    });
    expect(M.setTitle(s, key, '销售订单 SO1', 'SO1')).toBe(s);
    s = M.refresh(s, '/sales/orders');
    expect(M.top(M.activeTab(s) as M.Tab).key).not.toBe(key);

    const saved = JSON.parse(
      JSON.stringify(M.setDirty(s, '/sales/orders', true)),
    );
    const restored = M.restore(saved, (p) => p !== '/sales/orders');
    expect(restored.tabs.map((t) => t.menuPath)).toEqual(['/dashboard']);
    expect(restored.active).toBeUndefined();
    expect(M.isDirty(M.restore(saved, () => true).tabs[1])).toBe(false);
    expect(M.restore(undefined, () => true)).toEqual(M.emptyState());
  });

  it('直接打开地址：刷新不变，其他地址归到所属菜单的标签', () => {
    let s = M.openMenu(start(), '/sales/orders').state;
    s = M.navigated(s, '/sales/orders/3', 'push', menuOf);
    s = M.navigated(s, '/sales/pi/7', 'push', menuOf);
    // 刷新：当前标签栈顶就是这个地址
    expect(M.landed(s, '/sales/pi/7', menuOf)).toBe(s);

    // 粘贴采购订单链接：新开采购订单标签，不压进销售订单标签
    let t = M.landed(s, '/purchase/orders/5', menuOf);
    expect(t.active).toBe('/purchase/orders');
    expect(urls(M.activeTab(t))).toEqual(['/purchase/orders/5']);
    expect(urls(t.tabs.find((x) => x.menuPath === '/sales/orders'))).toEqual([
      '/sales/orders',
      '/sales/orders/3',
      '/sales/pi/7',
    ]);

    // 该菜单已有标签：切过去并入栈
    t = M.landed(t, '/sales/orders/8', menuOf);
    expect(t.active).toBe('/sales/orders');
    expect(urls(M.activeTab(t))).toEqual([
      '/sales/orders',
      '/sales/orders/3',
      '/sales/pi/7',
      '/sales/orders/8',
    ]);

    // 正好是别的标签的栈顶：只切过去
    t = M.landed(t, '/purchase/orders/5', menuOf);
    expect(t.active).toBe('/purchase/orders');
    expect(urls(M.activeTab(t))).toEqual(['/purchase/orders/5']);
  });
});
