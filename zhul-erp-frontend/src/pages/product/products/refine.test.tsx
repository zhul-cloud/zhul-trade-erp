import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import { productApi, refineApi } from '../service';
import ProductListPage from './index';
import { refineSummary } from './refine';

/** spec product/category-refine：租户账号在商品列表筛「品类未细分」、确认 AI 建议 */

let mockCanEdit = true;

jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  Link: () => null,
  request: jest.fn(),
  useAccess: () => ({
    productPlatform: false,
    'product:product:edit': mockCanEdit,
  }),
  useLocation: () => ({ pathname: '/product/products', search: '' }),
  useModel: () => ({ initialState: {} }),
}));

jest.mock('../service', () => ({
  ...jest.requireActual('../service'),
  productApi: { page: jest.fn(), summary: jest.fn(), update: jest.fn() },
  refineApi: {
    submit: jest.fn(),
    status: jest.fn(),
    accept: jest.fn(),
    ignore: jest.fn(),
    unignore: jest.fn(),
  },
  categoryApi: {
    page: jest.fn(() => Promise.resolve({ records: [] })),
    options: jest.fn(() => Promise.resolve([])),
  },
  brandApi: { options: jest.fn(() => Promise.resolve([])) },
  seriesApi: { options: jest.fn(() => Promise.resolve([])) },
}));

const row = (id: number, mpn: string, extra: object = {}) => ({
  id,
  mpnRaw: mpn,
  mpnDisplay: mpn,
  mpnNormalized: mpn,
  brandId: 1,
  brandName: 'Siemens',
  categoryId: 10,
  categoryCode: 'plc',
  categoryName: 'PLC',
  categoryNameZh: 'PLC 与控制器',
  productName: mpn,
  lifecycleStatus: 1,
  status: 1,
  editable: true,
  ownerTenantId: 1000,
  deleted: false,
  completeness: { done: 1, total: 10, missing: [], modules: [] },
  inquiryCount: 0,
  dealCount: 0,
  ...extra,
});

const summary = {
  total: 10,
  missingMedia: 1,
  missingLogistics: 1,
  missingCustoms: 1,
  missingPrice: 1,
  missingRefine: 6,
  refineTodo: 3,
  refinePending: 2,
  refineIgnored: 1,
};

beforeEach(() => {
  mockCanEdit = true;
  localStorage.clear();
  jest.clearAllMocks();
  (productApi.summary as jest.Mock).mockResolvedValue(summary);
  (productApi.page as jest.Mock).mockImplementation(
    (q: { refineState?: string }) =>
      Promise.resolve(
        q.refineState === 'pending'
          ? {
              total: 2,
              records: [
                row(1, '6ES7 214-1AG40-0XB0', {
                  aiCategoryState: 1,
                  aiCategoryId: 11,
                  aiCategoryName: 'PLC CPU/一体机',
                  aiCategoryCross: false,
                }),
                row(2, '6SL3210-1KE21-3UF1', {
                  aiCategoryState: 1,
                  aiCategoryId: 21,
                  aiCategoryName: '通用变频器',
                  aiCategoryCross: true,
                }),
              ],
            }
          : { total: 1, records: [row(3, 'E3Z-D61')] },
      ),
  );
});

const renderPage = () =>
  render(React.createElement(App, null, React.createElement(ProductListPage)));

it('品类未细分分三档，默认待 AI 细分', async () => {
  renderPage();
  fireEvent.click(await screen.findByRole('button', { name: /品类未细分/ }));
  await waitFor(() =>
    expect(productApi.page).toHaveBeenLastCalledWith(
      expect.objectContaining({ missing: 'refine', refineState: 'todo' }),
    ),
  );
  expect(screen.getByRole('tab', { name: /待 AI 细分\s*3/ })).toBeTruthy();
  expect(screen.getByRole('tab', { name: /待确认建议\s*2/ })).toBeTruthy();
  expect(screen.getByRole('button', { name: /AI 细分品类/ })).toBeTruthy();
});

it('批量采纳时单独提示改一级品类的数量', async () => {
  (refineApi.accept as jest.Mock).mockResolvedValue({ done: 2, skipped: [] });
  renderPage();
  fireEvent.click(await screen.findByRole('button', { name: /品类未细分/ }));
  fireEvent.click(await screen.findByRole('tab', { name: /待确认建议/ }));
  expect(await screen.findByText('改一级品类')).toBeTruthy();
  fireEvent.click(screen.getByLabelText('选择 6ES7 214-1AG40-0XB0'));
  fireEvent.click(screen.getByLabelText('选择 6SL3210-1KE21-3UF1'));
  fireEvent.click(screen.getByRole('button', { name: /采纳建议/ }));
  expect(await screen.findByText('其中 1 个会改一级品类')).toBeTruthy();
  fireEvent.click(screen.getByRole('button', { name: '采纳 2 个' }));
  await waitFor(() => expect(refineApi.accept).toHaveBeenCalledWith([1, 2]));
});

it('没有编辑权限时只能看，不显示细分与采纳操作', async () => {
  mockCanEdit = false;
  renderPage();
  fireEvent.click(await screen.findByRole('button', { name: /品类未细分/ }));
  fireEvent.click(await screen.findByRole('tab', { name: /待确认建议/ }));
  expect(await screen.findByText('改一级品类')).toBeTruthy();
  expect(screen.queryByRole('button', { name: /AI 细分品类/ })).toBeNull();
  expect(screen.queryByRole('button', { name: '采纳' })).toBeNull();
  expect(screen.queryByLabelText('选择 6ES7 214-1AG40-0XB0')).toBeNull();
});

it('跑完的结果把运行中被改的计入跳过', () => {
  expect(
    refineSummary({ suggested: 168, unknown: 27, skipped: 3, discarded: 2 }),
  ).toBe('给出建议 168 个 · 判断不出 27 个 · 跳过 5 个');
  expect(refineSummary({ suggested: 1, unknown: 0 })).toBe(
    '给出建议 1 个 · 判断不出 0 个',
  );
});
