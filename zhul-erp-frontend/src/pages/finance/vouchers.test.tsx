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
import Vouchers from './vouchers';
import VoucherDetail from './vouchers/detail';

/** openspec inventory-vouchers 4.1–4.3：业务凭证月份列表、月份详情、导出确认、科目设置 */
let mockAccess: Record<string, boolean> = {};
let mockParams: Record<string, string> = { month: '2026-12' };
jest.mock('@umijs/max', () => {
  const R = jest.requireActual('react');
  return {
    history: { push: jest.fn(), replace: jest.fn() },
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
  mockParams = { month: '2026-12' };
});

const months = [
  {
    month: '2027-01',
    generated: true,
    voucherCount: 18,
    debitTotal: 41208.5,
    currencyCode: 'CNY',
    reconDiff: 0,
    exportCount: 1,
    exportedByName: '张敏',
    exportedAt: '2027-02-03T10:20:00',
  },
  {
    month: '2026-12',
    generated: true,
    voucherCount: 12,
    debitTotal: 41976,
    currencyCode: 'CNY',
    reconDiff: 0,
    exportCount: 0,
  },
  { month: '2026-11', generated: false, currencyCode: 'CNY' },
];

const detail = (over: Record<string, unknown> = {}) => ({
  month: '2026-12',
  currencyCode: 'CNY',
  generated: true,
  generatedByName: '李会计',
  generatedAt: '2027-01-02T09:00:00',
  exportCount: 0,
  recon: {
    openingAmount: 54289.88,
    stockNetAmount: 2946,
    noVoucherAmount: 80,
    closingAmount: 57315.88,
    diff: 0,
    noVoucherItems: [
      { bizType: 'OPENING', name: '期初（含补关联商品）', amount: 80 },
      { bizType: 'SYSTEM_IN', name: '系统外入库', amount: 0 },
    ],
  },
  categoryCounts: { 1: 1, 3: 1 },
  debitTotal: 12840,
  missingItems: ['其他出库 · 货损'],
  periodCogsAmount: 18760,
  returnInAmount: 630,
  vouchers: [
    {
      id: 1,
      voucherDate: '2026-12-05',
      category: 1,
      categoryName: '采购入库',
      sourceDocType: 'GR',
      sourceDocId: 7,
      sourceDocNo: 'RC20261205001',
      bizType: 'PURCHASE_IN',
      summary: '采购入库 RC20261205001 深圳华强电子',
      action: '采购入库',
      partnerText: '深圳华强电子',
      debitTotal: 12600,
      entries: [
        {
          itemKey: 'STOCK',
          itemName: '库存商品',
          accountCode: '1405',
          accountName: '库存商品',
          debit: 12600,
        },
        {
          itemKey: 'AP',
          itemName: '应付账款',
          accountCode: '2202',
          accountName: '应付账款',
          partnerId: 3,
          partnerName: '深圳华强电子',
          credit: 12600,
        },
      ],
    },
    {
      id: 2,
      voucherDate: '2026-12-26',
      category: 3,
      categoryName: '其他出库',
      sourceDocType: 'OO',
      sourceDocId: 9,
      sourceDocNo: 'OO20261226001',
      bizType: 'OTHER_OUT',
      summary: '其他出库 · 货损 OO20261226001',
      action: '其他出库 · 货损',
      partnerText: '',
      debitTotal: 240,
      entries: [
        {
          itemKey: 'OTHER_OUT:4',
          itemName: '其他出库 · 货损',
          accountCode: '',
          accountName: '',
          debit: 240,
        },
        {
          itemKey: 'STOCK',
          itemName: '库存商品',
          accountCode: '1405',
          accountName: '库存商品',
          credit: 240,
        },
      ],
    },
  ],
  ...over,
});

it('月份列表：未导出、已导出、还没有凭证三种状态；没有权限不显示生成与导出', async () => {
  mockApi(() => months);
  mount(Vouchers);
  expect(await screen.findByText('2027-01')).toBeTruthy();
  expect(screen.getByText('已导出')).toBeTruthy();
  expect(screen.getByText('未导出')).toBeTruthy();
  expect(screen.getByText('还没有凭证')).toBeTruthy();
  expect(screen.getAllByText('相符').length).toBe(2);
  expect(screen.queryByRole('button', { name: /生成凭证/ })).toBeNull();
  expect(screen.queryByRole('button', { name: '导出' })).toBeNull();
  expect(screen.getByText('2026-12').closest('a')?.getAttribute('href')).toBe(
    '/finance/vouchers/2026-12',
  );
});

it('月份列表：上线前结账的月份手动生成凭证', async () => {
  mockAccess = { 'finance:voucher:export': true };
  const calls = mockApi((c) =>
    c.url.endsWith('/generate') ? detail() : months,
  );
  mount(Vouchers);
  await screen.findByText('2026-11');
  expect(screen.getAllByRole('button', { name: '导出' }).length).toBe(2);
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: /生成凭证/ }));
  });
  await waitFor(() =>
    expect(calls).toContainEqual(
      expect.objectContaining({
        url: '/api/v1/finance/vouchers/months/2026-11/generate',
        method: 'POST',
      }),
    ),
  );
});

it('月份详情：核对算式、未设科目提示、类别筛选、展开分录、单号打开来源单据', async () => {
  mockApi(() => detail());
  mount(VoucherDetail);
  expect(await screen.findByText('核对相符')).toBeTruthy();
  expect(screen.getByText('CNY 54,289.88')).toBeTruthy();
  expect(screen.getByText('CNY 57,315.88')).toBeTruthy();
  expect(screen.getByText(/不扣退货入库/)).toBeTruthy();
  expect(
    screen.getByText('「其他出库 · 货损」还没有设置科目，设置后才能导出'),
  ).toBeTruthy();
  expect(
    screen.getByText('RC20261205001').closest('a')?.getAttribute('href'),
  ).toBe('/warehouse/receipts/7');
  expect(
    screen.getByText('OO20261226001').closest('a')?.getAttribute('href'),
  ).toBe('/warehouse/other-outbounds/9');

  await act(async () => {
    fireEvent.click(screen.getByText('其他出库 1'));
  });
  expect(screen.queryByText('RC20261205001')).toBeNull();

  await act(async () => {
    fireEvent.click(screen.getByText('2026-12-26'));
  });
  expect(await screen.findByText('未设置科目')).toBeTruthy();
});

it('月份详情：核对不相符时显示差额', async () => {
  mockApi(() =>
    detail({
      missingItems: [],
      recon: { ...detail().recon, closingAmount: 57300, diff: 15.88 },
    }),
  );
  mount(VoucherDetail);
  expect(await screen.findByText('差 15.88')).toBeTruthy();
  expect(screen.queryByText('核对相符')).toBeNull();
});

it('导出：已经导出过的月份确认后带 confirmed 再导出', async () => {
  mockAccess = { 'finance:voucher:export': true };
  const confirmError = {
    type: 'application/json',
    text: async () =>
      JSON.stringify({
        code: 500,
        message: '重复',
        data: {
          errorCode: 'VOUCHER_EXPORT_CONFIRM',
          detail: {
            reasons: [
              '上次由 张敏 在 2027-02-03 10:20 导出，重复导入会在 T+ 里重复记账',
            ],
          },
        },
      }),
  };
  const calls = mockApi((c) => {
    if (c.url.endsWith('/export')) {
      if ((c.params as { confirmed: boolean }).confirmed)
        throw new Error('stop');
      return confirmError;
    }
    return detail({ missingItems: [], exportCount: 1, exportedByName: '张敏' });
  });
  mount(VoucherDetail);
  const exportBtn = await screen.findByRole('button', { name: /导出 T\+/ });
  await act(async () => {
    fireEvent.click(exportBtn);
  });
  expect(
    (await screen.findAllByText('2026-12 已经导出过')).length,
  ).toBeGreaterThan(0);
  expect(
    screen.getAllByText(/重复导入会在 T\+ 里重复记账/).length,
  ).toBeGreaterThan(0);
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: /仍然导出/ }));
  });
  await waitFor(() =>
    expect(
      calls.some(
        (c) =>
          c.url.endsWith('/export') &&
          (c.params as { confirmed: boolean }).confirmed === true,
      ),
    ).toBe(true),
  );
});

it('科目设置：只提交改过的业务项', async () => {
  mockAccess = { 'finance:voucher:account': true };
  const accounts = [
    {
      itemKey: 'STOCK',
      group: '库存与成本',
      itemName: '库存商品',
      accountCode: '1405',
      accountName: '库存商品',
    },
    {
      itemKey: 'OTHER_OUT:4',
      group: '其他出库',
      itemName: '货损',
      accountCode: '',
      accountName: '',
    },
  ];
  const calls = mockApi((c) => {
    if (c.url.endsWith('/accounts')) return accounts;
    if (c.url.endsWith('/suppliers')) return [];
    return months;
  });
  mount(Vouchers);
  const settingsBtn = await screen.findByRole('button', { name: /科目设置/ });
  await act(async () => {
    fireEvent.click(settingsBtn);
  });
  const code = await screen.findByLabelText('货损科目编码');
  await act(async () => {
    fireEvent.change(code, { target: { value: '6711' } });
    fireEvent.change(screen.getByLabelText('货损科目名称'), {
      target: { value: '营业外支出' },
    });
  });
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: /保\s*存/ }));
  });
  await waitFor(() =>
    expect(calls).toContainEqual(
      expect.objectContaining({
        url: '/api/v1/finance/vouchers/accounts',
        method: 'PUT',
        data: {
          items: [
            {
              itemKey: 'OTHER_OUT:4',
              accountCode: '6711',
              accountName: '营业外支出',
            },
          ],
        },
      }),
    ),
  );
});
