import { act, fireEvent, render, screen } from '@testing-library/react';
import { App } from 'antd';
import dayjs from 'dayjs';
import React from 'react';
import { bankOptionsApi, piApi } from '../service';
import { ConfirmReceiptModal } from './dialogs';

jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  Link: () => null,
  request: jest.fn().mockResolvedValue({ data: [] }),
  useAccess: () => ({}),
  useLocation: () => ({ pathname: '/sales/pi/1', search: '' }),
  useModel: () => ({ initialState: {} }),
}));

jest.mock('@/utils/businessUnit', () => ({
  ...jest.requireActual('@/utils/businessUnit'),
  useBusinessUnits: () => [{ enabled: false, units: [] }, () => undefined],
}));

jest.mock('../service', () => ({
  ...jest.requireActual('../service'),
  piApi: { receiptSettings: jest.fn() },
  bankOptionsApi: { options: jest.fn() },
}));

// 只给登记到账用到的字段
const owner = (confirmReceipt: unknown) =>
  ({
    no: 'PI20261118001',
    currencyCode: 'USD',
    exchangeRate: 7.15,
    totalAmount: 532,
    receivedAmount: 0,
    remainingAmount: 532,
    receipts: [
      {
        id: 7,
        kind: 1,
        status: 1,
        matched: false,
        amount: 532,
        receiptDate: '2026-11-18',
        paymentMethod: 'TT',
      },
    ],
    confirmReceipt,
  }) as never;

const renderModal = async (confirmReceipt: unknown) => {
  (piApi.receiptSettings as jest.Mock).mockResolvedValue({ feeTolerance: 50 });
  (bankOptionsApi.options as jest.Mock).mockResolvedValue([
    {
      id: 3,
      bankName: 'Test Bank (USD)',
      accountNoMasked: '****0001',
      accountType: 1,
      currencyCode: 'USD',
      isDefault: true,
    },
  ]);
  await act(async () => {
    render(
      React.createElement(
        App,
        null,
        React.createElement(ConfirmReceiptModal, {
          owner: owner(confirmReceipt),
          open: true,
          onClose: () => undefined,
          onDone: () => undefined,
        }),
      ),
    );
  });
};

/** ui-redesign 6.3：登记到账只改样式，提交的内容不变 */
it('默认带出待确认水单，结清时提交金额、日期、账户、水单与付款方式', async () => {
  const confirm = jest.fn().mockResolvedValue({ receiptStatusName: '已到账' });
  await renderModal(confirm);
  expect(screen.getByText('本次到账后结清')).toBeTruthy();
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: /登\s*记/ }));
  });
  expect(confirm).toHaveBeenCalledWith({
    amount: 532,
    receiptDate: dayjs().format('YYYY-MM-DD'),
    bankAccountId: 3,
    slipId: 7,
    feeDiff: false,
    paymentMethod: 'TT',
    note: undefined,
  });
});

it('差额在容差内可勾选记为手续费，勾选后随提交带上', async () => {
  const confirm = jest.fn().mockResolvedValue({ receiptStatusName: '已到账' });
  await renderModal(confirm);
  await act(async () => {
    fireEvent.change(screen.getByRole('spinbutton', { name: '到账金额' }), {
      target: { value: '500' },
    });
  });
  expect(screen.getByText(/本次到账后还差 USD 32\.00/)).toBeTruthy();
  await act(async () => {
    fireEvent.click(
      screen.getByRole('checkbox', { name: /差额记为银行中转手续费/ }),
    );
  });
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: /登\s*记/ }));
  });
  expect(confirm).toHaveBeenCalledWith(
    expect.objectContaining({ amount: 500, feeDiff: true }),
  );
});

/** 多收：先弹确认，确认后才提交（help-texts：确认框原样保留） */
it('多收时先确认，确认后再提交', async () => {
  const confirm = jest.fn().mockResolvedValue({ receiptStatusName: '已到账' });
  await renderModal(confirm);
  await act(async () => {
    fireEvent.change(screen.getByRole('spinbutton', { name: '到账金额' }), {
      target: { value: '600' },
    });
  });
  expect(screen.getByText(/多收 USD 68\.00，将记为预收/)).toBeTruthy();
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: /登\s*记/ }));
  });
  expect(confirm).not.toHaveBeenCalled();
  // 弹出了确认框
  expect(screen.getByRole('button', { name: '继续登记' })).toBeTruthy();
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: '继续登记' }));
  });
  expect(confirm).toHaveBeenCalledWith(
    expect.objectContaining({ amount: 600 }),
  );
});

it('没有到账金额时登记按钮置灰；差额超过容差时提示可记为手续费的上限', async () => {
  const confirm = jest.fn();
  await renderModal(confirm);
  await act(async () => {
    fireEvent.change(screen.getByRole('spinbutton', { name: '到账金额' }), {
      target: { value: '400' },
    });
  });
  expect(screen.getByText(/差额不超过 USD 50\.00 时可记为手续费/)).toBeTruthy();
  expect(screen.queryByRole('checkbox', { name: /差额记为/ })).toBeNull();
  await act(async () => {
    fireEvent.change(screen.getByRole('spinbutton', { name: '到账金额' }), {
      target: { value: '' },
    });
  });
  expect(
    (screen.getByRole('button', { name: /登\s*记/ }) as HTMLButtonElement)
      .disabled,
  ).toBe(true);
});
