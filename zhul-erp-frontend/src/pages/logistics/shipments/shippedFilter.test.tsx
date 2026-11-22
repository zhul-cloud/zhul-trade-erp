import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import { logisticsApi } from '../service';
import Shipments from './index';

/** 出运管理「出运日期」筛选（规范 logistics/shipment 出运单页；openspec ui-redesign 任务 7.8） */
jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  Link: ({ children }: { children: unknown }) => children,
  request: jest.fn(),
  useAccess: () => ({}),
  useLocation: () => ({ pathname: '/logistics/shipments', search: '' }),
  useModel: () => ({ initialState: {} }),
}));
jest.mock('../service', () => ({
  ...jest.requireActual('../service'),
  forwarderApi: { list: jest.fn(() => Promise.resolve([])) },
  logisticsApi: {
    page: jest.fn(() => Promise.resolve({ records: [], total: 0 })),
    pending: jest.fn(() => Promise.resolve([])),
  },
}));

const lastQuery = () => {
  const calls = (logisticsApi.page as jest.Mock).mock.calls;
  return calls[calls.length - 1][0];
};

/** 在日期框里输入日期并回车 */
const typeDate = (input: HTMLElement, value: string) => {
  fireEvent.mouseDown(input);
  fireEvent.focus(input);
  fireEvent.change(input, { target: { value } });
  fireEvent.keyDown(input, { key: 'Enter', code: 'Enter', keyCode: 13 });
};

it('出运日期点「查询」才生效，按 YYYY-MM-DD 传参，计入更多筛选；重置后清空', async () => {
  render(React.createElement(App, null, React.createElement(Shipments)));
  await waitFor(() => expect(logisticsApi.page).toHaveBeenCalled());

  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: /更多筛选/ }));
  });
  const [from, to] = screen.getAllByLabelText('出运日期');
  await act(async () => {
    typeDate(from, '2026-11-01');
  });
  await act(async () => {
    typeDate(to, '2026-11-30');
  });
  expect(lastQuery().shippedFrom).toBeUndefined();

  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: /查\s*询/ }));
  });
  await waitFor(() =>
    expect(lastQuery()).toMatchObject({
      shippedFrom: '2026-11-01',
      shippedTo: '2026-11-30',
    }),
  );
  expect(screen.getByRole('button', { name: /更多筛选 · 1/ })).toBeTruthy();

  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: /重\s*置/ }));
  });
  await waitFor(() => {
    expect(lastQuery().shippedFrom).toBeUndefined();
    expect(lastQuery().shippedTo).toBeUndefined();
  });
});
