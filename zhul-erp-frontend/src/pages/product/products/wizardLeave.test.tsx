import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import { history } from '@umijs/max';
import { App } from 'antd';
import React from 'react';
import { tabsActions } from '@/components/PageTabs/store';
import { brandApi, categoryApi, mediaApi, productApi } from '../service';
import NewProductPage from './new';

/** 新建商品向导的离开方式：关闭按钮退回上一页，浏览器后退时未保存的补充资料要先确认 */

jest.mock('@umijs/max', () => {
  const { createMemoryHistory } = jest.requireActual('history');
  // 真实的 useSearchParams 每次渲染返回同一个对象
  const search = [new URLSearchParams(), jest.fn()];
  return {
    history: createMemoryHistory({
      initialEntries: ['/product/products', '/product/products/new'],
      initialIndex: 1,
    }),
    request: jest.fn(),
    useModel: () => ({ initialState: {} }),
    useSearchParams: () => search,
  };
});

jest.mock('@/components/PageTabs/store', () => ({
  tabsActions: { backOr: jest.fn() },
}));

jest.mock('../service', () => ({
  ...jest.requireActual('../service'),
  brandApi: { options: jest.fn() },
  categoryApi: { page: jest.fn(), options: jest.fn() },
  seriesApi: { options: jest.fn(() => Promise.resolve([])) },
  mediaApi: { list: jest.fn() },
  productApi: {
    match: jest.fn(),
    search: jest.fn(),
    createQuiet: jest.fn(),
    update: jest.fn(),
    get: jest.fn(),
  },
}));

beforeAll(() => {
  window.matchMedia = jest.fn().mockImplementation(() => ({
    matches: false,
    addEventListener: jest.fn(),
    removeEventListener: jest.fn(),
  }));
});

beforeEach(() => {
  localStorage.clear();
  jest.clearAllMocks();
  history.replace('/product/products/new');
  (brandApi.options as jest.Mock).mockResolvedValue([
    { id: 1, brandName: 'Siemens' },
  ]);
  (categoryApi.page as jest.Mock).mockResolvedValue({
    total: 1,
    records: [{ id: 10, categoryName: 'PLC', productCount: 0 }],
  });
  (categoryApi.options as jest.Mock).mockResolvedValue([]);
  (productApi.match as jest.Mock).mockResolvedValue({});
  (productApi.search as jest.Mock).mockResolvedValue([]);
  (productApi.createQuiet as jest.Mock).mockResolvedValue({
    id: 99,
    brandId: 1,
    categoryId: 10,
    mpnRaw: 'X1',
    mpnDisplay: 'X1',
    lifecycleStatus: 6,
  });
  (mediaApi.list as jest.Mock).mockResolvedValue([]);
});

const renderWizard = () =>
  render(React.createElement(App, null, React.createElement(NewProductPage)));

it('直接关闭向导时退回上一页，不再压一层列表（audit UI-058）', async () => {
  renderWizard();
  fireEvent.click(
    await screen.findByRole('button', { name: '关闭并退出新建' }),
  );
  expect(tabsActions.backOr).toHaveBeenCalledWith('/product/products');
  expect(history.location.pathname).toBe('/product/products/new');
});

it('第 4 步填了资料没保存，浏览器后退先确认（audit UI-005）', async () => {
  renderWizard();
  fireEvent.click(await screen.findByRole('radio', { name: /Siemens/ }));
  fireEvent.change(screen.getByLabelText(/^型号/), { target: { value: 'X1' } });
  await screen.findByText(/可以新建/, undefined, { timeout: 3000 });
  fireEvent.click(screen.getByRole('button', { name: /下一步/ }));
  fireEvent.click(await screen.findByRole('radio', { name: /PLC/ }));
  fireEvent.click(screen.getByRole('button', { name: /创建并继续/ }));
  fireEvent.click(await screen.findByRole('button', { name: /跳过这一步/ }));
  fireEvent.change(
    await screen.findByPlaceholderText('如 SITOP Power Supply'),
    {
      target: { value: '电源模块' },
    },
  );

  // 表单监听是异步通知的，等它更新到页面
  await act(() => new Promise((r) => setTimeout(r, 50)));
  act(() => history.back());
  expect(
    (await screen.findAllByText('补充资料还没保存')).length,
  ).toBeGreaterThan(0);
  expect(history.location.pathname).toBe('/product/products/new');

  // 确认离开后才真的后退
  fireEvent.click(screen.getByRole('button', { name: '不保存，离开' }));
  await waitFor(() =>
    expect(history.location.pathname).toBe('/product/products'),
  );
}, 15000);
