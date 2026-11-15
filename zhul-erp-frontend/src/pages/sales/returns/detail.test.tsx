import { act, fireEvent, render, screen } from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import { PaneContext } from '@/components/PageTabs/hooks';
import { salesReturnApi } from '@/pages/sales/after-sales/service';
import SalesReturnDetail from './detail';

jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  Link: () => null,
  request: jest.fn(),
  useAccess: () => ({}),
  useLocation: () => ({ pathname: '/sales/returns/21', search: '' }),
  useModel: () => ({ initialState: {} }),
  useParams: () => ({ id: '21' }),
}));
jest.mock('@/components/PageTabs/store', () => ({
  tabsActions: { setDirty: jest.fn(), setTitle: jest.fn() },
}));
jest.mock('@/components/DocFiles', () => ({ DocFilesCard: () => null }));
jest.mock('@/pages/warehouse/components', () => ({ ReasonModal: () => null }));
jest.mock('./dialogs', () => ({
  ...jest.requireActual('./dialogs'),
  ConfirmReturnModal: (p: { data?: unknown }) =>
    p.data ? require('react').createElement('div', null, '确认退货弹窗') : null,
  EditReturnModal: () => null,
}));
jest.mock('@/pages/sales/after-sales/service', () => ({
  ...jest.requireActual('@/pages/sales/after-sales/service'),
  salesReturnApi: { detail: jest.fn() },
}));

const data = {
  id: 21,
  returnNo: 'TH20261115001',
  afterSalesId: 7,
  asNo: 'AS20261115001',
  soId: 3,
  soNo: 'SO20261115001',
  handling: 2,
  handlingName: '退货退款',
  status: 1,
  statusName: '待确认',
  currencyCode: 'USD',
  items: [{ id: 31, model: '6ES7214', quantity: 2, unitPrice: 100 }],
  refunds: [],
  createTime: '2026-11-15 10:00:00',
};

const page = (active: boolean) =>
  React.createElement(
    App,
    null,
    React.createElement(
      PaneContext.Provider,
      { value: { entryKey: 'th21', active } },
      React.createElement(SalesReturnDetail),
    ),
  );

beforeEach(() => {
  jest.clearAllMocks();
  (salesReturnApi.detail as jest.Mock).mockResolvedValue(data);
});

/** audit UI-065：「确认退货」开着时返回本页不重新取数，改过的逐行退款金额不被恢复成默认值 */
it('确认退货弹窗开着时返回不刷新，关着时刷新', async () => {
  const view = render(page(true));
  fireEvent.click(await screen.findByRole('button', { name: '确认退货' }));
  expect(screen.getByText('确认退货弹窗')).toBeTruthy();
  view.rerender(page(false));
  await act(async () => view.rerender(page(true)));
  expect(salesReturnApi.detail).toHaveBeenCalledTimes(1);
  expect(screen.getByText('确认退货弹窗')).toBeTruthy();
});

it('没有弹窗时返回重新取数', async () => {
  const view = render(page(true));
  await screen.findByRole('button', { name: '确认退货' });
  view.rerender(page(false));
  await act(async () => view.rerender(page(true)));
  expect(salesReturnApi.detail).toHaveBeenCalledTimes(2);
});
