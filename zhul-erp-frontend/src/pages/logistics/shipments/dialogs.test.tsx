import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import { logisticsApi } from '../service';
import { ShipModal } from './dialogs';

/** openspec ship-forwarder-choice：登记出运时选择交给谁，按选择切换必填项；修改运单不能切换 */
jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  request: jest.fn(),
}));
jest.mock('../service', () => ({
  ...jest.requireActual('../service'),
  logisticsApi: { ship: jest.fn() },
}));

const sh = {
  id: 7,
  shNo: 'SH20261119005',
  status: 1,
  forwarderName: 'Maggie YUAN',
  customerForwarder: false,
  volumeDivisor: 5000,
  reconciled: false,
};

const open = (over: Record<string, unknown> = {}) =>
  render(
    React.createElement(
      App,
      null,
      React.createElement(ShipModal, {
        sh: { ...sh, ...over } as never,
        open: true,
        onClose: () => undefined,
        onDone: () => undefined,
      }),
    ),
  );

beforeEach(() => jest.clearAllMocks());

it('默认我们的货代：运单号必填；改选客户的货代后只填日期即可确认已交货代', async () => {
  (logisticsApi.ship as jest.Mock).mockResolvedValue(sh);
  open();
  expect(
    screen.getByRole('button', { name: /确认出运/ }).hasAttribute('disabled'),
  ).toBe(true);
  fireEvent.click(screen.getByText('客户的货代'));
  const ok = screen.getByRole('button', { name: /确认已交货代/ });
  expect(ok.hasAttribute('disabled')).toBe(false);
  fireEvent.click(ok);
  await waitFor(() => expect(logisticsApi.ship).toHaveBeenCalled());
  expect((logisticsApi.ship as jest.Mock).mock.calls[0][1]).toMatchObject({
    customerForwarder: true,
  });
});

it('货代绑定了客户时默认选客户的货代', () => {
  open({ customerForwarder: true });
  expect(
    (screen.getByRole('radio', { name: /客户的货代/ }) as HTMLInputElement)
      .checked,
  ).toBe(true);
});

it('修改运单：交给谁不能切换，也不提交选择', async () => {
  (logisticsApi.ship as jest.Mock).mockResolvedValue(sh);
  open({ status: 2, customerForwarder: true, shippedDate: '2026-11-19' });
  expect(
    (screen.getByRole('radio', { name: /我们的货代/ }) as HTMLInputElement)
      .disabled,
  ).toBe(true);
  fireEvent.click(screen.getByRole('button', { name: /保\s*存/ }));
  await waitFor(() => expect(logisticsApi.ship).toHaveBeenCalled());
  expect((logisticsApi.ship as jest.Mock).mock.calls[0][1]).not.toHaveProperty(
    'customerForwarder',
  );
});
