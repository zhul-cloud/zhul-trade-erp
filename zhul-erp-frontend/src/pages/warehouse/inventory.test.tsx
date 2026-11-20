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
import { showStockShortage } from './inventoryParts';
import Ledger from './ledger';
import Stocks from './stocks';
import { OtherOutboundModal } from './stocks/dialogs';
import Warehouses from './warehouses';

/** openspec inventory-foundation 8.x：库存查询、台账、仓库档案、其他出库弹窗、库存不足提示 */
jest.mock('@umijs/max', () => {
  const R = jest.requireActual('react');
  return {
    history: { push: jest.fn(), replace: jest.fn() },
    Link: ({ to, children }: { to: string; children?: unknown }) =>
      R.createElement('a', { href: to }, children),
    request: jest.fn(),
    useAccess: () => ({
      'inventory:other-outbound:edit': true,
      'inventory:return-to-supplier': true,
      'inventory:warehouse:edit': true,
    }),
    useLocation: () => ({ pathname: '/warehouse', search: '' }),
    useModel: () => ({ initialState: {} }),
  };
});

const page = (records: unknown[] = []) => ({
  data: { total: records.length, records },
});

const stockRow = {
  productId: 11,
  model: '6ES7214-1AG40-0XB0',
  brand: 'Siemens',
  category: 'PLC CPU',
  warehouseId: 1,
  warehouseName: '公司仓库',
  warehouseType: 1,
  good: 12,
  defective: 1,
  orderReserved: 10,
  returnReserved: 0,
  available: 2,
  inTransit: 3,
  amountCny: 9231.81,
  lastMoveTime: '2026-11-20T15:02:00',
};

const batches = [
  {
    batchId: 101,
    batchNo: 'RC20261120002-1',
    sourceType: 1,
    sourceId: 7,
    inDate: '2026-11-20',
    grade: '全新原装',
    stockStatus: 1,
    stockStatusName: '合格',
    onHand: 10,
    reserved: 10,
    free: 0,
    unitPriceCny: 712.3456,
    amountCny: 7123.46,
    reservations: [
      { targetType: 1, targetId: 5, docNo: 'SO20261118003', quantity: 10 },
    ],
  },
  {
    batchId: 102,
    batchNo: 'RC20261009001-1',
    sourceType: 1,
    sourceId: 8,
    inDate: '2026-10-09',
    grade: '全新原装',
    stockStatus: 1,
    stockStatusName: '合格',
    onHand: 2,
    reserved: 0,
    free: 2,
    unitPriceCny: 698,
    amountCny: 1396,
    supplierId: 3,
    supplierName: '深圳工控自动化',
    poId: 9,
    poNo: 'FWPO20261115004',
    reservations: [],
  },
];

const mount = (el: unknown) =>
  render(React.createElement(App, null, el as never));

afterEach(() => (request as jest.Mock).mockReset());

it('库存查询：顶部汇总，展开看批次，可出的批次才有其他出库与退回供应商', async () => {
  const urls: string[] = [];
  (request as jest.Mock).mockImplementation((url: string) => {
    urls.push(url);
    if (url.endsWith('/stocks/summary'))
      return Promise.resolve({
        data: {
          amountCny: 54289.88,
          productCount: 4,
          warehouseCount: 2,
          available: 3,
          defective: 1,
          returnPending: 3,
        },
      });
    if (url.endsWith('/stocks/batches'))
      return Promise.resolve({ data: batches });
    if (url.endsWith('/warehouses')) return Promise.resolve({ data: [] });
    return Promise.resolve(page([stockRow]));
  });
  mount(React.createElement(Stocks));
  expect(await screen.findByText('6ES7214-1AG40-0XB0')).toBeTruthy();
  expect(screen.getByText('4 个商品 · 2 个仓库')).toBeTruthy();
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: /展开|Expand/ }));
  });
  expect(await screen.findByText('RC20261009001-1')).toBeTruthy();
  expect(urls).toContain('/api/v1/warehouse/stocks/batches');
  expect(screen.getByText('SO20261118003')).toBeTruthy();
  // 全部预占的批次没有操作；可用 2 的批次有其他出库、退回供应商
  expect(screen.getAllByRole('button', { name: '其他出库' })).toHaveLength(1);
  expect(screen.getAllByRole('button', { name: '退回供应商' })).toHaveLength(1);
});

it('库存台账：点数量跳到这个商品当月的流水', async () => {
  (request as jest.Mock).mockImplementation((url: string) => {
    if (url.endsWith('/warehouses')) return Promise.resolve({ data: [] });
    return Promise.resolve(
      page([
        {
          productId: 11,
          model: 'AX32-30-10-80',
          brand: 'ABB',
          openingQty: 4,
          openingAmount: 2184,
          inQty: 2,
          inAmount: 1092,
          outQty: 0,
          outAmount: 0,
          closingQty: 6,
          closingAmount: 3276,
        },
      ]),
    );
  });
  mount(React.createElement(Ledger));
  await screen.findByText('AX32-30-10-80');
  const link = screen
    .getAllByText('2')
    .map((e) => e.closest('a'))
    .find(Boolean);
  expect(link?.getAttribute('href')).toMatch(
    /^\/warehouse\/moves\?productId=11&model=AX32-30-10-80&from=\d{4}-\d{2}-01&to=/,
  );
  expect(screen.getByText('合计')).toBeTruthy();
});

it('仓库档案：切换库存模式要二次确认，确认后才保存', async () => {
  const calls: { url: string; method?: string; data?: unknown }[] = [];
  (request as jest.Mock).mockImplementation(
    (url: string, opt: { method?: string; data?: unknown }) => {
      calls.push({ url, method: opt?.method, data: opt?.data });
      if (url.endsWith('/mode'))
        return Promise.resolve({
          data: { mode: opt?.method === 'PUT' ? 'stock' : 'dropship' },
        });
      return Promise.resolve({ data: [] });
    },
  );
  mount(React.createElement(Warehouses));
  const stock = await screen.findByRole('button', { name: /库存模式.*有备货/ });
  await act(async () => {
    fireEvent.click(stock);
  });
  expect(
    (await screen.findAllByText('改为「库存模式」？')).length,
  ).toBeGreaterThan(0);
  expect(calls.some((c) => c.method === 'PUT')).toBe(false);
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: '确认修改' }));
  });
  await waitFor(() =>
    expect(calls).toContainEqual({
      url: '/api/v1/warehouse/warehouses/mode',
      method: 'PUT',
      data: { mode: 'stock' },
    }),
  );
});

it('其他出库弹窗：数量上限是批次可出数量，公司仓库只能报废或转样品', async () => {
  (request as jest.Mock).mockImplementation((url: string) =>
    Promise.resolve({ data: url.endsWith('/stocks/batches') ? batches : [] }),
  );
  mount(
    React.createElement(OtherOutboundModal, {
      open: true,
      target: {
        productId: 11,
        model: 'AX32-30-10-80',
        brand: 'ABB',
        warehouseId: 1,
        warehouseName: '公司仓库',
        warehouseType: 1,
      },
      onClose: jest.fn(),
      onDone: jest.fn(),
    }),
  );
  const input = await screen.findByRole('spinbutton', {
    name: 'RC20261009001-1 合格 数量',
  });
  expect(input.getAttribute('aria-valuemax')).toBe('2');
  // 全部预占的批次不出现
  expect(screen.queryByText(/RC20261120002-1/)).toBeNull();
  expect(screen.getByText('报废')).toBeTruthy();
  expect(screen.getByText('转样品')).toBeTruthy();
  expect(screen.queryByText('货代丢失')).toBeNull();
  expect(screen.getByText(/预占的 10 个不能出库/)).toBeTruthy();
});

it('库存不足：STOCK_SHORTAGE 弹窗逐行显示缺多少，其他错误不处理', async () => {
  const holder: { modal?: unknown } = {};
  const Probe = () => {
    holder.modal = App.useApp().modal;
    return null;
  };
  mount(React.createElement(Probe));
  expect(
    showStockShortage(holder.modal as never, { errorCode: 'OTHER' }, '交货代'),
  ).toBe(false);
  await act(async () => {
    showStockShortage(
      holder.modal as never,
      {
        errorCode: 'STOCK_SHORTAGE',
        detail: {
          docNo: 'OB20261121004',
          items: [
            { model: 'E3Z-D61', shortage: 5 },
            { model: 'AX32-30-10-80', shortage: 1 },
          ],
        },
      },
      '交货代',
    );
  });
  expect((await screen.findAllByText('不能交货代')).length).toBeGreaterThan(0);
  expect(screen.getByText('缺 5')).toBeTruthy();
  expect(screen.getByText('缺 1')).toBeTruthy();
});
