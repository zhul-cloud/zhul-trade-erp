import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import { supplyApi } from '../service';
import SupplyFollowupPage from './index';

const mockParams = { value: new URLSearchParams() };

jest.mock('@umijs/max', () => {
  const R = require('react');
  return {
    history: { push: jest.fn() },
    request: jest.fn(),
    useSearchParams: () => [mockParams.value],
    useLocation: () => ({ pathname: '/supply-chain/followup', search: '' }),
    useAccess: () => ({}),
    useModel: () => ({ initialState: {} }),
    Link: (p: { to: string; children?: import('react').ReactNode }) =>
      R.createElement('a', { href: p.to }, p.children),
  };
});
jest.mock('../components', () => ({
  ...jest.requireActual('../components'),
  useOptions: () => ({
    brands: [{ value: 1, label: '品牌甲' }],
    tree: [],
    categories: [{ label: '一级甲', options: [{ value: 11, label: '品类A' }] }],
    tops: [{ value: 10, label: '一级甲' }],
  }),
  useUsers: () => [],
}));
jest.mock('../service', () => ({
  ...jest.requireActual('../service'),
  supplyApi: {
    overview: jest.fn(),
    today: jest.fn(),
    groups: jest.fn(),
    devGroups: jest.fn(),
    unclassified: jest.fn(),
    team: jest.fn(),
    cards: jest.fn(),
  },
}));

const api = supplyApi as unknown as Record<string, jest.Mock>;

const overview = {
  todayModels: 0,
  overdue: 0,
  dueToday: 0,
  weekRest: 0,
  intelTotal: 3,
  unclassified: 0,
  watching: 1,
  review: 0,
  paused: 0,
  coverTotal: 2,
  coverage: { 1: 1, 2: 0, 3: 0, 4: 1 },
  stockoutOpen: 1,
  pending: 0,
  single: 0,
  canEdit: true,
  canTeam: true,
  canProject: true,
  canCoverage: true,
  canSeeSupplier: true,
  canDev: true,
  evidenceDays: 30,
  dev: { gapGroups: 1, todo: 1, doing: 0, done: 0, gapTotal: 1 },
};
const emptyToday = {
  overdue: 0,
  dueToday: 0,
  weekRest: 0,
  overdueCards: [],
  todayCards: [],
};
const demand = (id: number, model: string) => ({
  id,
  brandId: 1,
  brandName: '品牌甲',
  categoryId: 11,
  categoryName: '品类A',
  model,
  status: 2,
  demandLevel: 1,
  priority: 3,
  suggestedPriority: 3,
  cntInquiry: 1,
  cntOrder: 0,
  cntStockout: 0,
  cntLost: 0,
  cntSearch: 0,
  cntResearch: 0,
  coverageStatus: 1,
  suggestedCoverage: 1,
  createTime: '2026-11-01 10:00:00',
  updateTime: '2026-11-01 10:00:00',
});
const groupPage = {
  totalGroups: 1,
  totalModels: 2,
  groups: [
    {
      key: '1:11',
      brandId: 1,
      brandName: '品牌甲',
      categoryId: 11,
      categoryName: '品类A',
      models: 2,
      stockoutModels: 0,
      stockoutTimes: 0,
      coverage: { 1: 2 },
      overdue: 0,
      ownerCount: 1,
      projectSolved: 0,
      projectTotal: 0,
      items: [demand(101, 'M-101'), demand(102, 'M-102')],
    },
  ],
};
const team = {
  days: 90,
  stockoutModels: 3,
  stockoutTimes: 5,
  prevStockoutModels: 0,
  prevStockoutTimes: 0,
  filledGaps: 0,
  byBrand: [{ label: '品牌甲', brandId: 1, models: 3, times: 5, open: 3 }],
  byCategory: [],
  cells: [],
  reasons: [],
  projects: [],
  owners: [],
  attention: [],
};

const renderPage = () =>
  render(
    React.createElement(App, null, React.createElement(SupplyFollowupPage)),
  );

/** 从今日待办进入供应覆盖板块 */
const toCover = async () => {
  fireEvent.click(await screen.findByText('去供应覆盖看缺口'));
  await screen.findByText('M-101');
};

const lastCall = (m: jest.Mock) => m.mock.calls[m.mock.calls.length - 1][0];

beforeEach(() => {
  jest.clearAllMocks();
  mockParams.value = new URLSearchParams();
  api.overview.mockResolvedValue(overview);
  api.today.mockResolvedValue(emptyToday);
  api.groups.mockResolvedValue(groupPage);
  api.devGroups.mockResolvedValue({ total: 0, groups: [] });
  api.unclassified.mockResolvedValue({ total: 0, records: [] });
  api.team.mockResolvedValue(team);
  api.cards.mockResolvedValue({});
});

it('UI-086：换筛选条件后清空勾选，批量栏不再作用于看不见的型号', async () => {
  renderPage();
  await toCover();
  fireEvent.click(screen.getAllByRole('checkbox')[0]);
  expect(await screen.findByText('已选 2 条')).toBeTruthy();
  // 切到「可直接报价」：列表里已经没有这两个型号
  api.groups.mockResolvedValue({ totalGroups: 0, totalModels: 0, groups: [] });
  fireEvent.click(screen.getByRole('radio', { name: /可直接报价/ }));
  await waitFor(() => expect(lastCall(api.groups).stage).toBe('4'));
  await waitFor(() => expect(screen.queryByText(/已选 \d+ 条/)).toBeNull());
});

it('UI-087：供应商开发板块显示并可清除带进来的二级品类', async () => {
  mockParams.value = new URLSearchParams('board=dev&brandId=1&categoryId=11');
  renderPage();
  await waitFor(() =>
    expect(lastCall(api.devGroups)).toEqual(
      expect.objectContaining({ brandId: 1, categoryId: 11 }),
    ),
  );
  // 生效的二级品类在工具栏上显示成胶囊，看得见、能去掉
  expect(await screen.findByText('二级品类：品类A')).toBeTruthy();
  fireEvent.click(
    screen.getByRole('button', { name: '去掉条件 二级品类：品类A' }),
  );
  await waitFor(() =>
    expect(lastCall(api.devGroups).categoryId).toBeUndefined(),
  );
});

it('UI-089：团队视图近 90 天下钻带统计期，并清掉关键词与优先级', async () => {
  renderPage();
  const kw = await screen.findByPlaceholderText('搜索型号');
  fireEvent.change(kw, { target: { value: 'ABC' } });
  fireEvent.keyDown(kw, { key: 'Enter', keyCode: 13 });
  await waitFor(() => expect(lastCall(api.groups).keyword).toBe('ABC'));
  fireEvent.click(await screen.findByText('团队'));
  fireEvent.click(await screen.findByRole('button', { name: '品牌甲' }));
  await waitFor(() =>
    expect(lastCall(api.groups)).toEqual(
      expect.objectContaining({
        board: 'cover',
        brandId: 1,
        stockout: true,
        stockoutDays: 90,
        keyword: undefined,
        priority: undefined,
      }),
    ),
  );
  expect(screen.getByRole('button', { name: /近 90 天无货/ })).toBeTruthy();
});

it('UI-091：今日待办取数失败显示加载失败并可重试，不显示「都处理完了」', async () => {
  api.today.mockRejectedValueOnce(new Error('500'));
  renderPage();
  const retry = await screen.findByRole('button', { name: /重\s*试/ });
  expect(screen.queryByText('今天的都处理完了')).toBeNull();
  fireEvent.click(retry);
  expect(await screen.findByText('今天的都处理完了')).toBeTruthy();
});

it('UI-091：概览取数失败显示加载失败并可重试，不一直显示骨架屏', async () => {
  api.overview.mockRejectedValueOnce(new Error('500'));
  renderPage();
  expect(await screen.findByText(/概览加载失败/)).toBeTruthy();
  fireEvent.click(screen.getAllByRole('button', { name: /重\s*试/ })[0]);
  await waitFor(() => expect(api.overview).toHaveBeenCalledTimes(2));
  await waitFor(() => expect(screen.queryByText(/概览加载失败/)).toBeNull());
});

it('UI-091：团队视图取数失败显示加载失败并可重试', async () => {
  api.team.mockRejectedValueOnce(new Error('500'));
  renderPage();
  fireEvent.click(await screen.findByText('团队'));
  const retry = await screen.findByRole('button', { name: /重\s*试/ });
  fireEvent.click(retry);
  expect(await screen.findByRole('button', { name: '品牌甲' })).toBeTruthy();
});
