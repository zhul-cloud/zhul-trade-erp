import { act, fireEvent, render, screen } from '@testing-library/react';
import { request } from '@umijs/max';
import { App } from 'antd';
import React from 'react';
import PayableManagement from './index';

/** 应付管理：原统计卡片改为快捷筛选（stat-cards.md），四个条件互斥，再点一次取消 */
jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  Link: () => null,
  request: jest.fn(),
  useAccess: () => ({}),
  useLocation: () => ({ pathname: '/finance/payables', search: '' }),
  useModel: () => ({ initialState: {} }),
}));

it('点「已逾期未付」按逾期筛选，再点「供应商欠款」换成有欠款，再点一次取消', async () => {
  const params: Record<string, unknown>[] = [];
  (request as jest.Mock).mockImplementation(
    (url: string, opt?: { params?: Record<string, unknown> }) => {
      if (url === '/api/v1/finance/payables') {
        params.push(opt?.params ?? {});
        return Promise.resolve({ data: { total: 0, records: [] } });
      }
      return Promise.resolve({
        data: { overdue: 3200, overdueCount: 2, credit: 500 },
      });
    },
  );
  render(
    React.createElement(App, null, React.createElement(PayableManagement)),
  );
  const overdue = await screen.findByRole('button', {
    name: /已逾期未付 CNY 3,200\.00 · 2 张/,
  });
  await act(async () => {
    fireEvent.click(overdue);
  });
  expect(params[params.length - 1]).toMatchObject({ due: 'OVERDUE' });
  expect(params[params.length - 1].hasCredit).toBeUndefined();

  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: /供应商欠款/ }));
  });
  expect(params[params.length - 1]).toMatchObject({ hasCredit: true });
  expect(params[params.length - 1].due).toBeUndefined();
  expect(
    screen
      .getByRole('button', { name: /已逾期未付/ })
      .getAttribute('aria-pressed'),
  ).toBe('false');

  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: /供应商欠款/ }));
  });
  expect(params[params.length - 1].hasCredit).toBeUndefined();
  expect(params[params.length - 1].due).toBeUndefined();
});
