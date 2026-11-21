import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { App } from 'antd';
import React from 'react';
import { PaneContext } from '@/components/PageTabs/hooks';
import Candidates from './index';
import { candidateApi } from './service';

/** 商品候选批量 AI 解析的进度跟踪：跑完只刷新列表和计数，按当前条件刷新，暂时性错误继续查 */

jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  Link: () => null,
  request: jest.fn(),
  useAccess: () => ({ 'product:candidate:review': true }),
  useLocation: () => ({ pathname: '/product/candidates', search: '' }),
  useModel: () => ({ initialState: {} }),
  useSearchParams: () => [new URLSearchParams()],
}));

jest.mock('../service', () => ({
  ...jest.requireActual('../service'),
  brandApi: { options: jest.fn(() => Promise.resolve([])) },
  categoryApi: { options: jest.fn(() => Promise.resolve([])) },
  seriesApi: { options: jest.fn(() => Promise.resolve([])) },
  productApi: { search: jest.fn(() => Promise.resolve([])) },
}));

jest.mock('./service', () => ({
  ...jest.requireActual('./service'),
  candidateApi: {
    page: jest.fn(),
    counts: jest.fn(),
    unlinked: jest.fn(() => Promise.resolve({ records: [], total: 0 })),
    detail: jest.fn(),
    aiFillStatus: jest.fn(),
  },
}));

const AI_FILL_KEY = 'zhul:candidate:ai-fill';

const candidate = {
  id: 5,
  brandId: 1,
  brandName: 'Siemens',
  brandText: 'Siemens',
  mpnRaw: '6ES7214-1AG40',
  status: 1,
  statusName: '待审核',
  level: 1,
  levelName: '询盘出现',
  inquiryCount: 1,
  dealCount: 0,
  sources: [],
};

const lastPageQuery = () => {
  const calls = (candidateApi.page as jest.Mock).mock.calls;
  return calls[calls.length - 1][0];
};

const startJob = () =>
  localStorage.setItem(
    AI_FILL_KEY,
    JSON.stringify({ taskId: 7, mode: 'category', total: 1, skipped: 0 }),
  );

const done = { taskId: 7, status: 3, mode: 'category', filled: 1 };

beforeEach(() => {
  localStorage.clear();
  jest.clearAllMocks();
  (candidateApi.page as jest.Mock).mockResolvedValue({
    total: 1,
    records: [candidate],
  });
  (candidateApi.counts as jest.Mock).mockResolvedValue({
    pending: 1,
    archived: 0,
    merged: 0,
    rejected: 0,
  });
  (candidateApi.detail as jest.Mock).mockResolvedValue(candidate);
});

const page = (active = true) =>
  React.createElement(
    PaneContext.Provider,
    { value: { active } },
    React.createElement(App, null, React.createElement(Candidates)),
  );

it('AI 解析跑完不关掉正在审核的抽屉（audit UI-062）', async () => {
  startJob();
  (candidateApi.aiFillStatus as jest.Mock).mockResolvedValue(done);
  render(page());
  fireEvent.click(await screen.findByRole('button', { name: '审核' }));
  expect(await screen.findByText(/审核候选/)).toBeTruthy();
  await waitFor(() => expect(candidateApi.counts).toHaveBeenCalledTimes(2), {
    timeout: 5000,
  });
  expect(screen.getByText(/审核候选/)).toBeTruthy();
  expect(localStorage.getItem(AI_FILL_KEY)).toBeNull();
}, 10000);

it('AI 解析跑完按当前页签刷新，不按发起时的旧页签（audit UI-083）', async () => {
  startJob();
  (candidateApi.aiFillStatus as jest.Mock).mockResolvedValue(done);
  render(page());
  fireEvent.click(await screen.findByText(/^已建档/));
  await waitFor(() => expect(lastPageQuery()).toMatchObject({ status: 2 }));
  await waitFor(() => expect(candidateApi.counts).toHaveBeenCalledTimes(2), {
    timeout: 5000,
  });
  expect(lastPageQuery()).toMatchObject({ status: 2 });
}, 10000);

it('查询进度遇到网络错误继续跟踪，跑完照常刷新（audit UI-084）', async () => {
  startJob();
  (candidateApi.aiFillStatus as jest.Mock)
    .mockRejectedValueOnce(new Error('Network Error'))
    .mockResolvedValue(done);
  render(page());
  await waitFor(() => expect(candidateApi.counts).toHaveBeenCalledTimes(2), {
    timeout: 8000,
  });
  expect(candidateApi.aiFillStatus).toHaveBeenCalledTimes(2);
}, 12000);

it('从其他页面返回时页签计数一起刷新（audit UI-055）', async () => {
  const { rerender } = render(page(true));
  await screen.findByRole('button', { name: '审核' });
  await waitFor(() => expect(candidateApi.counts).toHaveBeenCalledTimes(1));
  rerender(page(false));
  rerender(page(true));
  await waitFor(() => expect(candidateApi.counts).toHaveBeenCalledTimes(2));
});
