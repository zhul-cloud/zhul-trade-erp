import { firstMenuPath, groupKey, toProLayoutMenu } from './menuOrder';

const tree = [
  {
    path: '/settings',
    name: '业务设置',
    type: 1,
    sort: 7,
    children: [
      { path: '/system/exchange-rate', name: '汇率', type: 2, sort: 2 },
    ],
  },
  {
    path: '/system',
    name: '系统管理',
    type: 1,
    sort: 98,
    children: [{ path: '/system/user', name: '用户', type: 2, sort: 1 }],
  },
];
const allowed = new Set([
  '/settings',
  '/system/exchange-rate',
  '/system',
  '/system/user',
]);

describe('toProLayoutMenu', () => {
  it('分组的 key 不会是任何页面地址的前缀，打开汇率不会连带展开系统管理', () => {
    const menu = toProLayoutMenu(tree, allowed);
    expect(menu.map((m) => m.path)).toEqual([
      groupKey('/settings'),
      groupKey('/system'),
    ]);
    const exchange = '/system/exchange-rate';
    const prefixHits = menu.filter((m) => exchange.startsWith(m.path));
    expect(prefixHits).toHaveLength(0);
    expect(menu[0].children?.[0].path).toBe(exchange);
  });

  it('首页取第一个叶子页面的真实地址', () => {
    expect(firstMenuPath(toProLayoutMenu(tree, allowed))).toBe(
      '/system/exchange-rate',
    );
  });
});
