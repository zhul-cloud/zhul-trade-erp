import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import { PaneContext } from '@/components/PageTabs/hooks';
import { PATHS, supplyApi } from '../service';
import CoverageModelsPage from './models';

const mockSearch = { value: '?brandId=1&categoryId=11' };

jest.mock('@umijs/max', () => {
  const R = require('react');
  return {
    history: { push: jest.fn() },
    request: jest.fn(),
    useLocation: () => ({
      pathname: '/supply-chain/coverage/models',
      search: mockSearch.value,
    }),
    useAccess: () => ({}),
    useModel: () => ({ initialState: {} }),
    Link: (p: any) => R.createElement('a', { href: p.to }, p.children),
  };
});
// 销售跟进的样式文件在模块顶层写 JSX，测试环境用不到箭头，直接替换
jest.mock('@/pages/sales/followup/FlowChain', () => ({ Arrow: () => null }));
jest.mock('../components', () => ({
  ...jest.requireActual('../components'),
  useUsers: () => [{ value: 5, label: '张三' }],
}));
jest.mock('../service', () => ({
  ...jest.requireActual('../service'),
  supplyApi: { models: jest.fn(), matrix: jest.fn() },
}));

const api = supplyApi as unknown as Record<string, jest.Mock>;

const row = (id: number) => ({
  id,
  brandId: 1,
  brandName: '品牌甲',
  categoryId: 11,
  categoryName: '品类A',
  model: `M-${id}`,
  status: 2,
  priority: 3,
  coverageStatus: 1,
  createTime: '2026-11-01 10:00:00',
  updateTime: '2026-11-01 10:00:00',
});

const page = (from: number) => ({
  total: 100,
  records: Array.from({ length: 20 }, (_, i) => row(from + i)),
});

const show = (active = true) =>
  React.createElement(
    PaneContext.Provider,
    { value: { active } },
    React.createElement(App, null, React.createElement(CoverageModelsPage)),
  );

beforeEach(() => {
  jest.clearAllMocks();
  mockSearch.value = '?brandId=1&categoryId=11';
  api.models.mockImplementation((q: { page: number }) =>
    Promise.resolve(page((q.page - 1) * 20 + 1)),
  );
  api.matrix.mockResolvedValue({
    mode: 'cover',
    days: 30,
    brands: [{ id: 1, name: '品牌甲', total: 100 }],
    categories: [{ id: 11, name: '品类A', total: 100 }],
    cells: [],
  });
});

it('UI-090：非第一页改筛选只发一次第 1 页的请求，迟到的旧页不覆盖', async () => {
  render(show());
  await screen.findByText('M-1');
  fireEvent.click(screen.getByTitle('3'));
  await screen.findByText('M-41');
  api.models.mockClear();
  const input = screen.getByPlaceholderText('型号');
  fireEvent.change(input, { target: { value: 'ABC' } });
  fireEvent.keyDown(input, { key: 'Enter', keyCode: 13 });
  await waitFor(() => expect(api.models).toHaveBeenCalled());
  await act(async () => {});
  expect(api.models).toHaveBeenCalledTimes(1);
  expect(api.models.mock.calls[0][0]).toEqual(
    expect.objectContaining({ keyword: 'ABC', page: 1 }),
  );
});

it('UI-090：迟到的旧请求结果被丢弃', async () => {
  let resolveOld!: (v: unknown) => void;
  api.models.mockImplementationOnce(
    () =>
      new Promise((res) => {
        resolveOld = res;
      }),
  );
  render(show());
  await waitFor(() => expect(api.models).toHaveBeenCalledTimes(1));
  const input = screen.getByPlaceholderText('型号');
  fireEvent.change(input, { target: { value: 'NEW' } });
  fireEvent.keyDown(input, { key: 'Enter', keyCode: 13 });
  await screen.findByText('M-1');
  await act(async () => {
    resolveOld({ total: 1, records: [row(999)] });
  });
  expect(screen.queryByText('M-999')).toBeNull();
  expect(screen.getByText('M-1')).toBeTruthy();
});

it('UI-055：从详情返回型号清单时重新取数', async () => {
  const { rerender } = render(show(true));
  await screen.findByText('M-1');
  const models = api.models.mock.calls.length;
  const matrix = api.matrix.mock.calls.length;
  rerender(show(false));
  rerender(show(true));
  await waitFor(() =>
    expect(api.models.mock.calls.length).toBeGreaterThan(models),
  );
  await waitFor(() =>
    expect(api.matrix.mock.calls.length).toBeGreaterThan(matrix),
  );
  // 静默刷新：列表一直在，不清空
  expect(screen.getByText('M-1')).toBeTruthy();
});

it('UI-089：从矩阵格子带来的负责人、优先级、无货统计期都生效', async () => {
  mockSearch.value =
    '?brandId=1&categoryId=11&ownerId=5&priority=3&stockoutDays=90';
  render(show());
  await waitFor(() =>
    expect(api.models.mock.calls.at(-1)?.[0]).toEqual(
      expect.objectContaining({
        brandId: 1,
        categoryId: 11,
        ownerId: 5,
        priority: 3,
        stockoutDays: 90,
      }),
    ),
  );
  expect(screen.getByTitle('近 90 天无货')).toBeTruthy();
  expect(screen.getByTitle('张三')).toBeTruthy();
});

it('UI-089：格子链接带上筛选与无货统计期', () => {
  expect(PATHS.models(1, 0, { ownerId: 5, priority: 3 })).toBe(
    '/supply-chain/coverage/models?brandId=1&categoryId=0&ownerId=5&priority=3',
  );
  expect(PATHS.models(1, 11, { stockoutDays: 90 })).toBe(
    '/supply-chain/coverage/models?brandId=1&categoryId=11&stockoutDays=90',
  );
  expect(PATHS.models(1)).toBe('/supply-chain/coverage/models?brandId=1');
});
