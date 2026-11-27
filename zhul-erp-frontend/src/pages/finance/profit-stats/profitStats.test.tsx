import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import ProfitStatsPage from './index';
import { profitApi } from './service';

/** 利润统计：「包含未结算 / 只看已结算」分段改为快捷筛选「只看已结算」（openspec ui-redesign 任务 7.6） */
jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  Link: () => null,
  request: jest.fn().mockResolvedValue({ data: [] }),
  useAccess: () => ({}),
  useLocation: () => ({ pathname: '/finance/profit-stats', search: '' }),
  useModel: () => ({ initialState: {} }),
}));
jest.mock('@/utils/businessUnit', () => ({
  ...jest.requireActual('@/utils/businessUnit'),
  useBusinessUnits: () => [{ enabled: false, units: [] }, jest.fn()],
}));
jest.mock('./service', () => ({
  ...jest.requireActual('./service'),
  profitApi: { stats: jest.fn() },
}));

it('点「只看已结算」后按已结算查询，再点取消', async () => {
  (profitApi.stats as jest.Mock).mockResolvedValue({
    summary: undefined,
    groups: [],
  });
  render(React.createElement(App, null, React.createElement(ProfitStatsPage)));
  const last = () => {
    const calls = (profitApi.stats as jest.Mock).mock.calls;
    return calls[calls.length - 1][0];
  };
  await waitFor(() => expect(profitApi.stats).toHaveBeenCalled());
  expect(last().settledOnly).toBeUndefined();
  const chip = screen.getByRole('button', { name: '只看已结算' });
  await act(async () => {
    fireEvent.click(chip);
  });
  await waitFor(() => expect(last().settledOnly).toBe(true));
  expect(chip.getAttribute('aria-pressed')).toBe('true');
  await act(async () => {
    fireEvent.click(chip);
  });
  await waitFor(() => expect(last().settledOnly).toBeUndefined());
});
