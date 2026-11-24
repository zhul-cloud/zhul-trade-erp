import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import { searchCustomers } from '@/services/zhul/masterdata';
import AfterSalesPage from './index';
import { AS_STATUS, afterSalesApi } from './service';

let mockSearch = '';
jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  Link: () => null,
  request: jest.fn(),
  useLocation: () => ({ pathname: '/sales/after-sales', search: '' }),
  useModel: () => ({ initialState: {} }),
  // 各用例可改 mockSearch 模拟从工作台带参数进来
  useSearchParams: () => [new URLSearchParams(mockSearch)],
}));
jest.mock('@/services/zhul/masterdata', () => ({ searchCustomers: jest.fn() }));
jest.mock('@/utils/dict', () => ({
  useDictTexts: (type: string) =>
    type === 'after_sales_reason'
      ? [
          { code: 'QUALITY', name: '产品质量' },
          { code: 'TRANSPORT', name: '运输包装' },
        ]
      : [],
}));
jest.mock('./dialogs', () => ({
  CreateAfterSalesModal: () => null,
  useOwnerOptions: () => [],
}));
jest.mock('./service', () => ({
  ...jest.requireActual('./service'),
  afterSalesApi: { page: jest.fn(), counts: jest.fn() },
}));

beforeEach(() => {
  mockSearch = '';
  jest.clearAllMocks();
  (afterSalesApi.page as jest.Mock).mockResolvedValue({
    total: 0,
    records: [],
  });
  (afterSalesApi.counts as jest.Mock).mockResolvedValue({
    open: 3,
    waiting: 1,
    overdue: 2,
    dueSoon: 1,
    closedThisMonth: 4,
  });
});

const lastQuery = () => {
  const calls = (afterSalesApi.page as jest.Mock).mock.calls;
  return calls[calls.length - 1][0];
};

/** audit UI-067：spec「售后管理页」的客户、原因筛选 */
it('按客户与原因筛选', async () => {
  (afterSalesApi.page as jest.Mock).mockResolvedValue({
    total: 0,
    records: [],
  });
  (afterSalesApi.counts as jest.Mock).mockResolvedValue({});
  (searchCustomers as jest.Mock).mockResolvedValue([
    { id: 5, name: 'Siam Automation' },
  ]);
  render(React.createElement(App, null, React.createElement(AfterSalesPage)));
  await waitFor(() => expect(afterSalesApi.page).toHaveBeenCalled());

  // ui-redesign 7.1 起：原因在「更多筛选」里，选好后点「查询」生效
  fireEvent.click(screen.getByRole('button', { name: /更多筛选/ }));
  fireEvent.mouseDown(await screen.findByLabelText('原因'));
  fireEvent.click(await screen.findByText('产品质量'));
  fireEvent.click(screen.getByRole('button', { name: /查\s*询/ }));
  await waitFor(() =>
    expect(lastQuery()).toMatchObject({ reasonCode: 'QUALITY', page: 1 }),
  );

  const customer = screen.getByLabelText('客户');
  fireEvent.focus(customer);
  fireEvent.mouseDown(customer);
  fireEvent.click(await screen.findByText('Siam Automation'));
  fireEvent.click(screen.getByRole('button', { name: /查\s*询/ }));
  await waitFor(() =>
    expect(lastQuery()).toMatchObject({ customerId: 5, reasonCode: 'QUALITY' }),
  );
});

/** 工作台「售后要更新客户」带 overdue=1 和本人：只看逾期、状态放开、带负责人（audit UI-081） */
it('从工作台带 overdue 与负责人进来，重置回到处理中并清掉负责人', async () => {
  mockSearch = 'overdue=1&ownerId=7';
  render(React.createElement(App, null, React.createElement(AfterSalesPage)));
  await waitFor(() =>
    expect(lastQuery()).toMatchObject({ overdue: true, ownerId: 7 }),
  );
  expect(lastQuery().status).toBeUndefined();
  expect(
    screen
      .getByRole('button', { name: /需要更新客户/ })
      .getAttribute('aria-pressed'),
  ).toBe('true');

  fireEvent.click(screen.getByRole('button', { name: /重\s*置/ }));
  await waitFor(() =>
    expect(lastQuery()).toMatchObject({ status: AS_STATUS.OPEN }),
  );
  expect(lastQuery().overdue).toBeUndefined();
  expect(lastQuery().ownerId).toBeUndefined();
});

it('两个快捷筛选互斥，打开时状态放开；数量为逾期加快到期', async () => {
  render(React.createElement(App, null, React.createElement(AfterSalesPage)));
  await waitFor(() =>
    expect(lastQuery()).toMatchObject({ status: AS_STATUS.OPEN }),
  );
  const overdue = await screen.findByRole('button', {
    name: '需要更新客户 2',
  });
  const soon = screen.getByRole('button', { name: '已逾期或快到期 3' });

  fireEvent.click(overdue);
  await waitFor(() => expect(lastQuery()).toMatchObject({ overdue: true }));
  expect(lastQuery().status).toBeUndefined();

  fireEvent.click(soon);
  await waitFor(() => expect(lastQuery()).toMatchObject({ dueSoon: true }));
  expect(lastQuery().overdue).toBeUndefined();
  expect(overdue.getAttribute('aria-pressed')).toBe('false');
});
