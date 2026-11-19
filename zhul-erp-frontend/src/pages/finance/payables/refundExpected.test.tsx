import { act, fireEvent, render, screen } from '@testing-library/react';
import { request } from '@umijs/max';
import { App } from 'antd';
import dayjs from 'dayjs';
import React from 'react';
import PayableManagement, { refundDays } from './index';

/** openspec po-cancel-refund：应付单「待退款」标记与筛选 */
jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  Link: () => null,
  request: jest.fn(),
  useAccess: () => ({}),
  useLocation: () => ({ pathname: '/finance/payables', search: '' }),
  useModel: () => ({ initialState: {} }),
}));

it('待退款天数：当天为今天', () => {
  expect(refundDays(dayjs().format('YYYY-MM-DDTHH:mm:ss'))).toBe('今天');
  expect(
    refundDays(dayjs().subtract(3, 'day').format('YYYY-MM-DDTHH:mm:ss')),
  ).toBe('3 天');
});

it('勾选待退款后按待退款筛选，欠款旁显示待退款', async () => {
  const params: Record<string, unknown>[] = [];
  (request as jest.Mock).mockImplementation(
    (url: string, opt?: { params?: Record<string, unknown> }) => {
      if (url === '/api/v1/finance/payables') {
        params.push(opt?.params ?? {});
        return Promise.resolve({
          data: {
            total: 1,
            records: [
              {
                id: 1,
                payableNo: 'AP20261116004',
                payeeType: 1,
                payeeName: '深圳工控',
                currencyCode: 'CNY',
                amount: 0,
                paidAmount: 2000,
                unpaidAmount: 0,
                creditAmount: 2000,
                refundExpectedAt: dayjs().format('YYYY-MM-DDTHH:mm:ss'),
                status: 4,
                statusName: '已关闭',
                installments: [],
                payments: [],
              },
            ],
          },
        });
      }
      return Promise.resolve({ data: { refundExpectedCount: 1 } });
    },
  );
  render(
    React.createElement(App, null, React.createElement(PayableManagement)),
  );
  expect(
    await screen.findByText('待退款 · 今天', {}, { timeout: 5000 }),
  ).toBeTruthy();
  await act(async () => {
    fireEvent.click(screen.getByRole('checkbox', { name: '待退款' }));
  });
  expect(params[params.length - 1].refundExpected).toBe(true);
});
