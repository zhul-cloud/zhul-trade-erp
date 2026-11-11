import { act, render, screen } from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import { outboundApi } from '@/pages/logistics/service';
import { PackDrawer } from './dialogs';

jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  Link: () => null,
  request: jest.fn(),
  useAccess: () => ({}),
  useLocation: () => ({ pathname: '/warehouse/outbounds', search: '' }),
  useModel: () => ({ initialState: {} }),
}));

jest.mock('@/pages/logistics/service', () => ({
  ...jest.requireActual('@/pages/logistics/service'),
  outboundApi: { detail: jest.fn(), pack: jest.fn() },
}));

const outbound = (id: number, obNo: string, model: string) => ({
  id,
  obNo,
  soId: 1,
  customerId: 1,
  forwarderId: 1,
  source: 1,
  sourceName: '发货通知',
  status: 1,
  statusName: '待打包',
  totalQuantity: 1,
  items: [{ id: id * 10, soItemId: 1, model, quantity: 1 }],
  boxes: [],
  packingAttachments: [],
});

const deferred = <T,>() => {
  let resolve!: (v: T) => void;
  const promise = new Promise<T>((r) => {
    resolve = r;
  });
  return { promise, resolve };
};

const view = (id?: number) =>
  React.createElement(
    App,
    null,
    React.createElement(PackDrawer, {
      id,
      onClose: () => undefined,
      onDone: () => undefined,
    }),
  );

/** UI-017：打开 A（慢）→ 关闭 → 打开 B（快）→ A 最后返回，抽屉始终是 B */
it('关闭或换单后，上一单迟到的详情不改写当前抽屉', async () => {
  const a = deferred<unknown>();
  const b = deferred<unknown>();
  (outboundApi.detail as jest.Mock).mockImplementation((id: number) =>
    id === 1 ? a.promise : b.promise,
  );
  const { rerender } = render(view(1));
  rerender(view(undefined));
  rerender(view(2));
  await act(async () => {
    b.resolve(outbound(2, 'OB-B', 'MODEL-B'));
  });
  await act(async () => {
    a.resolve(outbound(1, 'OB-A', 'MODEL-A'));
  });
  expect(screen.getAllByText(/OB-B/).length).toBeGreaterThan(0);
  expect(screen.queryByText(/OB-A/)).toBeNull();
  expect(screen.queryAllByText(/MODEL-A/)).toHaveLength(0);
});
