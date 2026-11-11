import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import { refundRequestApi } from '@/pages/finance/refunds/service';
import { salesReturnApi } from '@/pages/sales/after-sales/service';
import { ConfirmReturnModal, CreateReturnModal, lineRefund } from './dialogs';

/** spec sales/after-sales「处理方式」：退货退款选型号、预计退款、先退款 */
jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  request: jest.fn(),
}));
jest.mock('@/pages/sales/after-sales/service', () => ({
  ...jest.requireActual('@/pages/sales/after-sales/service'),
  salesReturnApi: { create: jest.fn(), confirm: jest.fn() },
}));
jest.mock('@/pages/finance/refunds/service', () => ({
  ...jest.requireActual('@/pages/finance/refunds/service'),
  refundRequestApi: { refundable: jest.fn() },
}));
jest.mock('@/utils/dict', () => ({
  useDictTexts: () => [{ code: 'QUALITY', name: '质量问题' }],
}));

const items = [
  {
    soItemId: 11,
    model: '6ES7214',
    brand: 'Siemens',
    shippedQty: 3,
    returnable: 3,
    unitPrice: 33.335,
  },
  {
    soItemId: 12,
    model: 'LC1D25M7',
    shippedQty: 0,
    returnable: 0,
    unitPrice: 18.5,
  },
];

const show = (handling: number, onDone = jest.fn()) =>
  render(
    React.createElement(
      App,
      null,
      React.createElement(CreateReturnModal, {
        open: true,
        handling,
        afterSalesId: 7,
        asNo: 'AS20261111001',
        currencyCode: 'USD',
        items,
        onClose: () => undefined,
        onDone,
      }),
    ),
  );

beforeEach(() => jest.clearAllMocks());

it('预计退款按数量 × 成交单价四舍五入到分', () => {
  expect(lineRefund(3, 33.335)).toBe(100.01);
  expect(lineRefund(0, 33.335)).toBe(0);
  expect(lineRefund(2)).toBe(0);
});

it('退货退款：没出运的型号不能填，勾「先退款」后提交', async () => {
  (salesReturnApi.create as jest.Mock).mockResolvedValue({
    id: 1,
    returnNo: 'TH20261111001',
  });
  const onDone = jest.fn();
  show(2, onDone);
  expect(
    (screen.getByLabelText('LC1D25M7 退货数量') as HTMLInputElement).disabled,
  ).toBe(true);

  fireEvent.click(screen.getByRole('button', { name: '生成销售退货单' }));
  expect(await screen.findByText('请填写退货数量')).toBeTruthy();
  expect(salesReturnApi.create).not.toHaveBeenCalled();

  const qty = screen.getByLabelText('6ES7214 退货数量');
  fireEvent.change(qty, { target: { value: '3' } });
  fireEvent.blur(qty);
  expect(await screen.findAllByText(/100\.01/)).not.toHaveLength(0);
  fireEvent.click(screen.getByText('先退款'));
  fireEvent.click(screen.getByRole('button', { name: '生成销售退货单' }));
  await waitFor(() => expect(onDone).toHaveBeenCalled());
  expect(salesReturnApi.create).toHaveBeenCalledWith(
    expect.objectContaining({
      afterSalesId: 7,
      handling: 2,
      refundFirst: true,
      items: [{ soItemId: 11, quantity: 3 }],
    }),
  );
});

it('仅退货：没有先退款与预计退款', () => {
  show(1);
  expect(screen.queryByText('先退款')).toBeNull();
  expect(screen.queryByText(/预计退款/)).toBeNull();
});

it('确认退货退款：每行默认 数量 × 成交单价，可改小，按行提交', async () => {
  (refundRequestApi.refundable as jest.Mock).mockResolvedValue({
    soId: 3,
    currencyCode: 'USD',
    available: 500,
    platformOnly: false,
  });
  (salesReturnApi.confirm as jest.Mock).mockResolvedValue({
    receipt: { no: 'RR20261111001' },
    refunds: [{ requestNo: 'RA20261111001' }],
  });
  const onDone = jest.fn();
  const data = {
    id: 21,
    returnNo: 'TH20261111001',
    handling: 2,
    soId: 3,
    currencyCode: 'USD',
    items: [
      { id: 31, model: '6ES7214', quantity: 2, unitPrice: 100 },
      { id: 32, model: 'LC1D25M7', quantity: 1, unitPrice: 18.5 },
    ],
  };
  render(
    React.createElement(
      App,
      null,
      React.createElement(ConfirmReturnModal, {
        data: data as never,
        onClose: () => undefined,
        onDone,
      }),
    ),
  );
  const first = screen.getByLabelText('6ES7214 退款金额') as HTMLInputElement;
  expect(first.value).toBe('200.00');
  expect(
    (screen.getByLabelText('LC1D25M7 退款金额') as HTMLInputElement).value,
  ).toBe('18.50');
  fireEvent.change(first, { target: { value: '150' } });
  fireEvent.blur(first);
  fireEvent.click(screen.getByRole('button', { name: '确认退货' }));
  await waitFor(() => expect(onDone).toHaveBeenCalled());
  expect(salesReturnApi.confirm).toHaveBeenCalledWith(
    21,
    expect.objectContaining({
      lines: [
        { returnItemId: 31, amount: 150 },
        { returnItemId: 32, amount: 18.5 },
      ],
    }),
  );
});
