import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import { supplyApi } from '../service';
import CoverageMatrixPage from './index';

jest.mock('@umijs/max', () => {
  const R = require('react');
  return {
    history: { push: jest.fn() },
    request: jest.fn(),
    useLocation: () => ({ pathname: '/supply-chain/coverage', search: '' }),
    useAccess: () => ({}),
    useModel: () => ({ initialState: {} }),
    Link: (p: any) => R.createElement('a', { href: p.to }, p.children),
  };
});
// 销售跟进的样式文件在模块顶层写 JSX，测试环境用不到箭头，直接替换
jest.mock('@/pages/sales/followup/FlowChain', () => ({ Arrow: () => null }));
jest.mock('../components', () => ({
  ...jest.requireActual('../components'),
  useOptions: () => ({ brands: [], tree: [], categories: [], tops: [] }),
  useUsers: () => [],
}));
jest.mock('../service', () => ({
  ...jest.requireActual('../service'),
  supplyApi: { matrix: jest.fn() },
}));

const api = supplyApi as unknown as Record<string, jest.Mock>;

const matrix = (mode: string, brand: string) => ({
  mode,
  days: 30,
  brands: [{ id: 1, name: brand, total: 2 }],
  categories: [{ id: 11, name: '品类A', total: 2 }],
  cells: [
    {
      brandId: 1,
      categoryId: 11,
      total: 2,
      coverage: { 1: 2 },
      worst: 1,
      stockoutModels: 1,
      stockoutTimes: 3,
      qualified: 0,
      required: 1,
      gap: 1,
      heat: 1,
      leads: 0,
    },
  ],
});

const show = () =>
  render(
    React.createElement(App, null, React.createElement(CoverageMatrixPage)),
  );

beforeEach(() => {
  jest.clearAllMocks();
  window.history.pushState({}, '', '/supply-chain/coverage');
});

it('UI-089：按无货点格子，型号清单只看统计期内无货的型号', async () => {
  window.history.pushState({}, '', '/supply-chain/coverage?mode=stockout');
  api.matrix.mockResolvedValue(matrix('stockout', '品牌甲'));
  show();
  const cell = await screen.findByText('个型号');
  expect(cell.closest('a')?.getAttribute('href')).toBe(
    '/supply-chain/coverage/models?brandId=1&categoryId=11&stockoutDays=30',
  );
});

it('UI-090：切换模式后旧模式迟到的矩阵不覆盖新结果', async () => {
  let resolveOld!: (v: unknown) => void;
  api.matrix
    .mockImplementationOnce(
      () =>
        new Promise((res) => {
          resolveOld = res;
        }),
    )
    .mockResolvedValue(matrix('stockout', '新品牌'));
  show();
  await waitFor(() => expect(api.matrix).toHaveBeenCalledTimes(1));
  fireEvent.click(screen.getByText('按无货'));
  expect(await screen.findByText('新品牌')).toBeTruthy();
  await act(async () => {
    resolveOld(matrix('cover', '旧品牌'));
  });
  expect(screen.queryByText('旧品牌')).toBeNull();
  expect(screen.getByText('新品牌')).toBeTruthy();
});
