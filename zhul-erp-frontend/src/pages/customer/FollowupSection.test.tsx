import { act, fireEvent, render, screen } from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import { PaneContext } from '@/components/PageTabs/hooks';
import { followupApi } from '@/pages/sales/followup/service';
import { FollowupTimeline } from './FollowupSection';

jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  request: jest.fn(),
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
  followupApi: { customerRecords: jest.fn() },
}));

/** 系统事件记录：时间线上显示 title */
const rec = (id: number, title: string) => ({
  id,
  recordType: 9,
  title,
  situationLabels: [],
  profileFields: [],
  createTime: '2026-11-10 10:00:00',
});
const range = (prefix: string, from: number, n: number) =>
  Array.from({ length: n }, (_, i) => rec(from + i, `${prefix}${from + i}`));

type Params = { type: string; page: number; pageSize: number };
let pending: Map<string, (v: unknown) => void>;
let calls: Params[];

beforeEach(() => {
  pending = new Map();
  calls = [];
  (followupApi.customerRecords as jest.Mock).mockImplementation(
    (_id: number, p: Params) => {
      calls.push(p);
      return new Promise((resolve) =>
        pending.set(`${p.type}-${p.page}-${p.pageSize}`, resolve),
      );
    },
  );
});

const resolve = async (key: string, records: unknown[], total: number) => {
  await act(async () => {
    pending.get(key)?.({ records, total });
  });
};

const first = { records: range('全部', 1, 5), total: 12 } as never;

const h = React.createElement;
const Timeline = ({ active }: { active: boolean }) =>
  h(
    App,
    null,
    h(
      PaneContext.Provider,
      { value: { active } },
      h(FollowupTimeline, { customerId: 7, first, reloadKey: 0 }),
    ),
  );

/** audit UI-072：「加载更早的」还没返回就切了筛选，旧筛选的第 2 页不能追加进新列表 */
it('切换筛选后丢弃旧筛选迟到的「加载更早的」', async () => {
  render(h(Timeline, { active: true }));
  await resolve('all-1-5', range('全部', 1, 5), 12);
  fireEvent.click(screen.getByText('加载更早的 7 条'));
  fireEvent.click(screen.getByText('业务员记录'));
  await resolve('user-1-5', range('业务员', 100, 1), 1);
  await resolve('all-2-5', range('全部', 6, 5), 12);
  expect(screen.getByText('业务员100')).toBeTruthy();
  expect(screen.queryByText('全部6')).toBeNull();
  expect(screen.queryByText(/加载更早的/)).toBeNull();
});

/** audit UI-055（补修）：返回刷新保留筛选和已加载的条数，不回到第一页 */
it('切回页面时按当前筛选重取已加载的条数', async () => {
  const view = render(h(Timeline, { active: true }));
  await resolve('all-1-5', range('全部', 1, 5), 12);
  fireEvent.click(screen.getByText('加载更早的 7 条'));
  await resolve('all-2-5', range('全部', 6, 5), 12);
  expect(screen.getByText('全部10')).toBeTruthy();
  view.rerender(h(Timeline, { active: false }));
  view.rerender(h(Timeline, { active: true }));
  expect(calls.at(-1)).toMatchObject({ type: 'all', page: 1, pageSize: 10 });
  await resolve('all-1-10', range('新', 1, 10), 13);
  expect(screen.getByText('新10')).toBeTruthy();
  expect(screen.getByText('加载更早的 3 条')).toBeTruthy();
  // 之后「加载更早的」从第 3 页接着取
  fireEvent.click(screen.getByText('加载更早的 3 条'));
  expect(calls.at(-1)).toMatchObject({ type: 'all', page: 3, pageSize: 5 });
});

/** audit UI-055（补修）：已加载超过后端单次上限 50 条时切回，之后「加载更早的」不跳过记录 */
it('切回时后端只返回 50 条，页码按实际条数推算', async () => {
  const view = render(h(Timeline, { active: true }));
  await resolve('all-1-5', range('全部', 1, 5), 80);
  for (let p = 2; p <= 11; p++) {
    fireEvent.click(screen.getByText(/加载更早的/));
    await resolve(`all-${p}-5`, range('全部', (p - 1) * 5 + 1, 5), 80);
  }
  expect(screen.getByText('全部55')).toBeTruthy();
  view.rerender(h(Timeline, { active: false }));
  view.rerender(h(Timeline, { active: true }));
  expect(calls.at(-1)).toMatchObject({ page: 1, pageSize: 55 });
  // 后端把 pageSize 限在 50
  await resolve('all-1-55', range('全部', 1, 50), 80);
  expect(screen.queryByText('全部51')).toBeNull();
  expect(screen.getByText('加载更早的 30 条')).toBeTruthy();
  // 第 11 页（第 51–55 条）接着取，不跳到第 12 页
  fireEvent.click(screen.getByText('加载更早的 30 条'));
  expect(calls.at(-1)).toMatchObject({ page: 11, pageSize: 5 });
  await resolve('all-11-5', range('全部', 51, 5), 80);
  expect(screen.getByText('全部51')).toBeTruthy();
  expect(screen.getByText('加载更早的 25 条')).toBeTruthy();
});
