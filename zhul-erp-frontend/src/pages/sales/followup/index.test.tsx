import { act, fireEvent, render, screen } from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import { PaneContext } from '@/components/PageTabs/hooks';
import FollowupPage from './index';
import { followupApi } from './service';

jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  Link: () => null,
  request: jest.fn(),
  useAccess: () => ({ 'crm:followup:record': true }),
  useLocation: () => ({ pathname: '/sales/followup', search: '' }),
  useModel: () => ({ initialState: {} }),
}));
jest.mock('@/pages/product/components/useCountries', () => ({
  useCountries: () => ({ options: [] }),
}));
jest.mock('@/services/zhul/users', () => ({
  getUserOptions: jest.fn(() => Promise.resolve([])),
}));
jest.mock('./AccountPanel', () => ({ AccountInquiryModal: () => null }));
jest.mock('./Batch', () => ({
  BatchBar: () => null,
  BatchFollowupModal: () => null,
  usePicks: () => ({
    list: [],
    picked: new Set(),
    clear: () => undefined,
    toggle: () => undefined,
    replace: () => undefined,
  }),
}));
jest.mock('./BattleCard', () => () => null);
jest.mock('./boardStyle', () => ({ VIEW_TODAY: 0 }));
jest.mock('./components', () => ({
  Card: (p: { children?: unknown }) => p.children ?? null,
  NurtureModal: () => null,
}));
jest.mock('./DevelopPanel', () => ({
  ConvertOpportunityModal: () => null,
  NewProspectModal: () => null,
  StopDevelopModal: () => null,
}));
jest.mock('./FlowChain', () => ({ FlowChain: () => null }));
jest.mock('./FollowupTable', () => ({
  FollowupTable: () => null,
  StageBar: () => null,
  stageItem: () => undefined,
}));
jest.mock('./QuickActions', () => ({ QuickBatchModal: () => null }));
jest.mock(
  './RecordFollowupModal',
  () => (p: { open: boolean }) =>
    p.open ? require('react').createElement('div', null, '记跟进弹窗') : null,
);
jest.mock('./TeamView', () => () => null);
jest.mock('./TodayList', () => ({ TodayList: () => null }));
jest.mock('./service', () => ({
  ...jest.requireActual('./service'),
  followupApi: {
    overview: jest.fn(),
    today: jest.fn(),
    items: jest.fn(),
    groups: jest.fn(),
  },
}));

const page = (active: boolean) =>
  React.createElement(
    App,
    null,
    React.createElement(
      PaneContext.Provider,
      { value: { entryKey: 'fu', active } },
      React.createElement(FollowupPage),
    ),
  );

beforeEach(() => {
  jest.clearAllMocks();
  (followupApi.overview as jest.Mock).mockResolvedValue({ boards: [] });
  (followupApi.today as jest.Mock).mockResolvedValue({
    overdue: [],
    today: [],
    handled: [],
    weekRemaining: 0,
    tomorrow: 0,
  });
});

/** audit UI-055：从售后单等页面返回客户跟进时重新取数，记跟进弹窗开着时不刷新 */
it('返回时重新取今日待办与计数，弹窗开着时不刷新', async () => {
  const view = render(page(true));
  await act(async () => {});
  expect(followupApi.overview).toHaveBeenCalledTimes(1);
  expect(followupApi.today).toHaveBeenCalledTimes(1);

  view.rerender(page(false));
  await act(async () => view.rerender(page(true)));
  expect(followupApi.overview).toHaveBeenCalledTimes(2);
  expect(followupApi.today).toHaveBeenCalledTimes(2);

  fireEvent.click(screen.getByRole('button', { name: /记一次跟进/ }));
  expect(screen.getByText('记跟进弹窗')).toBeTruthy();
  view.rerender(page(false));
  await act(async () => view.rerender(page(true)));
  expect(followupApi.overview).toHaveBeenCalledTimes(2);
});
