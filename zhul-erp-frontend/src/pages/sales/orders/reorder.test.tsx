import { act, render, screen } from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import { orderApi } from '../service';
import { CreateOrderDrawer } from './dialogs';

jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  Link: () => null,
  request: jest.fn().mockResolvedValue({ data: [] }),
  useAccess: () => ({}),
  useLocation: () => ({ pathname: '/sales/orders', search: '' }),
  useModel: () => ({ initialState: {} }),
}));

jest.mock('../service', () => ({
  ...jest.requireActual('../service'),
  orderApi: { reorder: jest.fn(), create: jest.fn() },
}));

/** spec sales/sales-order「再下一单」：按原订单预填，没有商品档案的型号要先建档才能创建 */
it('再下一单：预填原订单内容，未建档的型号拦住创建', async () => {
  (orderApi.reorder as jest.Mock).mockResolvedValue({
    sourceId: 9,
    sourceSoNo: 'SO20261109004',
    customerId: 5,
    customerName: 'ACROBOT TECHNOLOGIES',
    customerCountry: 'India',
    currencyCode: 'USD',
    items: [
      {
        productId: 11,
        mpnDisplay: '6ES7214-1AG40-0XB0',
        brandName: 'Siemens',
        categoryName: 'PLC',
        model: '6ES7214-1AG40-0XB0',
        quantity: 1,
        unitPrice: 740.44,
        stockType: 1,
      },
      {
        productId: null,
        model: '6SL3210-1KE21-3UF1',
        brand: 'Siemens',
        quantity: 2,
        unitPrice: 54.39,
        stockType: 1,
      },
    ],
    fees: [{ feeName: 'Shipping Cost', amount: 60 }],
  });
  await act(async () => {
    render(
      React.createElement(
        App,
        null,
        React.createElement(CreateOrderDrawer, {
          open: true,
          reorderFrom: 9,
          onClose: () => undefined,
          onDone: () => undefined,
        }),
      ),
    );
  });
  expect(orderApi.reorder).toHaveBeenCalledWith(9);
  expect(screen.getByText('再下一单')).toBeTruthy();
  expect(screen.getByText(/已复制 SO20261109004/)).toBeTruthy();
  expect(screen.getByText(/6SL3210-1KE21-3UF1 还没有商品档案/)).toBeTruthy();
  const create = screen.getByRole('button', { name: /创建订单/ });
  expect((create as HTMLButtonElement).disabled).toBe(true);
});
