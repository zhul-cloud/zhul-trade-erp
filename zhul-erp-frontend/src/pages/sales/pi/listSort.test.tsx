import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import { storageKey } from '@/components/ColumnSettings';
import { piApi } from '../service';
import PiListPage from './index';

jest.mock('@umijs/max', () => ({
  history: { push: jest.fn(), listen: jest.fn() },
  request: jest.fn(() => Promise.resolve({ data: [] })),
  useAccess: () => ({}),
  useLocation: () => ({ pathname: '/sales/pi', search: '' }),
  useModel: () => ({ initialState: {} }),
  useSearchParams: () => [new URLSearchParams()],
  Link: (p: { children: unknown }) => p.children,
}));
jest.mock('@/services/zhul/users', () => ({
  getUserOptions: jest.fn(() => Promise.resolve([])),
}));
jest.mock('./NewPiModal', () => () => null);
jest.mock('../service', () => ({
  ...jest.requireActual('../service'),
  piApi: {
    ...jest.requireActual('../service').piApi,
    page: jest.fn(),
    stats: jest.fn(),
  },
}));

const lastQuery = () => (piApi.page as jest.Mock).mock.calls.at(-1)?.[0];

/** 主规范 proforma-invoice：型号（按型号数）、总数量、合计都能点表头排序 */
it('点「总数量」表头按总数量排序，再点「型号」按型号数排序', async () => {
  (piApi.page as jest.Mock).mockResolvedValue({
    total: 1,
    records: [
      {
        id: 1,
        piNo: 'PI20261118001',
        customerName: 'ACME',
        firstModel: '6ES7214-1AG40-0XB0',
        itemCount: 2,
        totalQuantity: 30,
        currencyCode: 'USD',
        totalAmount: 100,
        receiptStatus: 0,
        receiptStatusName: '未收款',
        status: 1,
        statusName: '草稿',
        currentVersionNo: 1,
        revising: false,
        quotationNos: [],
        quotations: [],
      },
    ],
  });
  (piApi.stats as jest.Mock).mockResolvedValue({});
  // 型号、总数量默认收进列设置（D55），这里先打开
  localStorage.setItem(
    storageKey('sales-pi'),
    JSON.stringify({
      order: [],
      shown: ['itemCount', 'totalQuantity'],
      hidden: [],
    }),
  );
  render(React.createElement(App, null, React.createElement(PiListPage)));
  await waitFor(() => expect(piApi.page).toHaveBeenCalled());
  await screen.findByText('PI20261118001');

  await act(async () => {
    fireEvent.click(screen.getByRole('columnheader', { name: /总数量/ }));
  });
  await waitFor(() =>
    expect(lastQuery()).toMatchObject({ sortField: 'totalQuantity', page: 1 }),
  );

  await act(async () => {
    fireEvent.click(screen.getByRole('columnheader', { name: /^型号/ }));
  });
  await waitFor(() =>
    expect(lastQuery()).toMatchObject({ sortField: 'itemCount' }),
  );
});
