import { act, fireEvent, render, screen } from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import CustomerListPage from './index';
import { customerApi } from './service';

jest.mock('@umijs/max', () => ({
  history: { push: jest.fn(), listen: jest.fn() },
  request: jest.fn(() => Promise.resolve({ data: [] })),
  useAccess: () => ({}),
  useLocation: () => ({ pathname: '/customer/list', search: '' }),
  useModel: () => ({ initialState: {} }),
  Link: (p: { children: unknown }) => p.children,
}));
jest.mock('./service', () => ({
  ...jest.requireActual('./service'),
  customerApi: {
    ...jest.requireActual('./service').customerApi,
    page: jest.fn(),
    profileCounts: jest.fn(),
    assignableOwners: jest.fn(() => Promise.resolve({ scope: 'SELF' })),
  },
}));
jest.mock('./profile/service', () => ({
  ...jest.requireActual('./profile/service'),
  profileApi: {
    ...jest.requireActual('./profile/service').profileApi,
    duplicates: jest.fn(() => Promise.resolve([])),
  },
}));

/**
 * 建档状态显示数量，选了立即按建档状态筛选（feedback-1116-batch1）；
 * ui-redesign 7.1 起由表格上方的切换条改为搜索条件里的「建档状态」下拉
 */
it('建档状态下拉显示数量，选「待完善」立即筛选，清空后不限', async () => {
  (customerApi.page as jest.Mock).mockResolvedValue({ records: [], total: 0 });
  (customerApi.profileCounts as jest.Mock).mockResolvedValue({
    all: 2144,
    done: 312,
    pending: 1832,
  });
  render(React.createElement(App, null, React.createElement(CustomerListPage)));
  const select = await screen.findByRole('combobox', { name: '建档状态' });
  // 等统计取回后再展开
  await act(async () => {
    await Promise.resolve();
  });
  await act(async () => {
    fireEvent.mouseDown(select);
  });
  expect(screen.getAllByTitle('待完善 · 1832').length).toBeGreaterThan(0);
  expect(screen.getAllByTitle('已建档 · 312').length).toBeGreaterThan(0);
  await act(async () => {
    fireEvent.click(screen.getAllByTitle('待完善 · 1832')[0]);
  });
  const calls = (customerApi.page as jest.Mock).mock.calls;
  expect(calls[calls.length - 1][0]).toMatchObject({
    profileStatus: 'pending',
    page: 1,
  });
  await act(async () => {
    fireEvent.click(screen.getByRole('button', { name: /重\s*置/ }));
  });
  expect(
    (customerApi.page as jest.Mock).mock.calls.at(-1)?.[0].profileStatus,
  ).toBeUndefined();
});

/** 点下拉自带的清除图标同样立即查询、不限建档状态（ui-redesign 7.1 复审 R4） */
it('建档状态点清除图标立即查询，不限建档状态', async () => {
  (customerApi.page as jest.Mock).mockResolvedValue({ records: [], total: 0 });
  (customerApi.profileCounts as jest.Mock).mockResolvedValue({
    all: 2,
    done: 1,
    pending: 1,
  });
  const { container } = render(
    React.createElement(App, null, React.createElement(CustomerListPage)),
  );
  const select = await screen.findByRole('combobox', { name: '建档状态' });
  await act(async () => {
    await Promise.resolve();
  });
  await act(async () => {
    fireEvent.mouseDown(select);
  });
  await act(async () => {
    fireEvent.click(screen.getAllByTitle('已建档 · 1')[0]);
  });
  expect(
    (customerApi.page as jest.Mock).mock.calls.at(-1)?.[0].profileStatus,
  ).toBe('done');
  const before = (customerApi.page as jest.Mock).mock.calls.length;
  const clear = container.querySelector(
    '.ant-select-clear',
  ) as HTMLElement | null;
  expect(clear).not.toBeNull();
  await act(async () => {
    fireEvent.mouseDown(clear as HTMLElement);
    fireEvent.click(clear as HTMLElement);
  });
  expect((customerApi.page as jest.Mock).mock.calls.length).toBeGreaterThan(
    before,
  );
  expect(
    (customerApi.page as jest.Mock).mock.calls.at(-1)?.[0].profileStatus,
  ).toBeUndefined();
});
