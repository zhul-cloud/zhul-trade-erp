import { act, fireEvent, render, screen, within } from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import { orderApi } from '../service';
import OrderList, { HELP } from './index';

jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  Link: () => null,
  request: jest.fn().mockResolvedValue({ data: [] }),
  useAccess: () => ({ 'sales:order:create': true }),
  useLocation: () => ({ pathname: '/sales/orders', search: '' }),
  useModel: () => ({
    initialState: {
      menuTree: [
        {
          name: '销售管理',
          type: 1,
          children: [{ name: '销售订单', type: 2, path: '/sales/orders' }],
        },
      ],
    },
  }),
}));

jest.mock('@/services/zhul/users', () => ({
  getUserOptions: jest.fn().mockResolvedValue([]),
}));

jest.mock('../service', () => ({
  ...jest.requireActual('../service'),
  orderApi: { page: jest.fn(), stats: jest.fn() },
}));

/** 改造前统计卡片用的同一份接口数据（OrderStats） */
const STATS = {
  monthCount: 26,
  inProgressCount: 18,
  progressCounts: [
    { code: 'PENDING_PURCHASE', name: '待采购', count: 5 },
    { code: 'ORDERED', name: '已下单', count: 7 },
    { code: 'RECEIVED', name: '已入库', count: 3 },
    { code: 'TO_FORWARDER', name: '已交货代', count: 2 },
    { code: 'SHIPPED', name: '已出运', count: 1 },
  ],
  monthNetCny: 386420,
  monthOnlineCny: 120300,
  monthOfflineCny: 266120,
  unassignedCount: 3,
};

const renderList = async () => {
  (orderApi.stats as jest.Mock).mockResolvedValue(STATS);
  (orderApi.page as jest.Mock).mockResolvedValue({ records: [], total: 0 });
  await act(async () => {
    render(React.createElement(App, null, React.createElement(OrderList)));
  });
};

const lastQuery = () => {
  const calls = (orderApi.page as jest.Mock).mock.calls;
  return calls[calls.length - 1][0];
};

// jsdom 不排版，宽度都是 0：按 1440 宽的屏幕算，汇总行的副文字放得下
const widthDesc = Object.getOwnPropertyDescriptor(
  HTMLElement.prototype,
  'clientWidth',
);
beforeAll(() => {
  Object.defineProperty(HTMLElement.prototype, 'clientWidth', {
    configurable: true,
    get: () => 1440,
  });
});
afterAll(() => {
  if (widthDesc)
    Object.defineProperty(HTMLElement.prototype, 'clientWidth', widthDesc);
});

beforeEach(() => {
  jest.clearAllMocks();
  window.history.pushState({}, '', '/sales/orders');
});

/** ui-redesign 6.1：原来四张统计卡片的数字并入汇总行，数字与改造前一致 */
it('汇总行：本月实收、进行中（含各阶段）、本月订单、未指定采购员', async () => {
  await renderList();
  expect(screen.getByRole('heading', { level: 1 }).textContent).toBe(
    '销售订单',
  );
  expect(screen.getByText('CNY 386,420.00')).toBeTruthy();
  expect(
    screen.getByText('线上 CNY 120,300.00 · 线下 CNY 266,120.00'),
  ).toBeTruthy();
  expect(screen.getByText('18')).toBeTruthy();
  expect(
    screen.getByText('待采购 5 · 已下单 7 · 已入库 3 · 已交货代 2 · 已出运 1'),
  ).toBeTruthy();
  expect(screen.getByText('26')).toBeTruthy();
  expect(screen.getByText('3')).toBeTruthy();
  // 口径放进帮助图标
  expect(screen.getByRole('img', { name: HELP.monthCount })).toBeTruthy();
  expect(screen.getByRole('img', { name: HELP.unassigned })).toBeTruthy();
  expect(screen.getByRole('img', { name: HELP.keyword })).toBeTruthy();
});

/** 「进行中」各阶段的数量并入订单状态下拉；选了立即按该状态查询 */
it('订单状态下拉带各阶段数量，选中后立即查询', async () => {
  await renderList();
  const select = screen.getByRole('combobox', { name: '订单状态' });
  await act(async () => {
    fireEvent.mouseDown(select);
  });
  const list = screen.getByRole('listbox');
  expect(list).toBeTruthy();
  for (const label of [
    '待采购 · 5',
    '已下单 · 7',
    '已入库 · 3',
    '已交货代 · 2',
    '已出运 · 1',
    '已完成',
    '已取消',
  ]) {
    expect(screen.getAllByTitle(label).length).toBeGreaterThan(0);
  }
  await act(async () => {
    fireEvent.click(screen.getAllByTitle('已入库 · 3')[0]);
  });
  expect(lastQuery()).toEqual(
    expect.objectContaining({ progressCode: 'RECEIVED', page: 1 }),
  );
});

/** 快捷筛选「毛利为负」：点一下按毛利为负查询，再点取消 */
it('快捷筛选毛利为负', async () => {
  await renderList();
  const chips = within(screen.getByRole('group', { name: '快捷筛选' }));
  const loss = chips.getByRole('button', { name: '毛利为负' });
  expect(loss.getAttribute('aria-pressed')).toBe('false');
  expect(lastQuery().lossOnly).toBeUndefined();
  await act(async () => {
    fireEvent.click(loss);
  });
  expect(loss.getAttribute('aria-pressed')).toBe('true');
  expect(lastQuery()).toEqual(expect.objectContaining({ lossOnly: true }));
  await act(async () => {
    fireEvent.click(loss);
  });
  expect(lastQuery().lossOnly).toBeUndefined();
});

/** 工作台「毛利为负的订单」带 loss=1 进来时，快捷筛选默认选中 */
it('地址带 loss=1 时毛利为负默认选中', async () => {
  window.history.pushState({}, '', '/sales/orders?loss=1');
  await renderList();
  expect(
    screen
      .getByRole('button', { name: '毛利为负' })
      .getAttribute('aria-pressed'),
  ).toBe('true');
  expect(lastQuery()).toEqual(expect.objectContaining({ lossOnly: true }));
});
