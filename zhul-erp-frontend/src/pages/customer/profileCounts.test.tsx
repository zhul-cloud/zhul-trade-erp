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

/** 表格上方「全部 / 已建档 / 待完善」显示数量，点击按建档状态筛选（feedback-1116-batch1） */
it('建档统计显示数量，点「待完善」按建档状态筛选', async () => {
  (customerApi.page as jest.Mock).mockResolvedValue({ records: [], total: 0 });
  (customerApi.profileCounts as jest.Mock).mockResolvedValue({
    all: 2144,
    done: 312,
    pending: 1832,
  });
  render(React.createElement(App, null, React.createElement(CustomerListPage)));
  expect(await screen.findByText('待完善 1832')).toBeTruthy();
  expect(screen.getByText('已建档 312')).toBeTruthy();
  await act(async () => {
    fireEvent.click(screen.getByText('待完善 1832'));
  });
  const calls = (customerApi.page as jest.Mock).mock.calls;
  expect(calls[calls.length - 1][0]).toMatchObject({
    profileStatus: 'pending',
    page: 1,
  });
  await act(async () => {
    fireEvent.click(screen.getByText('全部 2144'));
  });
  expect(
    (customerApi.page as jest.Mock).mock.calls.at(-1)?.[0].profileStatus,
  ).toBeUndefined();
});
