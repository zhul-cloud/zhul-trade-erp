import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import { App } from 'antd';
import dayjs from 'dayjs';
import React from 'react';
import { inquiryApi } from '../shared/service';
import InquiryListPage from './index';

jest.mock('@umijs/max', () => ({
  history: { push: jest.fn(), listen: jest.fn() },
  request: jest.fn(() => Promise.resolve({ data: [] })),
  useAccess: () => ({}),
  useLocation: () => ({ pathname: '/inquiry/customer-inquiries', search: '' }),
  useModel: () => ({ initialState: {} }),
  useSearchParams: () => [new URLSearchParams(), jest.fn()],
  Link: (p: { children: unknown }) => p.children,
}));
jest.mock('@/services/zhul/users', () => ({
  getUserOptions: jest.fn(() => Promise.resolve([])),
}));
jest.mock('../shared/service', () => ({
  ...jest.requireActual('../shared/service'),
  inquiryApi: {
    ...jest.requireActual('../shared/service').inquiryApi,
    page: jest.fn(),
  },
}));

/** 列表本身的查询（统计查询的 pageSize 为 1） */
const lastListQuery = () =>
  (inquiryApi.page as jest.Mock).mock.calls
    .map((c) => c[0])
    .filter((q) => q.pageSize !== 1)
    .at(-1);

/**
 * 快捷筛选「今日新增」按询盘日期取今天，与统计同口径；打开时询盘日期框置灰，
 * 不再按框里的范围查（ui-redesign 7.1 复审 R4）
 */
it('点「今日新增」按今天查询并置灰询盘日期框，再点取消恢复', async () => {
  (inquiryApi.page as jest.Mock).mockResolvedValue({
    total: 1,
    records: [
      {
        id: 1,
        inquiryCode: 'IQ20261125001',
        customerName: 'ACME',
        status: 5,
        statusName: '询价中',
        inquiryDate: dayjs().format('YYYY-MM-DD'),
        itemCount: 1,
        totalQuantity: 1,
      },
    ],
  });
  render(React.createElement(App, null, React.createElement(InquiryListPage)));
  await waitFor(() => expect(lastListQuery()).toBeTruthy());
  // 列表默认带询盘日期范围；记下来，取消「今日新增」后应回到它
  const { inquiryDateFrom: from0, inquiryDateTo: to0 } = lastListQuery();
  const today = dayjs().format('YYYY-MM-DD');

  await act(async () => {
    fireEvent.click(await screen.findByText('今日新增'));
  });
  await waitFor(() =>
    expect(lastListQuery()).toMatchObject({
      inquiryDateFrom: today,
      inquiryDateTo: today,
      page: 1,
    }),
  );
  const start = document.getElementById('inquiry-date') as HTMLInputElement;
  expect(start.disabled).toBe(true);

  await act(async () => {
    fireEvent.click(screen.getByText('今日新增'));
  });
  await waitFor(() =>
    expect(lastListQuery()).toMatchObject({
      inquiryDateFrom: from0,
      inquiryDateTo: to0,
    }),
  );
  expect(
    (document.getElementById('inquiry-date') as HTMLInputElement).disabled,
  ).toBe(false);
});
