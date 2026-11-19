import { act, fireEvent, render, screen } from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import { orderApi } from '../service';
import { CreateOrderDrawer, ReorderPickModal } from './dialogs';

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

const DRAFT = {
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
};

/** spec sales/sales-order「再下一单」：按选好的内容预填，没有商品档案的型号要先建档才能创建 */
it('再下一单：预填原订单内容，未建档的型号拦住创建', async () => {
  await act(async () => {
    render(
      React.createElement(
        App,
        null,
        React.createElement(CreateOrderDrawer, {
          open: true,
          reorder: DRAFT,
          onClose: () => undefined,
          onDone: () => undefined,
        }),
      ),
    );
  });
  expect(screen.getByText('再下一单')).toBeTruthy();
  expect(screen.getByText(/已复制 SO20261109004/)).toBeTruthy();
  expect(screen.getByText(/6SL3210-1KE21-3UF1 还没有商品档案/)).toBeTruthy();
  const create = screen.getByRole('button', { name: /创建订单/ });
  expect((create as HTMLButtonElement).disabled).toBe(true);
});

/** openspec feedback-1119：先选择型号，默认全选；取消一个型号、不带费用，只把勾选的交给下单表单 */
it('再下一单：选择型号，只带勾选的型号与选择的费用', async () => {
  (orderApi.reorder as jest.Mock).mockResolvedValue(DRAFT);
  const onNext = jest.fn();
  await act(async () => {
    render(
      React.createElement(
        App,
        null,
        React.createElement(ReorderPickModal, {
          soId: 9,
          onCancel: () => undefined,
          onNext,
        }),
      ),
    );
  });
  expect(orderApi.reorder).toHaveBeenCalledWith(9);
  expect(screen.getByText(/下一步（2 个型号）/)).toBeTruthy();
  const boxes = screen.getAllByRole('checkbox');
  // 第 1 个是表头全选，第 2、3 个是型号，最后一个是费用
  fireEvent.click(boxes[2]);
  fireEvent.click(screen.getByRole('checkbox', { name: /普通费用一起带入/ }));
  expect(screen.getByText(/已选 1 \/ 2/)).toBeTruthy();
  fireEvent.click(screen.getByRole('button', { name: /下一步（1 个型号）/ }));
  expect(onNext).toHaveBeenCalledWith(
    expect.objectContaining({
      items: [expect.objectContaining({ model: '6ES7214-1AG40-0XB0' })],
      fees: [],
    }),
  );

  // 全部取消勾选时不能继续
  // 表头全选：先全选，再全部取消
  fireEvent.click(screen.getAllByRole('checkbox')[0]);
  fireEvent.click(screen.getAllByRole('checkbox')[0]);
  expect(screen.getByText(/已选 0 \/ 2/)).toBeTruthy();
  expect(
    (screen.getByRole('button', { name: /下一步/ }) as HTMLButtonElement)
      .disabled,
  ).toBe(true);
});
