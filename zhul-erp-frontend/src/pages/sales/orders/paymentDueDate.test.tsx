import { act, fireEvent, render, screen } from '@testing-library/react';
import { App } from 'antd';
import dayjs from 'dayjs';
import React from 'react';
import { orderApi } from '../service';
import { CreateFeeOrderModal, PaymentDueDateModal } from './dialogs';

jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  Link: () => null,
  request: jest.fn(),
  useAccess: () => ({}),
  useLocation: () => ({ pathname: '/sales/orders/1', search: '' }),
  useModel: () => ({ initialState: {} }),
}));

jest.mock('../service', () => ({
  ...jest.requireActual('../service'),
  orderApi: { paymentDueDate: jest.fn(), createFeeOrder: jest.fn() },
}));

const order = {
  id: 1,
  soNo: 'SO20261008001',
  source: 2,
  currencyCode: 'USD',
  salesDate: '2026-10-07',
  paymentDueDate: '2026-11-10',
  totalAmount: 1333.65,
  receivedAmount: 667,
  remainingAmount: 666.65,
  exchangeRate: 7.1,
};

const wrap = (el: unknown) =>
  render(React.createElement(App, null, el as never));

const save = async (name: RegExp) => {
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name }));
  });
};

beforeEach(() => jest.clearAllMocks());

/** spec sales/sales-order「订单约定付款日」：修改弹窗带出原值，可以清空 */
it('修改约定付款日：显示金额，清空后保存传 null', async () => {
  (orderApi.paymentDueDate as jest.Mock).mockResolvedValue(order);
  const onDone = jest.fn();
  wrap(
    React.createElement(PaymentDueDateModal, {
      order: order as never,
      open: true,
      onClose: () => undefined,
      onDone,
    }),
  );
  expect(screen.getByText('USD 666.65')).toBeTruthy();
  expect(
    (screen.getByLabelText('约定付款日') as HTMLInputElement).value,
  ).toBe('2026-11-10');

  await save(/保\s*存/);
  expect(orderApi.paymentDueDate).toHaveBeenLastCalledWith(1, '2026-11-10');

  const clear = document.querySelector('.ant-picker-clear') as HTMLElement;
  fireEvent.mouseDown(clear);
  fireEvent.mouseUp(clear);
  fireEvent.click(clear);
  await save(/保\s*存/);
  expect(orderApi.paymentDueDate).toHaveBeenLastCalledWith(1, null);
  expect(onDone).toHaveBeenCalledTimes(2);
});

/** 新建费用订单时填的约定付款日随请求提交 */
it('新建费用订单提交约定付款日', async () => {
  (orderApi.createFeeOrder as jest.Mock).mockResolvedValue({
    ...order,
    id: 2,
    soNo: 'SO20261116001',
  });
  const due = dayjs().add(20, 'day').format('YYYY-MM-DD');
  wrap(
    React.createElement(CreateFeeOrderModal, {
      order: order as never,
      open: true,
      onClose: () => undefined,
      onDone: () => undefined,
    }),
  );
  fireEvent.change(screen.getByLabelText('费用金额'), {
    target: { value: '80' },
  });
  const input = screen.getByLabelText('约定付款日') as HTMLInputElement;
  fireEvent.mouseDown(input);
  fireEvent.change(input, { target: { value: due } });
  fireEvent.keyDown(input, { key: 'Enter', code: 'Enter', keyCode: 13 });
  await save(/创建费用订单/);
  expect(orderApi.createFeeOrder).toHaveBeenCalledWith(
    1,
    expect.objectContaining({ paymentDueDate: due }),
  );
});
