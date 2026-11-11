import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import { payableApi } from '@/pages/finance/payables/service';
import { CompleteModal, ShipModal } from './dialogs';
import { PR_SOURCE, purchaseReturnApi } from './service';

/** spec purchase/purchase-return：寄出必填快递，有运费才选垫付人或公司支付；售后退货完成必须填退款或抵扣 */
jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  request: jest.fn(),
}));
jest.mock('@/pages/sales/orders/dialogs', () => ({ useUserOptions: () => [] }));
jest.mock('@/pages/warehouse/components', () => ({
  AttachmentWall: () => null,
}));
jest.mock('@/pages/finance/payables/service', () => ({
  ...jest.requireActual('@/pages/finance/payables/service'),
  payableApi: { bySource: jest.fn() },
}));
jest.mock('./service', () => ({
  ...jest.requireActual('./service'),
  purchaseReturnApi: { ship: jest.fn(), complete: jest.fn() },
}));

const data = {
  id: 5,
  returnNo: 'PR20261111001',
  sourceType: PR_SOURCE.AFTER_SALES,
  poId: 8,
  currencyCode: 'CNY',
};

const wrap = (el: unknown) =>
  render(React.createElement(App, null, el as never));

beforeEach(() => jest.clearAllMocks());

it('寄出：缺快递不提交；填了运费勾公司支付，不带垫付人', async () => {
  (purchaseReturnApi.ship as jest.Mock).mockResolvedValue({});
  const onDone = jest.fn();
  wrap(
    React.createElement(ShipModal, {
      data: data as never,
      onClose: () => undefined,
      onDone,
    }),
  );
  const ok = screen.getByRole('button', { name: /寄\s*出/ });
  fireEvent.click(ok);
  expect(
    await screen.findByText('请填写寄出日期、快递公司和快递单号'),
  ).toBeTruthy();
  expect(purchaseReturnApi.ship).not.toHaveBeenCalled();

  expect(screen.queryByText('公司支付（不进待报销）')).toBeNull();
  fireEvent.change(screen.getByLabelText('快递公司'), {
    target: { value: '顺丰' },
  });
  fireEvent.change(screen.getByLabelText('快递单号'), {
    target: { value: 'SF7788' },
  });
  fireEvent.change(screen.getByLabelText('运费'), { target: { value: '45' } });
  fireEvent.blur(screen.getByLabelText('运费'));
  fireEvent.click(await screen.findByText('公司支付（不进待报销）'));
  fireEvent.click(ok);
  await waitFor(() => expect(onDone).toHaveBeenCalled());
  expect(purchaseReturnApi.ship).toHaveBeenCalledWith(
    5,
    expect.objectContaining({
      carrier: '顺丰',
      trackingNo: 'SF7788',
      freightCny: 45,
      companyPaid: true,
      payerId: undefined,
    }),
  );
});

it('完成：售后退货必须填退款或抵扣，填 0 也可以', async () => {
  (payableApi.bySource as jest.Mock).mockRejectedValue(new Error('无权限'));
  (purchaseReturnApi.complete as jest.Mock).mockResolvedValue({});
  const onDone = jest.fn();
  wrap(
    React.createElement(CompleteModal, {
      data: data as never,
      onClose: () => undefined,
      onDone,
    }),
  );
  const ok = screen.getByRole('button', { name: /完\s*成/ });
  fireEvent.click(ok);
  expect(
    await screen.findByText('请填写供应商退款或抵扣金额（没有就填 0）'),
  ).toBeTruthy();
  expect(purchaseReturnApi.complete).not.toHaveBeenCalled();

  fireEvent.change(screen.getByLabelText('供应商退款或抵扣'), {
    target: { value: '0' },
  });
  fireEvent.blur(screen.getByLabelText('供应商退款或抵扣'));
  fireEvent.click(ok);
  await waitFor(() => expect(onDone).toHaveBeenCalled());
  expect(purchaseReturnApi.complete).toHaveBeenCalledWith(
    5,
    expect.objectContaining({ refundAmount: 0 }),
  );
});
