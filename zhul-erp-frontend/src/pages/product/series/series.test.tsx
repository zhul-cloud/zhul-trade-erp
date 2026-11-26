import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import { seriesApi } from '../service';
import SeriesPage from './index';

/** 系列管理：ProTable 自带的搜索表单换成统一搜索条件区（openspec ui-redesign 任务 7.5） */
let mockAccess: Record<string, boolean> = {};
jest.mock('@umijs/max', () => ({
  Link: () => null,
  request: jest.fn(),
  useAccess: () => mockAccess,
  useLocation: () => ({ pathname: '/product/series', search: '' }),
  useModel: () => ({ initialState: {} }),
}));
jest.mock('../service', () => ({
  ...jest.requireActual('../service'),
  brandApi: {
    options: jest.fn().mockResolvedValue([{ id: 7, brandName: 'Siemens' }]),
  },
  seriesApi: { page: jest.fn() },
}));

const lastQuery = () => {
  const calls = (seriesApi.page as jest.Mock).mock.calls;
  return calls[calls.length - 1][0];
};

beforeEach(() => {
  jest.clearAllMocks();
  mockAccess = {};
  (seriesApi.page as jest.Mock).mockResolvedValue({ total: 0, records: [] });
});

it('状态切换后立即查询；系列名称、品牌点「查询」才生效；重置清空', async () => {
  render(React.createElement(App, null, React.createElement(SeriesPage)));
  await waitFor(() => expect(seriesApi.page).toHaveBeenCalled());

  await act(async () => {
    fireEvent.mouseDown(screen.getByRole('combobox', { name: '状态' }));
  });
  await act(async () => {
    fireEvent.click(await screen.findByTitle('启用'));
  });
  await waitFor(() => expect(lastQuery()).toMatchObject({ status: 1 }));

  fireEvent.change(screen.getByLabelText('系列名称'), {
    target: { value: 'S7-1200' },
  });
  await act(async () => {
    fireEvent.mouseDown(screen.getByRole('combobox', { name: '品牌' }));
  });
  await act(async () => {
    fireEvent.click(await screen.findByTitle('Siemens'));
  });
  // 还没点查询
  expect(lastQuery().keyword).toBeUndefined();
  expect(lastQuery().brandId).toBeUndefined();
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: /查\s*询/ }));
  });
  await waitFor(() =>
    expect(lastQuery()).toMatchObject({
      keyword: 'S7-1200',
      brandId: 7,
      status: 1,
    }),
  );

  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: /重\s*置/ }));
  });
  await waitFor(() =>
    expect(lastQuery()).toMatchObject({
      keyword: undefined,
      brandId: undefined,
      status: undefined,
    }),
  );
});

it('停用用危险确认（错误色按钮、焦点在取消），启用用普通确认（复审 C6）', async () => {
  mockAccess = { 'product:series:edit': true };
  const row = {
    brandId: 7,
    brandName: 'Siemens',
    description: '',
    productCount: 0,
    createBy: 'sys',
    createTime: '2026-11-01 10:00:00',
    updateBy: 'sys',
    updateTime: '2026-11-01 10:00:00',
  };
  (seriesApi.page as jest.Mock).mockResolvedValue({
    total: 2,
    records: [
      { ...row, id: 1, seriesName: 'S7-1200', status: 1 },
      { ...row, id: 2, seriesName: 'ET200', status: 0 },
    ],
  });
  render(React.createElement(App, null, React.createElement(SeriesPage)));
  const more = await screen.findAllByRole('button', { name: '更多' });

  await act(async () => {
    fireEvent.click(more[0]);
  });
  await act(async () => {
    fireEvent.click(await screen.findByRole('menuitem', { name: /停用/ }));
  });
  const stop = await screen.findByRole('button', { name: /停\s*用/ });
  expect(stop.className).toContain('ant-btn-dangerous');
  expect(screen.getAllByText('停用「S7-1200」？').length).toBeGreaterThan(0);
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: /取\s*消/ }));
  });
  await waitFor(() =>
    expect(screen.queryAllByText('停用「S7-1200」？')).toHaveLength(0),
  );

  await act(async () => {
    fireEvent.click(more[1]);
  });
  await act(async () => {
    fireEvent.click(await screen.findByRole('menuitem', { name: /启用/ }));
  });
  const start = await screen.findByRole('button', { name: /启\s*用/ });
  expect(start.className).not.toContain('ant-btn-dangerous');
});
