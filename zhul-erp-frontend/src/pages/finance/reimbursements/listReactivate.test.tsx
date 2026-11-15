import { act, fireEvent, render, screen } from '@testing-library/react';
import { request } from '@umijs/max';
import { App } from 'antd';
import React from 'react';
import { PaneContext } from '@/components/PageTabs/hooks';
import ReimbursementPage from './index';

jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  Link: ({ children }: { children?: unknown }) => children ?? null,
  request: jest.fn(),
  useAccess: () => ({}),
  useLocation: () => ({ pathname: '/finance/reimbursements', search: '' }),
  useModel: () => ({ initialState: {} }),
}));

const page = (active: boolean) =>
  React.createElement(
    PaneContext.Provider,
    { value: { active } },
    React.createElement(App, null, React.createElement(ReimbursementPage)),
  );

/** UI-055：报销单页签从详情返回（标签页重新可见）时重新取数 */
it('报销单页签返回时重新取数', async () => {
  const listCalls: string[] = [];
  (request as jest.Mock).mockImplementation((url: string) => {
    if (url === '/api/v1/finance/reimbursements') {
      listCalls.push(url);
      return Promise.resolve({ data: { total: 0, records: [] } });
    }
    if (url.endsWith('/pending-total')) return Promise.resolve({ data: 0 });
    return Promise.resolve({ data: [] });
  });
  const view = render(page(true));
  await act(async () => {
    fireEvent.click(await screen.findByText('报销单', {}, { timeout: 5000 }));
  });
  await act(async () => {
    await new Promise((r) => setTimeout(r, 50));
  });
  const before = listCalls.length;
  expect(before).toBeGreaterThan(0);
  view.rerender(page(false));
  await act(async () => {
    view.rerender(page(true));
  });
  expect(listCalls.length).toBe(before + 1);
});
