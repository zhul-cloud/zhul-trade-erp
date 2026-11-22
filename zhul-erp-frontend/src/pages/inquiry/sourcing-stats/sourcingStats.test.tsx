import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import SourcingStatsPage from './index';
import { noStockApi, sourcingStatsApi } from './service';

/** 询价统计：按月份、按人员、无货分析改为页签（openspec ui-redesign 任务 7.6） */
jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  Link: () => null,
  request: jest.fn(),
  useAccess: () => ({}),
  useLocation: () => ({ pathname: '/inquiry/sourcing-stats', search: '' }),
  useModel: () => ({ initialState: {} }),
}));
jest.mock('./service', () => ({
  ...jest.requireActual('./service'),
  sourcingStatsApi: { stats: jest.fn() },
  noStockApi: { stats: jest.fn() },
}));

it('切到「无货分析」页签：不显示统计期间、汇总行和页头帮助，显示无货分析的条件', async () => {
  (sourcingStatsApi.stats as jest.Mock).mockResolvedValue({
    total: {
      total: 3,
      completed: 1,
      unquotable: 1,
      inProgress: 1,
      completedRate: 33.3,
      unquotableRate: 33.3,
    },
    months: [],
    purchasers: [],
    workload: [],
  });
  (noStockApi.stats as jest.Mock).mockResolvedValue({
    totals: { models: 0, noStock: 0, rate: null, brandCount: 0 },
    brands: [],
    categories: [],
    trend: [],
  });
  render(
    React.createElement(App, null, React.createElement(SourcingStatsPage)),
  );
  expect(await screen.findByText('询价完成率')).toBeTruthy();
  expect(screen.getByText('统计期间')).toBeTruthy();
  expect(
    screen.getAllByRole('img', { name: /按月份按询盘单统计/ }),
  ).toHaveLength(1);

  await act(async () => {
    fireEvent.click(screen.getByRole('tab', { name: '无货分析' }));
  });
  await waitFor(() => expect(noStockApi.stats).toHaveBeenCalled());
  expect(screen.queryByText('统计期间')).toBeNull();
  expect(screen.queryByText('询价完成率')).toBeNull();
  expect(screen.queryByRole('img', { name: /按月份按询盘单统计/ })).toBeNull();
  expect(screen.getByText('无货率走势')).toBeTruthy();
  expect(screen.getByText('周期')).toBeTruthy();
});
