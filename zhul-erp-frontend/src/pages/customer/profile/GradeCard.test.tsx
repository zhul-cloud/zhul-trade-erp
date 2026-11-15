import { fireEvent, render, screen } from '@testing-library/react';
import { history } from '@umijs/max';
import { App } from 'antd';
import React from 'react';
import { RfmModal } from './GradeCard';
import { profileApi } from './service';

jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  request: jest.fn(),
}));
jest.mock('./service', () => ({
  ...jest.requireActual('./service'),
  profileApi: { rfm: jest.fn() },
}));

const h = React.createElement;

const RFM = {
  firstOrderDate: '2026-01-05',
  lastOrderDate: '2026-10-20',
  daysSinceLast: 26,
  ordersInYear: 1,
  usdInYear: 1000,
  usdTotal: 1000,
  r: 4,
  f: 2,
  m: 3,
  score: 3,
  calc: 2,
  adjust: 0,
  finalGrade: 2,
  followDays: 14,
  recencyDays: [30, 90, 180, 365],
  frequency: [12, 6, 3, 1],
  monetaryUsd: [100000, 50000, 10000, 1000],
  weights: [0.3, 0.3, 0.4],
  cutoffs: [4.2, 3.4, 2.6, 1.8],
  orders: [
    {
      id: 9,
      soNo: 'SO2026001',
      salesDate: '2026-10-20',
      currencyCode: 'USD',
      amount: 1000,
      usd: 1000,
      amountCny: 7100,
    },
  ],
};

/** audit UI-071：RFM 明细里点订单号跳页前先关弹窗，不能盖在销售订单页上 */
it('点订单号：先关掉 RFM 明细再跳到销售订单', async () => {
  (profileApi.rfm as jest.Mock).mockResolvedValue(RFM);
  const onClose = jest.fn();
  render(
    h(
      App,
      null,
      h(RfmModal, {
        open: true,
        customerId: 7,
        customerName: 'Pacific Controls',
        canAdjust: false,
        onAdjust: jest.fn(),
        onClose,
      }),
    ),
  );
  fireEvent.click(await screen.findByText('SO2026001'));
  expect(onClose).toHaveBeenCalled();
  expect(history.push).toHaveBeenCalledWith('/sales/orders/9');
});
