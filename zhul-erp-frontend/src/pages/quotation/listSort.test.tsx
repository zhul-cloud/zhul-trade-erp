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
import QuotationListPage from './index';
import { quotationApi } from './service';

jest.mock('@umijs/max', () => ({
  history: { push: jest.fn(), listen: jest.fn() },
  request: jest.fn(() => Promise.resolve({ data: [] })),
  useAccess: () => ({}),
  useLocation: () => ({ pathname: '/quotation', search: '' }),
  useModel: () => ({ initialState: {} }),
  useSearchParams: () => [new URLSearchParams()],
  Link: (p: { children: unknown }) => p.children,
}));
jest.mock('@/services/zhul/users', () => ({
  getUserOptions: jest.fn(() => Promise.resolve([])),
}));
jest.mock('./NewQuotationModal', () => () => null);
jest.mock('./service', () => ({
  ...jest.requireActual('./service'),
  quotationApi: {
    ...jest.requireActual('./service').quotationApi,
    page: jest.fn(),
    stats: jest.fn(),
  },
}));

const lastQuery = () => (quotationApi.page as jest.Mock).mock.calls.at(-1)?.[0];

/** 主规范 quotation：型号（按型号数）、总数量都能点表头排序（ui-redesign 7.1 复审 R4） */
it('点「总数量」表头按总数量排序，再点「型号」按型号数排序', async () => {
  (quotationApi.page as jest.Mock).mockResolvedValue({
    total: 1,
    records: [
      {
        id: 1,
        quotationNo: 'QT20261118001',
        customerId: 1,
        customerName: 'ACME',
        firstModel: '6ES7214-1AG40-0XB0',
        itemCount: 2,
        totalQuantity: 30,
        currencyCode: 'USD',
        totalAmount: 100,
        status: 1,
        statusName: '草稿',
        inquiryCodes: [],
        inquiries: [],
        ownerId: 1,
        createTime: '2026-11-18 10:00:00',
      },
    ],
  });
  (quotationApi.stats as jest.Mock).mockResolvedValue({});
  // 型号、总数量默认收进列设置（D55），这里先打开
  localStorage.setItem(
    storageKey('quotation'),
    JSON.stringify({
      order: [],
      shown: ['itemCount', 'totalQuantity'],
      hidden: [],
    }),
  );
  render(
    React.createElement(App, null, React.createElement(QuotationListPage)),
  );
  await waitFor(() => expect(quotationApi.page).toHaveBeenCalled());
  await screen.findByText('QT20261118001');

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
