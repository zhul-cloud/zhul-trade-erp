import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { request } from '@umijs/max';
import { App } from 'antd';
import React from 'react';
import Outbounds from './outbounds';
import Receipts from './receipts';

/** openspec doc-list-unify：状态放在搜索条件栏的下拉，主内容区放主操作 */
jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  Link: ({ children }: { children?: unknown }) => children ?? null,
  request: jest.fn(),
  useAccess: () => ({
    'warehouse:receipt:create': true,
    'warehouse:outbound:pack': true,
    'logistics:outbound:pack': true,
  }),
  useLocation: () => ({ pathname: '/warehouse', search: '' }),
  useModel: () => ({ initialState: {} }),
}));

const page = (records: unknown[] = []) => ({ data: { total: records.length, records } });

const pickStatus = async (label: RegExp) => {
  const box = screen.getByRole('combobox', { name: '状态' });
  await act(async () => {
    fireEvent.mouseDown(box);
  });
  const opt = await screen.findAllByText(label);
  await act(async () => {
    fireEvent.click(opt[opt.length - 1]);
  });
};

it('采购入库：状态在搜索条件栏，切到已入库请求入库单', async () => {
  const urls: string[] = [];
  (request as jest.Mock).mockImplementation((url: string) => {
    urls.push(url);
    if (url.endsWith('/counts')) return Promise.resolve({ data: { pending: 3 } });
    return Promise.resolve(page());
  });
  render(React.createElement(App, null, React.createElement(Receipts)));
  expect(await screen.findByText('待收货 · 3')).toBeTruthy();
  expect(screen.getByRole('button', { name: /直接收货/ })).toBeTruthy();
  await pickStatus(/^已入库$/);
  await waitFor(() =>
    expect(urls).toContain('/api/v1/warehouse/receipts/page'),
  );
  expect(screen.getByText('已入库 0 张')).toBeTruthy();
});

it('销售出库：状态下拉带数量，切到待交快递按状态查询', async () => {
  const bodies: Record<string, unknown>[] = [];
  (request as jest.Mock).mockImplementation(
    (url: string, opt?: { data?: Record<string, unknown> }) => {
      if (url.endsWith('/outbounds/counts'))
        return Promise.resolve({ data: { pending: 2, packed: 5 } });
      if (url.endsWith('/outbounds/page')) bodies.push(opt?.data ?? {});
      return Promise.resolve(page());
    },
  );
  render(React.createElement(App, null, React.createElement(Outbounds)));
  expect(await screen.findByText('待打包 · 2')).toBeTruthy();
  const before = bodies.length;
  await pickStatus(/^待交快递 · 5$/);
  await waitFor(() => expect(bodies.length).toBeGreaterThan(before));
  expect(bodies[bodies.length - 1].status).not.toEqual(bodies[0].status);
  expect(within(document.body).queryByRole('radio')).toBeNull();
});
