import { render, waitFor } from '@testing-library/react';
import React from 'react';
import { PaneContext } from '@/components/PageTabs/hooks';
import OpportunityPage from './index';
import { opportunityApi } from './service';

jest.mock('@umijs/max', () => ({
  request: jest.fn(),
  history: { push: jest.fn() },
  useAccess: () => ({}),
  useModel: () => ({ initialState: {} }),
  useLocation: () => ({ pathname: '/crm/opportunity' }),
}));
jest.mock('./service', () => ({
  ...jest.requireActual('./service'),
  opportunityApi: {
    stages: jest.fn().mockResolvedValue([]),
    page: jest.fn().mockResolvedValue({ records: [], total: 0 }),
    summary: jest.fn().mockResolvedValue({
      todayNew: 1,
      todayInvalid: 0,
      weekNew: 1,
      lastWeekNew: 0,
      firstStageCount: 1,
      firstStageStale: 0,
      monthNew: 1,
      monthValid: 1,
    }),
  },
}));
jest.mock('./RegisterDrawer', () => () => null);
jest.mock('@/services/zhul/users', () => ({
  getUserOptions: jest.fn().mockResolvedValue([]),
}));
jest.mock('@/utils/dict', () => ({
  DICT_SOURCE_CHANNEL: 'source_channel',
  useDictOptions: () => ({ options: [], labelOf: () => '' }),
}));

const page = opportunityApi.page as jest.Mock;
const summary = opportunityApi.summary as jest.Mock;
const h = React.createElement;

const Pane = ({ active }: { active: boolean }) =>
  h(PaneContext.Provider, { value: { active } }, h(OpportunityPage));

/** audit UI-055#2：从其他页面返回保活的商机列表时，表格和顶部统计卡片一起刷新 */
it('返回列表时统计卡片与表格一起重新取数', async () => {
  const view = render(h(Pane, { active: true }));
  await waitFor(() => expect(summary).toHaveBeenCalledTimes(1));
  await waitFor(() => expect(page).toHaveBeenCalledTimes(1));
  view.rerender(h(Pane, { active: false }));
  view.rerender(h(Pane, { active: true }));
  await waitFor(() => expect(page).toHaveBeenCalledTimes(2));
  await waitFor(() => expect(summary).toHaveBeenCalledTimes(2));
});
