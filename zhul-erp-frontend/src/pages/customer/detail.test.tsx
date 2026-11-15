import { act, fireEvent, render, screen } from '@testing-library/react';
import { history } from '@umijs/max';
import { App } from 'antd';
import React from 'react';
import { PaneContext } from '@/components/PageTabs/hooks';
import { tabsActions } from '@/components/PageTabs/store';
import { creditApi } from '@/pages/finance/credits/service';
import { followupApi } from '@/pages/sales/followup/service';
import { backgroundApi } from './background/service';
import CustomerDetailPage from './detail';
import { profileApi } from './profile/service';
import { customerApi } from './service';

jest.mock('@umijs/max', () => ({
  history: {
    push: jest.fn(),
    replace: jest.fn(),
    location: { pathname: '/customer/list/7', search: '', hash: '' },
    listen: jest.fn(),
  },
  // 其余接口（售后卡片等）不返回
  request: jest.fn(() => new Promise(() => undefined)),
  useParams: () => ({ id: '7' }),
  useLocation: () => ({ pathname: '/customer/list/7' }),
  useModel: () => ({ initialState: {} }),
  Link: (p: { children: unknown }) => p.children,
  useAccess: () => ({ salesFollowup: true, 'partner:customer:merge': true }),
}));
// boardStyle 的 JSX 依赖全局 React（jest 下没有），这里只需要颜色
jest.mock('@/pages/sales/followup/boardStyle', () => ({
  styleOf: () => ({ color: '#4338CA', soft: '#E0E7FF', icon: null }),
  BOARD_STYLE: {},
  TODAY_COLOR: '#4F46E5',
  VIEW_TODAY: 0,
}));
jest.mock('@/pages/sales/followup/service', () => ({
  ...jest.requireActual('@/pages/sales/followup/service'),
  followupApi: {
    ...jest.requireActual('@/pages/sales/followup/service').followupApi,
    customerSummary: jest.fn(),
    customerRecords: jest.fn(),
    develop: jest.fn(),
  },
}));
jest.mock('./service', () => ({
  ...jest.requireActual('./service'),
  customerApi: { detail: jest.fn() },
}));
jest.mock('./profile/service', () => ({
  ...jest.requireActual('./profile/service'),
  profileApi: { get: jest.fn() },
}));
jest.mock('./background/service', () => ({
  ...jest.requireActual('./background/service'),
  backgroundApi: { list: jest.fn(), precheck: jest.fn() },
}));
jest.mock('@/pages/finance/credits/service', () => ({
  ...jest.requireActual('@/pages/finance/credits/service'),
  creditApi: { ofCustomer: jest.fn() },
}));
jest.mock('@/pages/product/components/useCountries', () => ({
  useCountries: () => ({ labelOf: (c: string) => c, zhOf: (c: string) => c }),
}));
jest.mock('@/utils/dict', () => ({
  ...jest.requireActual('@/utils/dict'),
  useDictOptions: () => ({ labelOf: () => '' }),
}));
/** 流水抽屉只看传进来的客户名 */
jest.mock('@/pages/finance/credits/EntriesDrawer', () => ({
  __esModule: true,
  default: (p: { open: boolean; account?: { customerName: string } }) =>
    p.open ? `流水抽屉：${p.account?.customerName}` : null,
}));
/** 合并弹窗：打开时给一个按钮，模拟「对调后确认合并」，保留的是另一个客户 */
jest.mock('./profile/ManualMergeModal', () => ({
  __esModule: true,
  default: (p: { open: boolean; onDone: (keepId: number) => void }) =>
    p.open
      ? require('react').createElement(
          'button',
          { type: 'button', onClick: () => p.onDone(99) },
          '模拟合并完成',
        )
      : null,
}));

const h = React.createElement;

const customer = (over: Record<string, unknown> = {}) => ({
  id: 7,
  customerCode: 'CU7',
  name: 'Pacific Controls Pty Ltd',
  displayName: 'Pacific Controls Pty Ltd',
  nameMissing: false,
  country: 'AU',
  status: 1,
  parties: [],
  createTime: '2026-11-01 10:00:00',
  updateTime: '2026-11-01 10:00:00',
  ...over,
});

const summary = {
  records: [],
  total: 0,
  openAmounts: [],
  step: 0,
};

beforeEach(() => {
  jest.clearAllMocks();
  (customerApi.detail as jest.Mock).mockResolvedValue(customer());
  (profileApi.get as jest.Mock).mockRejectedValue(new Error('无档案'));
  (backgroundApi.list as jest.Mock).mockResolvedValue([]);
  (backgroundApi.precheck as jest.Mock).mockResolvedValue(undefined);
  (followupApi.customerSummary as jest.Mock).mockResolvedValue(summary);
  (followupApi.customerRecords as jest.Mock).mockResolvedValue({
    records: [],
    total: 0,
  });
  (followupApi.develop as jest.Mock).mockResolvedValue({ state: 'none' });
  (creditApi.ofCustomer as jest.Mock).mockResolvedValue([]);
});

const Page = ({ active }: { active: boolean }) =>
  h(
    App,
    null,
    h(
      PaneContext.Provider,
      { value: { active, entryKey: 'e1' } },
      h(CustomerDetailPage),
    ),
  );

const flush = () => act(async () => {});

/**
 * audit UI-055（补修）：切回客户详情时静默刷新，不换成骨架屏、不卸载页面；
 * 背调报告、背调前检查、跟进概况一并重取
 */
it('切回页面：静默刷新，子卡片的数据一并重取', async () => {
  const view = render(h(Page, { active: true }));
  await flush();
  expect(screen.getByText('跳到主要内容')).toBeTruthy();
  const section = screen.getByRole('region', { name: '跟进' });
  expect(customerApi.detail).toHaveBeenCalledTimes(1);

  view.rerender(h(Page, { active: false }));
  view.rerender(h(Page, { active: true }));
  // 重取还没返回：页面内容仍在，没有换成骨架屏
  expect(screen.getByRole('region', { name: '跟进' })).toBe(section);
  await flush();
  expect(screen.getByRole('region', { name: '跟进' })).toBe(section);
  expect(customerApi.detail).toHaveBeenCalledTimes(2);
  expect(profileApi.get).toHaveBeenCalledTimes(2);
  expect(backgroundApi.list).toHaveBeenCalledTimes(2);
  expect(backgroundApi.precheck).toHaveBeenCalledTimes(2);
  expect(followupApi.customerSummary).toHaveBeenCalledTimes(2);
});

/** audit UI-073：客户名称为空时，标签、返回条和流水抽屉都用展示名 */
it('客户名称为空：标题和流水抽屉用展示名', async () => {
  const setTitle = jest.spyOn(tabsActions, 'setTitle');
  (customerApi.detail as jest.Mock).mockResolvedValue(
    customer({ name: '', displayName: 'John Smith', nameMissing: true }),
  );
  (creditApi.ofCustomer as jest.Mock).mockResolvedValue([
    {
      currencyCode: 'USD',
      available: 100,
      occupied: 0,
      occupiers: [],
    },
  ]);
  render(h(Page, { active: true }));
  await flush();
  expect(setTitle).toHaveBeenLastCalledWith(
    'e1',
    '客户 John Smith',
    'John Smith',
  );
  fireEvent.click(screen.getByText('流水'));
  expect(screen.getByText('流水抽屉：John Smith')).toBeTruthy();
});

/** audit UI-058（补修）：当前客户被并入另一个客户后，替换当前页，不在已删除的客户上面压栈 */
it('当前客户被合并：用替换打开保留的客户', async () => {
  render(h(Page, { active: true }));
  await flush();
  fireEvent.click(screen.getByRole('button', { name: /合\s*并/ }));
  fireEvent.click(screen.getByText('模拟合并完成'));
  expect(history.replace).toHaveBeenCalledWith('/customer/list/99');
  expect(history.push).not.toHaveBeenCalled();
});
