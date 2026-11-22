import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import { poApi } from '../service';
import PurchaseOrderList from './index';

jest.mock('@umijs/max', () => ({
  history: { push: jest.fn(), listen: jest.fn() },
  request: jest.fn(() => Promise.resolve({ data: [] })),
  useAccess: () => ({}),
  useLocation: () => ({ pathname: '/purchase/orders', search: '' }),
  useModel: () => ({ initialState: {} }),
  useSearchParams: () => [new URLSearchParams()],
  Link: (p: { children: unknown }) => p.children,
}));
jest.mock('@/services/zhul/users', () => ({
  getUserOptions: jest.fn(() => Promise.resolve([])),
}));
jest.mock('../service', () => ({
  ...jest.requireActual('../service'),
  poApi: {
    ...jest.requireActual('../service').poApi,
    page: jest.fn(),
    stats: jest.fn(),
  },
}));

const lastQuery = () => (poApi.page as jest.Mock).mock.calls.at(-1)?.[0];

/** 增量 purchase-order：快捷筛选「逾期未发」只按这一个条件查，取消后回到默认的最近 3 个月 */
it('点「逾期未发」只按发货进度查，再点一次回到默认条件', async () => {
  (poApi.page as jest.Mock).mockResolvedValue({ total: 0, records: [] });
  (poApi.stats as jest.Mock).mockResolvedValue({
    monthOrderedCny: 0,
    monthOrderedCount: 0,
    monthBargain: 0,
    monthBargainRate: null,
    drafts: 0,
    pendingShip: 2,
    overdueShip: 1,
    staleDrafts: 0,
    orderCancelled: 0,
  });
  render(
    React.createElement(App, null, React.createElement(PurchaseOrderList)),
  );
  await waitFor(() => expect(lastQuery()?.orderFrom).toBeTruthy());

  const quick = await screen.findByRole('button', { name: /逾期未发 · 1/ });
  await act(async () => {
    fireEvent.click(quick);
  });
  await waitFor(() => expect(lastQuery()?.shipProgress).toBe('OVERDUE'));
  expect(lastQuery()?.orderFrom).toBeUndefined();
  expect(quick.getAttribute('aria-pressed')).toBe('true');
  // 搜索条件卡片的显示也清空：日期框为空
  const dates = () =>
    [
      ...document.querySelectorAll<HTMLInputElement>('.ant-picker-range input'),
    ].map((i) => i.value);
  expect(dates()).toEqual(['', '']);

  await act(async () => {
    fireEvent.click(quick);
  });
  await waitFor(() => expect(lastQuery()?.shipProgress).toBeUndefined());
  expect(lastQuery()?.orderFrom).toBeTruthy();
  expect(dates().every(Boolean)).toBe(true);
});
