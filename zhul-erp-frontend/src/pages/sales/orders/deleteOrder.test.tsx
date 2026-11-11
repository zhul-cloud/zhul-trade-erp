import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import { orderApi } from '../service';
import { DeleteOrderModal } from './deleteOrder';

/** spec sales/sales-order「删除销售订单」：三档弹窗与删除按钮可用性 */
jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  request: jest.fn(),
}));
jest.mock('../service', () => ({
  ...jest.requireActual('../service'),
  orderApi: { deleteCheck: jest.fn(), remove: jest.fn() },
}));

const order = {
  id: 9,
  soNo: 'SO20261111005',
  source: 1,
  piNo: 'PI20261110002',
  status: 1,
  customerName: 'ACME Automation GmbH',
  totalAmount: 1341,
  currencyCode: 'USD',
};

const show = (onDeleted = jest.fn()) =>
  render(
    React.createElement(
      App,
      null,
      React.createElement(DeleteOrderModal, {
        order: order as never,
        open: true,
        onClose: () => undefined,
        onDeleted,
      }),
    ),
  );

beforeEach(() => jest.clearAllMocks());

it('直接删除：填了原因才能删', async () => {
  (orderApi.deleteCheck as jest.Mock).mockResolvedValue({
    level: 'DIRECT',
    blockers: [],
    related: [],
  });
  (orderApi.remove as jest.Mock).mockResolvedValue(undefined);
  const onDeleted = jest.fn();
  show(onDeleted);
  expect(await screen.findByText('没有关联单据，删除后不能恢复')).toBeTruthy();
  const del = screen.getByRole('button', { name: /删\s*除/ });
  expect((del as HTMLButtonElement).disabled).toBe(true);
  fireEvent.change(screen.getByLabelText('删除原因'), {
    target: { value: '录错客户' },
  });
  expect((del as HTMLButtonElement).disabled).toBe(false);
  fireEvent.click(del);
  await waitFor(() => expect(onDeleted).toHaveBeenCalled());
  expect(orderApi.remove).toHaveBeenCalledWith(9, '录错客户');
});

it('确认后删除：列出一起处理的单据', async () => {
  (orderApi.deleteCheck as jest.Mock).mockResolvedValue({
    level: 'CONFIRM',
    blockers: [],
    related: [
      {
        kind: '形式发票',
        no: 'PI20261110002',
        path: '/sales/pi/3',
        note: '回到「已发送」，可以再改',
      },
      { kind: '采购需求', no: '2 条（A、B）', path: null, note: '关闭' },
    ],
  });
  show();
  expect(await screen.findByText('删除后这些单据会一起处理')).toBeTruthy();
  expect(screen.getByText('回到「已发送」，可以再改')).toBeTruthy();
  expect(screen.getByLabelText('删除原因')).toBeTruthy();
});

it('不能删除：只列挡住的单据，没有原因和删除按钮', async () => {
  (orderApi.deleteCheck as jest.Mock).mockResolvedValue({
    level: 'BLOCKED',
    blockers: [
      {
        kind: '采购订单',
        no: 'PO20261111001 · 已下单',
        path: '/purchase/orders/5',
        note: '先取消采购订单',
      },
    ],
    related: [],
  });
  show();
  expect(await screen.findByText('订单 SO20261111005 不能删除')).toBeTruthy();
  expect(screen.getByText('先取消采购订单')).toBeTruthy();
  expect(screen.queryByLabelText('删除原因')).toBeNull();
  expect(screen.queryByRole('button', { name: /删\s*除/ })).toBeNull();
  expect(screen.getByRole('button', { name: '知道了' })).toBeTruthy();
});
