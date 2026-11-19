import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import { request } from '@umijs/max';
import { App } from 'antd';
import React from 'react';
import { PaneContext } from '@/components/PageTabs/hooks';
import ReimbursementPage from './index';

/** openspec po-cancel-refund：待报销里垫付可以撤回，快递费不能撤回 */
jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  Link: ({ children }: { children?: unknown }) => children ?? null,
  request: jest.fn(),
  useAccess: () => ({}),
  useLocation: () => ({ pathname: '/finance/reimbursements', search: '' }),
  useModel: () => ({ initialState: {} }),
}));

const base = {
  occurDate: '2026-11-19',
  proofOwnerType: 'PAYMENT',
  proofs: [],
  proofUploadable: false,
  mine: true,
};
const items = [
  {
    ...base,
    sourceType: 1,
    sourceId: 9,
    sourceNo: 'PY20261119001',
    description: '淘宝 · 上海成安机电',
    amount: 700,
    withdrawable: true,
  },
  {
    ...base,
    sourceType: 2,
    sourceId: 3,
    sourceNo: 'SF1234',
    description: '国内快递 · 顺丰 SF1234',
    amount: 12,
    proofOwnerType: 'COURIER_PROOF',
    withdrawable: false,
  },
];

it('垫付可以撤回并提交原因；快递费显示随快递单处理', async () => {
  const calls: { url: string; data?: unknown }[] = [];
  (request as jest.Mock).mockImplementation(
    (url: string, opt?: { data?: unknown }) => {
      calls.push({ url, data: opt?.data });
      if (url.endsWith('/pending')) return Promise.resolve({ data: items });
      if (url.endsWith('/pending-total')) return Promise.resolve({ data: 0 });
      if (url === '/api/v1/finance/reimbursements')
        return Promise.resolve({ data: { total: 0, records: [] } });
      return Promise.resolve({ data: [] });
    },
  );
  render(
    React.createElement(
      PaneContext.Provider,
      { value: { active: true } },
      React.createElement(App, null, React.createElement(ReimbursementPage)),
    ),
  );
  expect(
    await screen.findByText('随快递单处理', {}, { timeout: 5000 }),
  ).toBeTruthy();
  const buttons = screen.getAllByRole('button', { name: '撤回' });
  expect(buttons).toHaveLength(1);
  await act(async () => {
    fireEvent.click(buttons[0]);
  });
  fireEvent.change(await screen.findByLabelText('撤回原因'), {
    target: { value: '采购已取消，店铺已退款' },
  });
  const ok = screen
    .getAllByRole('button', { name: /撤\s*回/ })
    .find((b) => b.closest('.ant-modal'));
  await act(async () => {
    fireEvent.click(ok as HTMLElement);
  });
  await waitFor(() =>
    expect(calls.find((c) => c.url.endsWith('/items/withdraw'))?.data).toEqual({
      sourceType: 1,
      sourceId: 9,
      reason: '采购已取消，店铺已退款',
    }),
  );
});
