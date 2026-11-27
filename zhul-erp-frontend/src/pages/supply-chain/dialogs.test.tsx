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
import { ExpansionModal } from './devDialogs';
import { CoverageModal, ProjectItemsModal, ProjectModal } from './dialogs';
import { supplyApi } from './service';

jest.mock('@umijs/max', () => ({
  history: { push: jest.fn() },
  request: jest.fn(),
  useAccess: () => ({}),
  useModel: () => ({ initialState: {} }),
}));
jest.mock('./components', () => ({
  ...jest.requireActual('./components'),
  useOptions: () => ({
    brands: [{ value: 1, label: '品牌甲' }],
    tree: [],
    categories: [
      {
        label: '一级甲',
        options: [
          { value: 11, label: '品类A' },
          { value: 12, label: '品类B' },
        ],
      },
    ],
    tops: [],
  }),
  useUsers: () => [],
}));
jest.mock('./service', () => ({
  ...jest.requireActual('./service'),
  supplyApi: {
    models: jest.fn(),
    candidates: jest.fn(),
    devScope: jest.fn(),
    devGroups: jest.fn(),
    addExpansion: jest.fn(),
    updateExpansion: jest.fn(),
  },
}));

const api = supplyApi as unknown as Record<string, jest.Mock>;

/** 手动控制返回时机的请求 */
const deferred = <T,>() => {
  let resolve!: (v: T) => void;
  let reject!: (e: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
};

const wrap = (node: any) => React.createElement(App, null, node);
const h = React.createElement as (type: any, props?: any) => any;

const scopeOf = (over: Record<string, unknown> = {}) => ({
  brandId: 1,
  categoryId: 11,
  heat: 1,
  required: 1,
  qualified: 0,
  gap: 1,
  defaultName: '开发',
  ...over,
});

beforeEach(() => {
  jest.clearAllMocks();
  api.models.mockResolvedValue({ total: 0, records: [] });
  api.devScope.mockResolvedValue(scopeOf());
  api.devGroups.mockResolvedValue({ total: 0, groups: [] });
  api.candidates.mockResolvedValue({
    items: [],
    defaultName: '品牌甲',
    activeProjectId: 0,
  });
});

it('BUS-051：全部品类专项增减型号按整个品牌取型号，不把品类 0 传给后端', async () => {
  render(
    wrap(
      h(ProjectItemsModal, {
        project: { id: 1, brandId: 1, categoryId: 0, items: [] },
        onClose: jest.fn(),
        onDone: jest.fn(),
      }),
    ),
  );
  await waitFor(() => expect(api.models).toHaveBeenCalled());
  expect(api.models.mock.calls[0][0]).toEqual(
    expect.objectContaining({ brandId: 1, categoryId: undefined }),
  );
});

it('UI-088：「去询价」在站内打开客户询价并带型号关键词，不整页跳转', () => {
  const onClose = jest.fn();
  render(
    wrap(
      h(CoverageModal, {
        demand: {
          id: 1,
          model: 'ABC-1',
          coverageStatus: 1,
          suggestedCoverage: 1,
        } as never,
        onClose: onClose,
        onDone: jest.fn(),
      }),
    ),
  );
  const btn = screen.getByText('去询价').closest('a, button') as HTMLElement;
  expect(btn.getAttribute('href')).toBeNull();
  fireEvent.click(btn);
  expect(history.push).toHaveBeenCalledWith(
    '/inquiry/customer-inquiries?keyword=ABC-1',
  );
  expect(onClose).toHaveBeenCalled();
});

it('UI-088：建专项提示「去查看」在站内打开专项', async () => {
  api.candidates.mockResolvedValue({
    items: [],
    defaultName: '品牌甲',
    activeProjectId: 7,
  });
  const onClose = jest.fn();
  render(
    wrap(
      h(ProjectModal, {
        scope: { brandId: 1, categoryId: 11 },
        onClose: onClose,
        onDone: jest.fn(),
      }),
    ),
  );
  const view = await screen.findByText('去查看');
  expect(view.closest('a')?.getAttribute('href') ?? null).toBeNull();
  fireEvent.click(view);
  expect(history.push).toHaveBeenCalledWith('/supply-chain/projects/7');
  expect(onClose).toHaveBeenCalled();
});

it('UI-056：建专项重新打开或换范围后，不残留上一个范围的进行中专项与勾选', async () => {
  api.candidates.mockResolvedValueOnce({
    items: [{ id: 501, model: 'OLD-1', coverageStatus: 1 }],
    defaultName: '品牌甲',
    activeProjectId: 7,
  });
  const onDone = jest.fn();
  const { rerender } = render(
    wrap(
      h(ProjectModal, {
        scope: { brandId: 1, categoryId: 11 },
        onClose: jest.fn(),
        onDone: onDone,
      }),
    ),
  );
  expect(await screen.findByText('去查看')).toBeTruthy();
  rerender(
    wrap(
      h(ProjectModal, { scope: undefined, onClose: jest.fn(), onDone: onDone }),
    ),
  );
  // 新范围取候选失败：旧的进行中专项、型号都不能留下
  api.candidates.mockRejectedValue(new Error('500'));
  rerender(
    wrap(
      h(ProjectModal, {
        scope: { brandId: 1, categoryId: 12 },
        onClose: jest.fn(),
        onDone: onDone,
      }),
    ),
  );
  await waitFor(() => expect(api.candidates).toHaveBeenLastCalledWith(1, 12));
  await act(async () => {});
  expect(screen.queryByText('去查看')).toBeNull();
  expect(screen.queryByText('OLD-1')).toBeNull();
});

it('UI-056：建专项丢弃上一个范围迟到的候选型号', async () => {
  const slow = deferred<unknown>();
  api.candidates.mockReturnValueOnce(slow.promise).mockResolvedValueOnce({
    items: [],
    defaultName: '品牌甲',
    activeProjectId: 0,
  });
  const { rerender } = render(
    wrap(
      h(ProjectModal, {
        scope: { brandId: 1, categoryId: 11 },
        onClose: jest.fn(),
        onDone: jest.fn(),
      }),
    ),
  );
  rerender(
    wrap(
      h(ProjectModal, {
        scope: { brandId: 1, categoryId: 12 },
        onClose: jest.fn(),
        onDone: jest.fn(),
      }),
    ),
  );
  await waitFor(() => expect(api.candidates).toHaveBeenLastCalledWith(1, 12));
  await act(async () => {
    slow.resolve({
      items: [{ id: 501, model: 'OLD-1', coverageStatus: 1 }],
      defaultName: '品牌甲',
      activeProjectId: 7,
    });
  });
  expect(screen.queryByText('去查看')).toBeNull();
  expect(screen.queryByText('OLD-1')).toBeNull();
});

it('UI-056：主动拓展丢弃上一个品类迟到的范围', async () => {
  const slowX = deferred<unknown>();
  api.devScope
    .mockReturnValueOnce(slowX.promise)
    .mockResolvedValueOnce(scopeOf({ categoryId: 12 }));
  const { rerender } = render(
    wrap(
      h(ExpansionModal, {
        open: true,
        scope: { brandId: 1, categoryId: 11 },
        onClose: jest.fn(),
        onDone: jest.fn(),
      }),
    ),
  );
  rerender(
    wrap(
      h(ExpansionModal, {
        open: true,
        scope: { brandId: 1, categoryId: 12 },
        onClose: jest.fn(),
        onDone: jest.fn(),
      }),
    ),
  );
  await waitFor(() => expect(api.devScope).toHaveBeenCalledTimes(2));
  await act(async () => {
    slowX.resolve(scopeOf({ expansionId: 9, expansionReason: '老理由' }));
  });
  expect(screen.queryByText('修改主动拓展')).toBeNull();
  expect(screen.getByText('加入主动拓展')).toBeTruthy();
});

it('UI-056：主动拓展换品类后不再按上一个品类的主动拓展保存', async () => {
  const slowY = deferred<unknown>();
  api.devScope
    .mockResolvedValueOnce(
      scopeOf({ expansionId: 9, expansionReason: '老理由' }),
    )
    .mockReturnValueOnce(slowY.promise);
  const { rerender } = render(
    wrap(
      h(ExpansionModal, {
        open: true,
        scope: { brandId: 1, categoryId: 11 },
        onClose: jest.fn(),
        onDone: jest.fn(),
      }),
    ),
  );
  expect(await screen.findByText('修改主动拓展')).toBeTruthy();
  rerender(
    wrap(
      h(ExpansionModal, {
        open: true,
        scope: { brandId: 1, categoryId: 12 },
        onClose: jest.fn(),
        onDone: jest.fn(),
      }),
    ),
  );
  // 新品类的范围还没返回，不能按旧范围显示为「修改」
  await waitFor(() => expect(api.devScope).toHaveBeenCalledTimes(2));
  expect(screen.queryByText('修改主动拓展')).toBeNull();
  await act(async () => {
    slowY.resolve(scopeOf({ categoryId: 12 }));
  });
  expect(screen.getByText('加入主动拓展')).toBeTruthy();
  fireEvent.change(screen.getByPlaceholderText(/重点拓展/), {
    target: { value: '新理由' },
  });
  fireEvent.click(screen.getByRole('button', { name: /加\s*入/ }));
  await waitFor(() => expect(api.addExpansion).toHaveBeenCalled());
  expect(api.updateExpansion).not.toHaveBeenCalled();
  expect(api.addExpansion.mock.calls[0][0]).toEqual(
    expect.objectContaining({ categoryId: 12 }),
  );
});
