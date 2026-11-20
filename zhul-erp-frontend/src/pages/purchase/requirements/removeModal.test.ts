import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import { requirementApi } from '../service';
import { RemoveModal } from './index';

jest.mock('@umijs/max', () => ({
  request: jest.fn(),
  history: { push: jest.fn() },
  useAccess: () => ({}),
}));
jest.mock('../service', () => ({
  ...jest.requireActual('../service'),
  requirementApi: { remove: jest.fn().mockResolvedValue(undefined) },
}));

const remove = requirementApi.remove as jest.Mock;

const req = (over: Record<string, unknown>) =>
  ({
    id: 7,
    soId: 1,
    soNo: 'SO20261120001',
    customerName: 'ACROBOT',
    model: '1756-PB72',
    brand: 'AB',
    quantity: 100,
    draftQty: 0,
    orderedQty: 0,
    availableQty: 100,
    status: 'pending',
    statusName: '待下单',
    purchaseOrders: [],
    createTime: '',
    updateTime: '',
    ...over,
  }) as never;

const h = React.createElement;

const open = (target: never, onDone = jest.fn()) => {
  render(h(App, null, h(RemoveModal, { target, onClose: jest.fn(), onDone })));
  return onDone;
};

beforeEach(() => remove.mockClear());

test('草稿中的需求整条删除：提示草稿一并移除，提交原因与说明', async () => {
  const done = open(req({ draftQty: 100, status: 'draft' }));
  expect(screen.getByText('删除采购需求')).toBeTruthy();
  expect(screen.getByText(/草稿采购订单上的这个型号一并移除/)).toBeTruthy();
  fireEvent.change(screen.getByLabelText('删除说明'), {
    target: { value: '仓库有 100 个现货' },
  });
  fireEvent.click(screen.getByRole('button', { name: '删 除' }));
  await waitFor(() => expect(done).toHaveBeenCalled());
  expect(remove).toHaveBeenCalledWith(7, {
    reason: 'stock',
    note: '仓库有 100 个现货',
  });
});

test('部分下单只删没下单的数量', async () => {
  const done = open(req({ orderedQty: 60, status: 'partial' }));
  expect(screen.getByText('删除没下单的数量')).toBeTruthy();
  expect(screen.getByText(/已下单的 60 个不受影响/)).toBeTruthy();
  fireEvent.click(screen.getByText('在系统外采购'));
  fireEvent.click(screen.getByRole('button', { name: '删除 40 个' }));
  await waitFor(() => expect(done).toHaveBeenCalled());
  expect(remove).toHaveBeenCalledWith(7, { reason: 'outside', note: '' });
});

test('选「其他」没填说明不提交', async () => {
  open(req({}));
  fireEvent.click(screen.getByText('其他'));
  fireEvent.click(screen.getByRole('button', { name: '删 除' }));
  expect(await screen.findByText('请填写删除说明')).toBeTruthy();
  expect(remove).not.toHaveBeenCalled();
});
