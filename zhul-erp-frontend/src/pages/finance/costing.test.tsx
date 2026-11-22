import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import { request } from '@umijs/max';
import { App } from 'antd';
import React from 'react';
import CostAdjustments from './cost-adjustments';
import CostAdjustDetail from './cost-adjustments/detail';
import Periods from './periods';
import PeriodDetail from './periods/detail';

/** openspec inventory-costing 7.1、7.2：成本调整列表与详情、库存月结的结账确认与反结账 */
let mockAccess: Record<string, boolean> = {};
let mockParams: Record<string, string> = { id: '5' };
const mockPush = jest.fn();
jest.mock('@umijs/max', () => {
  const R = jest.requireActual('react');
  return {
    history: { push: (...a: unknown[]) => mockPush(...a), replace: jest.fn() },
    Link: ({ to, children }: { to: string; children?: unknown }) =>
      R.createElement('a', { href: to }, children),
    request: jest.fn(),
    useAccess: () => mockAccess,
    useLocation: () => ({ pathname: '/finance', search: '' }),
    useModel: () => ({ initialState: {} }),
    useParams: () => mockParams,
  };
});
jest.mock('@/components/PageTabs/store', () => ({
  tabsActions: { setDirty: jest.fn(), setTitle: jest.fn() },
}));
jest.mock('@/components/DocFiles', () => ({ DocFilesCard: () => null }));

type Call = { url: string; method?: string; data?: unknown; params?: unknown };
const mockApi = (route: (c: Call) => unknown) => {
  const calls: Call[] = [];
  (request as jest.Mock).mockImplementation(
    (url: string, opt: Omit<Call, 'url'> = {}) => {
      const c = { url, ...opt };
      calls.push(c);
      return Promise.resolve({ data: route(c) });
    },
  );
  return calls;
};
const mount = (el: unknown) =>
  render(React.createElement(App, null, React.createElement(el as never)));

afterEach(() => {
  (request as jest.Mock).mockReset();
  mockAccess = {};
  mockParams = { id: '5' };
  mockPush.mockReset();
});

const adjust = {
  id: 5,
  adjustNo: 'CA20261205001',
  adjustDate: '2026-12-05',
  periodMonth: '2026-12',
  reason: 1,
  reasonName: '外币付款',
  sourceDocType: 'PY',
  sourceDocId: 3,
  sourceDocNo: 'FK20261205003',
  batchCount: 2,
  firstModel: '6ES7214-1AG40-0XB0',
  stockAmount: 12,
  cogsAmount: 18,
  otherAmount: 0,
  totalAmount: 30,
  createTime: '2026-12-05T14:32:00',
  createBy: '系统',
  updateTime: '2026-12-05T14:32:00',
  updateBy: '系统',
};

it('成本调整列表：月份合计卡片，只看有销售成本调整，来源单号可点', async () => {
  const calls = mockApi((c) =>
    c.url.endsWith('/summary')
      ? {
          month: '2026-12',
          stockAmount: 1284.06,
          cogsAmount: 2031.55,
          otherAmount: 0,
          count: 14,
          countByReason: { 外币付款: 9, 采购订单费用: 3, 折价接收: 2 },
        }
      : { total: 1, records: [adjust] },
  );
  mount(CostAdjustments);
  expect(await screen.findByText('CA20261205001')).toBeTruthy();
  expect(screen.getByText('+¥1,284.06')).toBeTruthy();
  expect(
    screen.getByText('外币付款 9 · 采购订单费用 3 · 折价接收 2'),
  ).toBeTruthy();
  expect(
    screen.getByText('FK20261205003').closest('a')?.getAttribute('href'),
  ).toBe('/finance/payments/3');
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: /只看有销售成本调整/ }));
  });
  await waitFor(() =>
    expect(
      calls.some(
        (c) =>
          c.url.endsWith('/page') &&
          (c.data as { cogsOnly?: boolean }).cogsOnly === true,
      ),
    ).toBe(true),
  );
});

it('成本调整详情：按批次展开在库与已出运各调了多少', async () => {
  mockApi(() => ({
    ...adjust,
    batchCount: 1,
    batches: [
      {
        batchId: 9,
        batchNo: 'RC20261120002-1',
        productId: 11,
        model: '6ES7214-1AG40-0XB0',
        brand: 'Siemens',
        oldUnitCost: 710,
        newUnitCost: 713,
        diffGoods: 3,
        diffInvoice: 0,
        diffFee: 0,
        diffClaim: 0,
        quantity: 10,
        amountCny: 30,
        parts: [
          {
            id: 1,
            part: 1,
            partName: '在库',
            warehouseName: '公司仓库',
            quantity: 4,
            amountCny: 12,
          },
          {
            id: 2,
            part: 2,
            partName: '已出运',
            docType: 'SH',
            docId: 7,
            docNo: 'SH20261128001',
            soId: 3,
            soNo: 'SO20261120001',
            quantity: 6,
            amountCny: 18,
          },
        ],
      },
    ],
    logs: [],
  }));
  mount(CostAdjustDetail);
  expect(await screen.findByText('713.000000')).toBeTruthy();
  expect(screen.getByText('+3.000000')).toBeTruthy();
  expect(screen.getByText('公司仓库')).toBeTruthy();
  expect(
    screen.getByText('SH20261128001').closest('a')?.getAttribute('href'),
  ).toBe('/logistics/shipments/7');
  expect(screen.getByText('+18.00')).toBeTruthy();
});

const periods = [
  {
    month: '2026-12',
    status: 0,
    statusName: '未结账',
    openingAmount: 54289.88,
    inAmount: 28350,
    outAmount: 23824.48,
    stockAdjustAmount: 3105,
    closingAmount: 61920.4,
    cogsAmount: 18402.1,
    adjustAmount: 3315.61,
    adjustCogsAmount: 210.61,
    adjustOtherAmount: 0,
    closable: true,
    reopenable: false,
  },
  {
    month: '2026-11',
    status: 1,
    statusName: '已结账',
    openingAmount: 0,
    inAmount: 96905.25,
    outAmount: 42627.37,
    stockAdjustAmount: 12,
    closingAmount: 54289.88,
    cogsAmount: 42615.37,
    adjustAmount: 12,
    closedByName: '张敏',
    closedAt: '2026-12-02T10:12:00',
    closable: false,
    closeBlock: '已结账',
    reopenable: true,
  },
];

it('库存月结：没有按钮权限只能查看', async () => {
  mockApi(() => periods);
  mount(Periods);
  expect(await screen.findByText('2026-12')).toBeTruthy();
  expect(screen.queryByRole('button', { name: '结账' })).toBeNull();
  expect(screen.queryByRole('button', { name: '反结账' })).toBeNull();
  expect(screen.getAllByText('查看')).toHaveLength(2);
  // 汇总按算式排：表头带 + − =
  for (const t of [
    '+ 本月入库',
    '− 本月出库',
    '+ 在库成本调整',
    '= 期末库存金额',
  ])
    expect(screen.getAllByText(t).length).toBeGreaterThan(0);
});

it('库存月结：结账先看汇总与提醒，确认后才结账', async () => {
  mockAccess = { 'inventory:period:close': true };
  const calls = mockApi((c) => {
    if (c.url.endsWith('/2026-12'))
      return {
        ...periods[0],
        reminders: ['3 条采购入库行还没关联商品，这些货不在库存里'],
      };
    if (c.url.endsWith('/close')) return periods[0];
    return periods;
  });
  mount(Periods);
  await screen.findByText('2026-12');
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: /^结\s?账$/ }));
  });
  expect(
    await screen.findByText('3 条采购入库行还没关联商品，这些货不在库存里'),
  ).toBeTruthy();
  expect(screen.getByText('本月出库')).toBeTruthy();
  expect(screen.getByText('+CNY 3,105.00')).toBeTruthy();
  expect(
    screen.getByText(/（在库 \+3,105\.00、销售成本 \+210\.61）/),
  ).toBeTruthy();
  expect(calls.some((c) => c.url.endsWith('/close'))).toBe(false);
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: /确认结账/ }));
  });
  await waitFor(() =>
    expect(
      calls.some(
        (c) =>
          c.url === '/api/v1/finance/periods/2026-12/close' &&
          c.method === 'POST',
      ),
    ).toBe(true),
  );
});

it('库存月结：反结账必须填原因', async () => {
  mockAccess = { 'inventory:period:reopen': true };
  const calls = mockApi((c) =>
    c.url.endsWith('/reopen') ? periods[1] : periods,
  );
  mount(Periods);
  await screen.findByText('2026-11');
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: '反结账' }));
  });
  const ok = await screen.findByRole('button', { name: '确认反结账' });
  expect(ok.hasAttribute('disabled')).toBe(true);
  await act(async () => {
    fireEvent.change(screen.getByRole('textbox'), {
      target: { value: '补登 11-30 的出运单' },
    });
  });
  expect(ok.hasAttribute('disabled')).toBe(false);
  await act(async () => {
    fireEvent.click(ok);
  });
  await waitFor(() =>
    expect(calls).toContainEqual(
      expect.objectContaining({
        url: '/api/v1/finance/periods/2026-11/reopen',
        data: { reason: '补登 11-30 的出运单' },
      }),
    ),
  );
});

it('库存月结：凭证已导出的月份反结账时提示去 T+ 删除', async () => {
  mockAccess = { 'inventory:period:reopen': true };
  mockApi((c) =>
    c.url.endsWith('/periods/2026-11')
      ? { ...periods[1], voucherExported: true }
      : periods,
  );
  mount(Periods);
  await screen.findByText('2026-11');
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: '反结账' }));
  });
  expect(await screen.findByText(/请在 T\+ 里删除它们/)).toBeTruthy();
});

it('库存月结详情：类目账页签在最前，合计与汇总一致，点品类打开库存台账明细账', async () => {
  mockParams = { month: '2026-11' };
  mockApi((c) => {
    if (c.url.endsWith('/category-ledger'))
      return {
        month: '2026-11',
        rows: [
          {
            categoryId: 1,
            categoryName: 'PLC 与控制器',
            productCount: 6,
            openingAmount: 0,
            inAmount: 96905.25,
            outAmount: 42627.37,
            adjustAmount: 12,
            closingAmount: 54289.88,
            cogsAmount: 42615.37,
          },
        ],
        total: {
          categoryName: '合计',
          productCount: 6,
          openingAmount: 0,
          inAmount: 96905.25,
          outAmount: 42627.37,
          adjustAmount: 12,
          closingAmount: 54289.88,
          cogsAmount: 42615.37,
        },
      };
    if (c.url.endsWith('/periods/2026-11')) return periods[1];
    return { total: 0, records: [] };
  });
  mount(PeriodDetail);
  await screen.findByText('PLC 与控制器');
  const tabs = screen.getAllByRole('tab').map((t) => t.textContent);
  expect(tabs.slice(0, 4)).toEqual([
    '类目账',
    '库存台账',
    '销售成本',
    '成本调整',
  ]);
  expect(screen.getByText('合计')).toBeTruthy();
  expect(screen.getAllByText('54,289.88').length).toBeGreaterThan(0);
  await act(async () => {
    fireEvent.click(screen.getByText('PLC 与控制器'));
  });
  expect(mockPush).toHaveBeenCalledWith(
    '/warehouse/ledger?view=detail&month=2026-11&categoryId=1',
  );
});
