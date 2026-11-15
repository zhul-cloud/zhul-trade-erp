import { act, fireEvent, render, screen } from '@testing-library/react';
import { request } from '@umijs/max';
import { App } from 'antd';
import React from 'react';
import OverpaidTab from './OverpaidTab';

jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  Link: () => null,
  request: jest.fn(),
  useAccess: () => ({ 'sales:pi:receipt-confirm': true }),
  useLocation: () => ({ pathname: '/finance/receipts', search: '' }),
  useModel: () => ({ initialState: {} }),
}));

/** BUS-058：原路退回改成部分金额时，默认实际退出人民币按 HALF_UP 两位，与后端锁定价值一致 */
it('原路退回 1.20 × 7.0125 默认带出 8.42（浮点会算成 8.41）', async () => {
  (request as jest.Mock).mockImplementation((url: string) => {
    if (url.endsWith('/overpaid')) {
      return Promise.resolve({
        data: [
          {
            ownerType: 'PI',
            ownerId: 1,
            ownerNo: 'PI001',
            customerId: 1,
            customerName: 'ACME',
            currencyCode: 'USD',
            total: 1000,
            received: 1050,
            overpaid: 50,
            pending: 50,
            rate: 7.0125,
            pendingCny: 350.63,
            platformReceipts: [
              {
                platform: '阿里信保',
                platformOrderNo: 'TA1',
                amount: 1050,
                receiptDate: '2026-11-01',
              },
            ],
          },
        ],
      });
    }
    return Promise.resolve({ data: [] });
  });
  render(React.createElement(App, null, React.createElement(OverpaidTab)));
  const refund = await screen.findByText('退回客户', {}, { timeout: 5000 });
  await act(async () => {
    fireEvent.click(refund);
  });
  fireEvent.change(screen.getByLabelText('金额'), { target: { value: '1.2' } });
  expect(
    (screen.getByLabelText('实际退出人民币') as HTMLInputElement).value,
  ).toBe('8.42');
});
