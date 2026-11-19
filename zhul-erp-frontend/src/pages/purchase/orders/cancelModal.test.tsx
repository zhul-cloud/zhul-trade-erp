import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { request } from '@umijs/max';
import { App } from 'antd';
import React from 'react';
import { CancelModal } from './detail';

/** openspec po-cancel-refund：有付款时要选是否退款，并逐笔显示处理方式；没有付款时只填原因 */
jest.mock('@umijs/max', () => ({
  history: { push: jest.fn(), listen: jest.fn() },
  request: jest.fn(),
  useAccess: () => ({}),
  useParams: () => ({ id: '1' }),
  useLocation: () => ({ pathname: '/purchase/orders/1', search: '' }),
  useModel: () => ({ initialState: {} }),
  Link: (p: { children: unknown }) => p.children,
}));

const open = (onConfirm: jest.Mock) =>
  render(
    React.createElement(
      App,
      null,
      React.createElement(CancelModal, {
        open: true,
        poId: 1,
        onClose: () => undefined,
        onConfirm,
      }),
    ),
  );

const okButton = () => screen.getByRole('button', { name: /确认取消/ });

beforeEach(() => jest.clearAllMocks());

it('有付款：不选是否退款不能取消；选会退款后显示作废垫付并带上选择', async () => {
  (request as jest.Mock).mockResolvedValue({
    data: [
      {
        paymentId: 9,
        paymentNo: 'PY20261119001',
        method: 2,
        advanceUser: 'johnny',
        reimburseStatus: 1,
        currencyCode: 'CNY',
        amount: 700,
        action: 1,
      },
    ],
  });
  const onConfirm = jest.fn(() => Promise.resolve());
  open(onConfirm);
  await screen.findByText('PY20261119001');
  fireEvent.change(screen.getByLabelText('取消原因'), {
    target: { value: '客户要换型号' },
  });
  expect(okButton().hasAttribute('disabled')).toBe(true);
  fireEvent.click(screen.getByText('会退款'));
  expect(screen.getByText('作废这笔垫付，不再报销')).toBeTruthy();
  expect(okButton().hasAttribute('disabled')).toBe(false);
  fireEvent.click(okButton());
  await waitFor(() =>
    expect(onConfirm).toHaveBeenCalledWith('客户要换型号', true),
  );
});

it('没有付款：不出现退款选择，填原因即可取消', async () => {
  (request as jest.Mock).mockResolvedValue({ data: [] });
  const onConfirm = jest.fn(() => Promise.resolve());
  open(onConfirm);
  await screen.findByText(/各型号数量回到需求池/);
  expect(screen.queryByText('会退款')).toBeNull();
  fireEvent.change(screen.getByLabelText('取消原因'), {
    target: { value: '供应商缺货' },
  });
  fireEvent.click(okButton());
  await waitFor(() =>
    expect(onConfirm).toHaveBeenCalledWith('供应商缺货', undefined),
  );
});
