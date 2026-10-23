import { findMenu } from './menuName';

const tree = [
  { name: '工作台', path: '/dashboard', type: 2 },
  {
    name: '财务管理',
    path: '/finance',
    type: 1,
    children: [
      {
        name: '收款管理',
        path: '/finance/receipts',
        type: 2,
        children: [{ name: '登记到账', path: '', type: 3 }],
      },
    ],
  },
  {
    name: '业务设置',
    path: '/settings',
    type: 1,
    children: [{ name: '汇率设置', path: '/system/exchange-rate', type: 2 }],
  },
  {
    name: '系统管理',
    path: '/system',
    type: 1,
    children: [{ name: '用户管理', path: '/system/user', type: 2 }],
  },
  {
    name: '采购管理',
    path: '/purchase',
    type: 1,
    children: [
      { name: '询价分配', path: '/inquiry/sourcing-board', type: 2 },
      {
        name: '兼职看板',
        path: '/inquiry/part-time-board',
        type: 2,
        isHidden: 1,
      },
    ],
  },
  {
    name: '销售管理',
    path: '/business',
    type: 1,
    children: [
      { name: '询盘管理', path: '/inquiry/customer-inquiries', type: 2 },
      { name: '询盘单（已下线）', path: '/inquiry/orders', type: 2, status: 0 },
    ],
  },
];

describe('findMenu：页面地址对应的菜单名称与分组', () => {
  it.each([
    ['/finance/receipts', '收款管理', '财务管理'],
    ['/finance/receipts/12', '收款管理', '财务管理'],
    ['/system/exchange-rate', '汇率设置', '业务设置'],
    ['/system/user', '用户管理', '系统管理'],
    ['/inquiry/sourcing-board/rules', '询价分配', '采购管理'],
    ['/inquiry/customer-inquiries/3/confirm', '询盘管理', '销售管理'],
    ['/dashboard', '工作台', ''],
  ])('%s → %s（%s）', (path, name, group) => {
    expect(findMenu(tree, path)).toEqual({ name, group });
  });

  it('分组地址、前缀相同但不是下级的地址、隐藏或停用的菜单都不算', () => {
    expect(findMenu(tree, '/finance')).toBeUndefined();
    expect(findMenu(tree, '/finance/receipts-old')).toBeUndefined();
    expect(findMenu(tree, '/inquiry/part-time-board')).toBeUndefined();
    expect(findMenu(tree, '/inquiry/orders')).toBeUndefined();
  });
});
