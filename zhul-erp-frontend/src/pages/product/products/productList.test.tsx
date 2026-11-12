import { fireEvent, render, screen } from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import { productApi } from '../service';
import ProductListPage from './index';

jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  Link: () => null,
  request: jest.fn(),
  useAccess: () => ({ productPlatform: true, 'product:product:edit': true }),
  useLocation: () => ({ pathname: '/product/products', search: '' }),
  useModel: () => ({ initialState: {} }),
}));

jest.mock('../service', () => ({
  ...jest.requireActual('../service'),
  productApi: { page: jest.fn(), summary: jest.fn(), setStatus: jest.fn() },
  categoryApi: { page: jest.fn(() => Promise.resolve({ records: [] })) },
  brandApi: { options: jest.fn(() => Promise.resolve([])) },
  seriesApi: { options: jest.fn(() => Promise.resolve([])) },
}));

const row = (id: number, mpn: string) => ({
  id,
  mpnRaw: mpn,
  mpnDisplay: mpn,
  mpnNormalized: mpn,
  brandId: 1,
  brandName: 'Omron',
  categoryId: 1,
  categoryName: '传感器',
  productName: mpn,
  lifecycleStatus: 1,
  status: 1,
  editable: true,
  deleted: false,
  completeness: { done: 1, total: 10, missing: [], modules: [] },
  inquiryCount: 0,
  dealCount: 0,
});

/** UI-029：勾选后改关键词，结果里看不见的旧商品不能还留在「已选」里被批量操作 */
it('关键词换了查询结果就清掉勾选', async () => {
  (productApi.summary as jest.Mock).mockResolvedValue(undefined);
  (productApi.page as jest.Mock).mockImplementation(
    (q: { keyword?: string }) =>
      Promise.resolve(
        q.keyword === 'MODEL-B'
          ? { total: 1, records: [row(2, 'MODEL-B')] }
          : { total: 1, records: [row(1, 'MODEL-A')] },
      ),
  );
  render(React.createElement(App, null, React.createElement(ProductListPage)));
  fireEvent.click(await screen.findByLabelText('选择 MODEL-A'));
  expect(await screen.findByText(/已选/)).toBeTruthy();
  fireEvent.change(screen.getByLabelText('搜索商品'), {
    target: { value: 'MODEL-B' },
  });
  expect(await screen.findByLabelText('选择 MODEL-B', {}, { timeout: 3000 })).toBeTruthy();
  expect(screen.queryByText(/已选/)).toBeNull();
});
