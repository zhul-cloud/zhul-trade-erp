import { render, screen } from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import { ShipNoticeModal } from './shipNotice';

jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  Link: () => null,
  request: jest.fn(),
  useAccess: () => ({}),
  useLocation: () => ({ pathname: '/sales/orders/1', search: '' }),
  useModel: () => ({ initialState: {} }),
}));

const mockForwarders = jest.fn();
jest.mock('@/pages/logistics/service', () => ({
  ...jest.requireActual('@/pages/logistics/service'),
  outboundApi: {
    noticeForm: () =>
      Promise.resolve({
        soId: 1,
        soNo: 'SO1',
        customerName: '客户',
        lines: [
          {
            soItemId: 11,
            model: 'ATV71HD30N4',
            quantity: 1,
            tracked: true,
            occupied: 0,
            available: 1,
            current: 0,
          },
        ],
      }),
  },
  forwarderApi: { list: () => mockForwarders() },
}));

const open = () =>
  render(
    React.createElement(
      App,
      null,
      React.createElement(ShipNoticeModal, {
        open: true,
        soId: 1,
        onClose: () => undefined,
        onDone: () => undefined,
      }),
    ),
  );

/** 有多家货代时不会默认选中：发通知不可点，要说清楚是没选货代 */
it('没选货代时提示原因并禁用发通知', async () => {
  mockForwarders.mockResolvedValue([
    { id: 1, name: '货代甲' },
    { id: 2, name: '货代乙' },
  ]);
  open();
  expect(await screen.findByText('先选货代')).toBeTruthy();
  expect(
    screen.getByRole('button', { name: /发通知/ }).hasAttribute('disabled'),
  ).toBe(true);
});

it('只有一家货代时自动选中，可以直接发通知', async () => {
  mockForwarders.mockResolvedValue([{ id: 1, name: '货代甲' }]);
  open();
  await screen.findByText('ATV71HD30N4');
  expect(screen.queryByText('先选货代')).toBeNull();
  expect(
    screen.getByRole('button', { name: /发通知/ }).hasAttribute('disabled'),
  ).toBe(false);
});
